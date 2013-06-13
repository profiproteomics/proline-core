package fr.proline.core.om.provider.msi.impl

import scala.collection.JavaConversions.{ asScalaBuffer, asScalaSet }
import scala.collection.mutable

import com.codahale.jerkson.Json
import com.weiglewilczek.slf4s.Logging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.{ Enzyme, InstrumentConfig, MSISearch, Ms2Query, MsQuery, MsQueryProperties, Peaklist, PeaklistSoftware, Peptide, PeptideMatch, PeptideMatchProperties, Protein, ProteinMatch, PtmDefinition, PtmEvidence, PtmNames, ResultSet, ResultSetProperties, SearchSettings, SearchSettingsProperties, SeqDatabase, SequenceMatch }
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.orm.msi.MsiSearch
import fr.proline.core.orm.msi.ResultSet.Type
import fr.proline.core.orm.msi.repository.{ MsiSeqDatabaseRepository => seqDatabaseRepo, PeptideMatchRepository => peptideMatchRepo, ProteinMatchRepository => proteinMatchRepo, ScoringRepository => scoringRepo, SequenceMatchRepository => sequenceMatchRepo }
import fr.proline.repository.util.JPAUtils
import fr.proline.util.StringUtils
import javax.persistence.EntityManager

class ORMResultSetProvider(val msiDbCtx: DatabaseConnectionContext,
                           val psDbCtx: DatabaseConnectionContext,
                           val pdiDbCtx: DatabaseConnectionContext) extends IResultSetProvider with Logging {

  require((msiDbCtx != null) && msiDbCtx.isJPA, "Invalid MSI Db Context")
  require((psDbCtx != null) && psDbCtx.isJPA, "Invalid PS Db Context")
  require((pdiDbCtx != null) && pdiDbCtx.isJPA, "Invalid PDI Db Context")

  type MsiResultSet = fr.proline.core.orm.msi.ResultSet
  type MsiPeptideMatch = fr.proline.core.orm.msi.PeptideMatch
  type MsiPeptide = fr.proline.core.orm.msi.Peptide
  type MsiMsQuery = fr.proline.core.orm.msi.MsQuery
  type MsiProteinMatch = fr.proline.core.orm.msi.ProteinMatch
  type MsiSearchSetting = fr.proline.core.orm.msi.SearchSetting
  type MsiInstrumentConfig = fr.proline.core.orm.msi.InstrumentConfig
  type MsiUsedPtm = fr.proline.core.orm.msi.UsedPtm
  type MsiSeqDatabase = fr.proline.core.orm.msi.SeqDatabase
  type MsiSequenceMatch = fr.proline.core.orm.msi.SequenceMatch
  type MsiPeaklist = fr.proline.core.orm.msi.Peaklist
  type MsiPeaklistSoftware = fr.proline.core.orm.msi.PeaklistSoftware

  val peptideProvider = new ORMPeptideProvider(psDbCtx)

  val proteinProvider = new ORMProteinProvider(pdiDbCtx)

  /* OM Entity caches */
  private val entityCaches = mutable.Map.empty[Class[_], mutable.Map[Long, _]]

  def getResultSetsAsOptions(resultSetIds: Seq[Long]): Array[Option[ResultSet]] = {
    val resultSets = Array.newBuilder[Option[ResultSet]]

    for (resultSetId <- resultSetIds) {
      resultSets += getResultSet(resultSetId)
    }

    resultSets.result
  }

  def getResultSets(resultSetIds: Seq[Long]): Array[ResultSet] = {
    val resultSets = Array.newBuilder[ResultSet]

    for (resultSetId <- resultSetIds) {
      val resultSet = getResultSet(resultSetId)

      if (resultSet.isDefined) {
        resultSets += resultSet.get
      }

    }

    resultSets.result
  }

  override def getResultSet(resultSetId: Long): Option[ResultSet] = {
    val msiEM = msiDbCtx.getEntityManager

    JPAUtils.checkEntityManager(msiEM)

    val msiResultSet = msiEM.find(classOf[MsiResultSet], resultSetId)

    if (msiResultSet == null) {
      None
    } else {
      Some(buildResultSet(msiResultSet))
    }

  }

  /* Private methods */
  private def getEntityCache[T](classifier: Class[T]): mutable.Map[Long, T] = {
    assert(classifier != null, "getEntityCache() classifier is null")

    val knownCache = entityCaches.get(classifier)

    if (knownCache.isDefined) {
      knownCache.get.asInstanceOf[mutable.Map[Long, T]]
    } else {
      logger.debug("Creating OM entity cache for " + classifier)

      val newCache = mutable.Map.empty[Long, T]

      entityCaches += classifier -> newCache

      newCache
    }

  }

  private def buildResultSet(msiResultSet: MsiResultSet): ResultSet = {
    assert(msiResultSet != null, "buildResultSet() msiResultSet is null")

    val msiResultSetId = msiResultSet.getId

    val knownResultSets = getEntityCache(classOf[ResultSet])

    val knownResultSet = knownResultSets.get(msiResultSetId)

    if (knownResultSet.isDefined) {
      knownResultSet.get
    } else {
      val msiEM = msiDbCtx.getEntityManager

      /* Peptides & PeptideMatches */
      val msiPeptideMatches = peptideMatchRepo.findPeptideMatchByResultSet(msiEM, msiResultSetId)

      val omPeptideMatches =
        for (msiPeptideMatch <- msiPeptideMatches) yield {
          buildPeptideMatch(msiPeptideMatch, msiResultSetId, msiEM)
        }

      val peptides = getEntityCache(classOf[Peptide]).values.toArray[Peptide]
      logger.trace("Loaded Peptides: " + peptides.size)

      val peptideMatches = omPeptideMatches.toArray[PeptideMatch]
      logger.trace("Loaded PeptideMatches: " + peptideMatches.size)

      /* ProteinMaches */
      val msiProteinMatches = proteinMatchRepo.findProteinMatchesForResultSet(msiEM, msiResultSet)

      val omProteinMatches =
        for (msiProteinMatch <- msiProteinMatches) yield {
          buildProteinMatch(msiProteinMatch, msiResultSetId, msiEM)
        }

      val proteinMatches = omProteinMatches.toArray[ProteinMatch]
      logger.trace("Loaded ProteinMatches: " + proteinMatches.size)

      val rsType = msiResultSet.getType

      // TODO Decoy, Native and Quantified bools must be coherant with JPARsStorer parseType()
      val isDecoy = if ((rsType == Type.DECOY_SEARCH) || (rsType == Type.DECOY_USER)) {
        true
      } else {
        false
      }

      val isNative = if ((rsType == Type.SEARCH) || (rsType == Type.DECOY_SEARCH)) {
        true
      } else {
        false
      }

      val isQuantified = (rsType == Type.QUANTITATION)

      /* MsiSearch */
      var msiSearchId: Long = 0L
      var optionalMsiSearch: Option[MSISearch] = None

      val msiMsiSearch = msiResultSet.getMsiSearch
      if (msiMsiSearch != null) {
        val omMsiSearch = buildMsiSearch(msiMsiSearch)

        msiSearchId = omMsiSearch.id
        optionalMsiSearch = Some(omMsiSearch)
      }

      /* Decoy RS */
      var decoyRSId: Long = 0L
      var optionalDecoyRS: Option[ResultSet] = None

      val msiDecoyRs = msiResultSet.getDecoyResultSet
      if (msiDecoyRs != null) {
        val omDecoyRs = buildResultSet(msiDecoyRs)

        decoyRSId = omDecoyRs.id
        optionalDecoyRS = Some(omDecoyRs)
      }

      val serializedProperties = msiResultSet.getSerializedProperties

      val resultSetProperties: Option[ResultSetProperties] = if (StringUtils.isEmpty(serializedProperties)) {
        None
      } else {
        Some(Json.parse[ResultSetProperties](serializedProperties))
      }

      val resultSet = new ResultSet(peptides,
        peptideMatches,
        proteinMatches,
        isDecoy,
        isNative,
        msiResultSetId,
        msiResultSet.getName,
        msiResultSet.getDescription,
        isQuantified,
        msiSearchId,
        optionalMsiSearch,
        decoyRSId,
        optionalDecoyRS,
        resultSetProperties
      )

      knownResultSets += msiResultSetId -> resultSet

      logger.info("ResultSet #" + msiResultSetId + " loaded")

      resultSet
    }

  }

  private def buildPeptideMatch(msiPeptideMatch: MsiPeptideMatch, resultSetId: Long, msiEM: EntityManager): PeptideMatch = {
    assert(msiPeptideMatch != null, "buildPeptideMatch() msiPeptideMatch is null")

    val msiPeptideMatchId = msiPeptideMatch.getId

    val knownPeptideMatches = getEntityCache(classOf[PeptideMatch])

    val knownPeptideMatch = knownPeptideMatches.get(msiPeptideMatchId)

    if (knownPeptideMatch.isDefined) {
      knownPeptideMatch.get
    } else {
      /* Handle best child */
      var bestChildId: Long = 0L
      var optionalBestChild: Option[PeptideMatch] = None

      val msiBestChild = msiPeptideMatch.getBestPeptideMatch
      if (msiBestChild != null) {
        val omBestChild = buildPeptideMatch(msiBestChild, resultSetId, msiEM)

        bestChildId = omBestChild.id
        optionalBestChild = Some(omBestChild)
      }

      val serializedProperties = msiPeptideMatch.getSerializedProperties

      val peptideMatchProperties: Option[PeptideMatchProperties] = if (StringUtils.isEmpty(serializedProperties)) {
        None
      } else {
        Some(Json.parse[PeptideMatchProperties](serializedProperties))
      }

      val peptideMatch = new PeptideMatch(msiPeptideMatchId,
        msiPeptideMatch.getRank.intValue,
        msiPeptideMatch.getScore.floatValue,
        scoringRepo.getScoreTypeForId(msiEM, msiPeptideMatch.getScoringId),
        msiPeptideMatch.getDeltaMoz.floatValue,
        msiPeptideMatch.getIsDecoy,
        retrievePeptide(msiPeptideMatch.getPeptideId),
        msiPeptideMatch.getMissedCleavage,
        msiPeptideMatch.getFragmentMatchCount.intValue,
        buildMsQuery(msiPeptideMatch.getMsQuery),
        true, // isValidated always true when loading from ORM
        resultSetId,
        null, // TODO handle children
        null, // TODO handle children
        bestChildId,
        optionalBestChild,
        peptideMatchProperties,
        None)

      knownPeptideMatches += msiPeptideMatchId -> peptideMatch

      peptideMatch
    }

  }

  def retrievePeptide(peptideId: Long): Peptide = {
    val knownPeptides = getEntityCache(classOf[Peptide])

    val knownPeptide = knownPeptides.get(peptideId)

    if (knownPeptide.isDefined) {
      knownPeptide.get
    } else {
      val optionalPeptide = peptideProvider.getPeptide(peptideId)

      if ((optionalPeptide != null) && optionalPeptide.isDefined) {
        val peptide = optionalPeptide.get

        knownPeptides += peptideId -> peptide

        peptide
      } else {
        throw new IllegalArgumentException("Peptide #" + peptideId + " NOT found in PS Db")
      }

    }

  }

  private def buildMsQuery(msiMsQuery: MsiMsQuery): MsQuery = {

    if (msiMsQuery == null) {
      null
    } else {
      val msiMsQueryId = msiMsQuery.getId

      val knownMsQueries = getEntityCache(classOf[MsQuery])

      val knownMsQuery = knownMsQueries.get(msiMsQueryId)

      if (knownMsQuery.isDefined) {
        knownMsQuery.get
      } else {
        /* Always a Ms2Querry, handle Spectrum */
        var spectrumId: Long = 0L
        var spectrumTitle: String = "Unknown"

        val msiSpectrum = msiMsQuery.getSpectrum
        if (msiSpectrum != null) {
          spectrumId = msiSpectrum.getId
          spectrumTitle = msiSpectrum.getTitle
        }

        val serializedProperties = msiMsQuery.getSerializedProperties

        val msQueryProperties: Option[MsQueryProperties] = if (StringUtils.isEmpty(serializedProperties)) {
          None
        } else {
          Some(Json.parse[MsQueryProperties](serializedProperties))
        }

        val msQuery = new Ms2Query(msiMsQueryId,
          msiMsQuery.getInitialId,
          msiMsQuery.getMoz,
          msiMsQuery.getCharge,
          spectrumTitle,
          spectrumId,
          msQueryProperties)

        knownMsQueries += msiMsQueryId -> msQuery

        msQuery
      }

    }

  }

  private def buildProteinMatch(msiProteinMatch: MsiProteinMatch, resultSetId: Long, msiEM: EntityManager): ProteinMatch = {
    assert(msiProteinMatch != null, "buildProteinMatch() msiProteinMatch is null")

    def retrieveProtein(proteinId: Long): Protein = {
      val knownProteins = getEntityCache(classOf[Protein])

      val knownProtein = knownProteins.get(proteinId)

      if (knownProtein.isDefined) {
        knownProtein.get
      } else {
        val optionalProtein = proteinProvider.getProtein(proteinId)

        if ((optionalProtein != null) && optionalProtein.isDefined) {
          val protein = optionalProtein.get

          knownProteins += proteinId -> protein

          protein
        } else {
          throw new IllegalArgumentException("Protein #" + proteinId + " NOT found in PDI Db")
        }

      }

    }

    val msiProteinMatchId = msiProteinMatch.getId

    /* TaxonId java Long to Scala Long... */
    var taxonId: Long = 0L

    val msiTaxonId = msiProteinMatch.getTaxonId
    if (msiTaxonId != null) {
      taxonId = msiTaxonId.longValue
    }

    /* Optional BioSequence (Protein) */
    var proteinId: Long = 0L
    var optionalProtein: Option[Protein] = None

    val msiProteinId = msiProteinMatch.getBioSequenceId
    if (msiProteinId != null) {

      val numericProteinId = msiProteinId.longValue
      if (numericProteinId > 0L) {
        val omProtein = retrieveProtein(numericProteinId)

        proteinId = omProtein.id
        optionalProtein = Some(omProtein)
      }

    }

    /* SeqDatabase Ids */
    val seqDatabasesIds = Array.newBuilder[Long]

    val msiSeqDatabaseIds = seqDatabaseRepo.findSeqDatabaseIdsForProteinMatch(msiEM, msiProteinMatchId)
    if ((msiSeqDatabaseIds != null) && !msiSeqDatabaseIds.isEmpty) {

      for (msiSeqDatabaseId <- msiSeqDatabaseIds) {
        seqDatabasesIds += msiSeqDatabaseId.longValue
      }

    }

    /* SequenceMatches */
    val sequenceMatches = Array.newBuilder[SequenceMatch]

    val msiSequenceMatches = sequenceMatchRepo.findSequenceMatchForProteinMatch(msiEM, msiProteinMatchId)

    if ((msiSequenceMatches != null) && !msiSequenceMatches.isEmpty) {

      for (msiSequenceMatch <- msiSequenceMatches) {
        sequenceMatches += buildSequenceMatch(msiSequenceMatch, resultSetId, msiEM)
      }

    }

    new ProteinMatch(msiProteinMatch.getAccession,
      msiProteinMatch.getDescription,
      msiProteinMatch.getIsDecoy,
      msiProteinMatch.getIsLastBioSequence,
      msiProteinMatchId,
      taxonId,
      resultSetId,
      proteinId,
      optionalProtein,
      seqDatabasesIds.result,
      msiProteinMatch.getGeneName,
      msiProteinMatch.getScore.floatValue,
      scoringRepo.getScoreTypeForId(msiEM, msiProteinMatch.getScoringId),
      msiProteinMatch.getCoverage,
      msiProteinMatch.getPeptideMatchCount.intValue,
      sequenceMatches.result,
      None // TODO handle properties
    )
  }

  private def buildMsiSearch(msiSearch: MsiSearch): MSISearch = {
    assert(msiSearch != null, "buildMsiSearch() msiSearch is null")

    new MSISearch(msiSearch.getId,
      msiSearch.getResultFileName,
      msiSearch.getSubmittedQueriesCount,
      buildSearchSettings(msiSearch.getSearchSetting),
      buildPeaklist(msiSearch.getPeaklist),
      msiSearch.getDate,
      msiSearch.getTitle,
      msiSearch.getResultFileDirectory,
      msiSearch.getJobNumber.intValue,
      msiSearch.getUserName,
      msiSearch.getUserEmail,
      msiSearch.getQueriesCount.intValue,
      msiSearch.getSearchedSequencesCount.intValue)
  }

  private def buildSearchSettings(msiSearchSetting: MsiSearchSetting): SearchSettings = {
    assert(msiSearchSetting != null, "buildSearchSettings() msiSearchSetting is null")

    /* Enzymes */
    val msiEnzymes = msiSearchSetting.getEnzymes

    val enzymes = if ((msiEnzymes == null) || msiEnzymes.isEmpty) {
      new Array[Enzyme](0) // An empty array
    } else {

      (for (msiEnzyme <- msiEnzymes) yield {
        new Enzyme(msiEnzyme.getName)
      }).toArray

    }

    /* Used PTMs */
    val variablePtmDefs = Array.newBuilder[PtmDefinition]
    val fixedPtmDefs = Array.newBuilder[PtmDefinition]

    val msiUsedPtms = msiSearchSetting.getUsedPtms
    if ((msiUsedPtms != null) && !msiUsedPtms.isEmpty) {

      for (msiUsedPtm <- msiUsedPtms) {
        val ptmDef = buildPtmDefinition(msiUsedPtm)

        if (msiUsedPtm.getIsFixed) {
          fixedPtmDefs += ptmDef
        } else {
          variablePtmDefs += ptmDef
        }

      }

    }

    /* SeqDatabases */
    val seqDatabases = Array.newBuilder[SeqDatabase]

    val searchSettingsSeqDatabaseMaps = msiSearchSetting.getSearchSettingsSeqDatabaseMaps
    if ((searchSettingsSeqDatabaseMaps != null) && !searchSettingsSeqDatabaseMaps.isEmpty) {

      for (entry <- searchSettingsSeqDatabaseMaps) {
        val msiSeqDatabase = entry.getSeqDatabase

        if (msiSeqDatabase != null) {
          seqDatabases += buildSeqDatabase(msiSeqDatabase)
        }

      }

    }

    val serializedProperties = msiSearchSetting.getSerializedProperties

    val searchSettingsProperties: Option[SearchSettingsProperties] = if (StringUtils.isEmpty(serializedProperties)) {
      None
    } else {
      Some(Json.parse[SearchSettingsProperties](serializedProperties))
    }

    new SearchSettings(msiSearchSetting.getId,
      msiSearchSetting.getSoftwareName,
      msiSearchSetting.getSoftwareVersion,
      msiSearchSetting.getTaxonomy,
      msiSearchSetting.getMaxMissedCleavages.intValue,
      msiSearchSetting.getPeptideChargeStates,
      msiSearchSetting.getPeptideMassErrorTolerance.doubleValue,
      msiSearchSetting.getPeptideMassErrorToleranceUnit,
      msiSearchSetting.getIsDecoy,
      enzymes,
      variablePtmDefs.result,
      fixedPtmDefs.result,
      seqDatabases.result,
      buildInstrumentConfig(msiSearchSetting.getInstrumentConfig),
      None,
      None,
      msiSearchSetting.getQuantitation,
      searchSettingsProperties)
  }

  private def buildPtmDefinition(msiUsedPtm: MsiUsedPtm): PtmDefinition = {
    assert(msiUsedPtm != null, "buildPtmDefinition() msiUsedPtm is null")

    val msiPtmSpecificity = msiUsedPtm.getPtmSpecificity

    val names = new PtmNames(msiUsedPtm.getShortName, null) // TODO handle fullName

    new PtmDefinition(msiPtmSpecificity.getId,
      msiPtmSpecificity.getLocation,
      names,
      new Array[PtmEvidence](0), // TODO handle ptmEvidences
      StringUtils.convertStringResidueToChar(msiPtmSpecificity.getResidue),
      null, // TODO handle classification
      0 // TODO handle PTM Id (Ps PTM)
    )
  }

  private def buildSeqDatabase(msiSeqDatabase: MsiSeqDatabase): SeqDatabase = {
    assert(msiSeqDatabase != null, "buildSeqDatabase() msiSeqDatabase is null")

    val msiSeqDatabaseId = msiSeqDatabase.getId

    val knownSeqDatabases = getEntityCache(classOf[SeqDatabase])

    val knownSeqDatabase = knownSeqDatabases.get(msiSeqDatabaseId)

    if (knownSeqDatabase.isDefined) {
      knownSeqDatabase.get
    } else {
      val relDate: java.util.Date = msiSeqDatabase.getReleaseDate
      val seqDatabase = new SeqDatabase(
        msiSeqDatabaseId,
        msiSeqDatabase.getName,
        msiSeqDatabase.getFastaFilePath,
        msiSeqDatabase.getSequenceCount.intValue,
        relDate, // DateUtils.formatReleaseDate(msiSeqDatabase.getReleaseDate)
        msiSeqDatabase.getVersion)

      knownSeqDatabases += msiSeqDatabaseId -> seqDatabase

      seqDatabase
    }

  }

  private def buildSequenceMatch(msiSequenceMatch: MsiSequenceMatch, resultSetId: Long, msiEM: EntityManager): SequenceMatch = {
    assert(msiSequenceMatch != null, "buildSequenceMatch() msiSeqDatabase is null")

    val msiSequenceMatchId = msiSequenceMatch.getId

    /* Peptide */
    val peptide = retrievePeptide(msiSequenceMatchId.getPeptideId)

    /* BestPeptideMatch */
    val msiBestPeptideMatchId = msiSequenceMatch.getBestPeptideMatchId

    val knownPeptideMatches = getEntityCache(classOf[PeptideMatch])

    val knownBestPeptideMatch = knownPeptideMatches.get(msiBestPeptideMatchId)

    if (knownBestPeptideMatch.isEmpty) {
      throw new IllegalArgumentException("Unknown best PeptideMatch Id: " + msiBestPeptideMatchId)
    }

    val omBestPeptideMatch = knownBestPeptideMatch.get

    new SequenceMatch(msiSequenceMatchId.getStart,
      msiSequenceMatchId.getStop,
      StringUtils.convertStringResidueToChar(msiSequenceMatch.getResidueBefore),
      StringUtils.convertStringResidueToChar(msiSequenceMatch.getResidueAfter),
      msiSequenceMatch.getIsDecoy,
      msiSequenceMatch.getResultSetId,
      peptide.id,
      Some(peptide),
      omBestPeptideMatch.id,
      Some(omBestPeptideMatch),
      None) // TODO handle properties
  }

  private def buildInstrumentConfig(msiInstrumentConfig: MsiInstrumentConfig): InstrumentConfig = {
    assert(msiInstrumentConfig != null, "buildInstrumentConfig() msiInstrumentConfig is null")

    val msiInstrumentConfigId = msiInstrumentConfig.getId

    val knownInstrumentConfigs = getEntityCache(classOf[InstrumentConfig])

    val knownInstrumentConfig = knownInstrumentConfigs.get(msiInstrumentConfigId)

    if (knownInstrumentConfig.isDefined) {
      knownInstrumentConfig.get
    } else {

      val instrumentConfig = new InstrumentConfig(msiInstrumentConfigId,
        msiInstrumentConfig.getName,
        null, // TODO handle instrument from Uds Db
        msiInstrumentConfig.getMs1Analyzer,
        msiInstrumentConfig.getMsnAnalyzer,
        null // TODO handle activationType
      )

      knownInstrumentConfigs += msiInstrumentConfigId -> instrumentConfig

      instrumentConfig
    }

  }

  private def buildPeaklist(msiPeaklist: MsiPeaklist): Peaklist = {
    assert(msiPeaklist != null, "buildPeaklist() msiPeaklist is null")

    new Peaklist(msiPeaklist.getId,
      msiPeaklist.getType,
      msiPeaklist.getPath,
      msiPeaklist.getRawFileName,
      msiPeaklist.getMsLevel,
      msiPeaklist.getSpectrumDataCompression,
      buildPeaklistSoftware(msiPeaklist.getPeaklistSoftware))
  }

  private def buildPeaklistSoftware(msiPeaklistSoftware: MsiPeaklistSoftware): PeaklistSoftware = {
    assert(msiPeaklistSoftware != null, "buildPeaklistSoftware() msiPeaklistSoftware is null")

    val msiPeaklistSoftwareId = msiPeaklistSoftware.getId

    val knownPeaklistSoftwares = getEntityCache(classOf[PeaklistSoftware])

    val knownPeaklistSoftware = knownPeaklistSoftwares.get(msiPeaklistSoftwareId)

    if (knownPeaklistSoftware.isDefined) {
      knownPeaklistSoftware.get
    } else {

      val knownPeaklistSoftware = new PeaklistSoftware(msiPeaklistSoftwareId,
        msiPeaklistSoftware.getName,
        msiPeaklistSoftware.getVersion)

      knownPeaklistSoftwares += msiPeaklistSoftwareId -> knownPeaklistSoftware

      knownPeaklistSoftware
    }

  }

}
