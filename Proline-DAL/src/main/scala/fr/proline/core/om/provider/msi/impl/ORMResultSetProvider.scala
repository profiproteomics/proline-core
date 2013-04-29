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
  private val entityCaches = mutable.Map.empty[Class[_], mutable.Map[Int, _]]

  def getResultSetsAsOptions(resultSetIds: Seq[Int]): Array[Option[ResultSet]] = {
    val resultSets = Array.newBuilder[Option[ResultSet]]

    for (resultSetId <- resultSetIds) {
      resultSets += getResultSet(resultSetId)
    }

    resultSets.result
  }

  def getResultSets(resultSetIds: Seq[Int]): Array[ResultSet] = {
    val resultSets = Array.newBuilder[ResultSet]

    for (resultSetId <- resultSetIds) {
      val resultSet = getResultSet(resultSetId)

      if (resultSet.isDefined) {
        resultSets += resultSet.get
      }

    }

    resultSets.result
  }

  override def getResultSet(resultSetId: Int): Option[ResultSet] = {

    val msiEm = msiDbCtx.getEntityManager

    JPAUtils.checkEntityManager(msiEm)

    val msiResultSet = msiEm.find(classOf[MsiResultSet], resultSetId)

    if (msiResultSet == null) {
      None
    } else {
      Some(buildResultSet(msiResultSet))
    }

  }

  /* Private methods */
  private def getEntityCache[T](classifier: Class[T]): mutable.Map[Int, T] = {
    assert(classifier != null, "getEntityCache() classifier is null")

    val knownCache = entityCaches.get(classifier)

    if (knownCache.isDefined) {
      knownCache.get.asInstanceOf[mutable.Map[Int, T]]
    } else {
      logger.debug("Creating OM entity cache for " + classifier)

      val newCache = mutable.Map.empty[Int, T]

      entityCaches += classifier -> newCache

      newCache
    }

  }

  private def buildResultSet(msiResultSet: MsiResultSet): ResultSet = {
    assert(msiResultSet != null, "buildResultSet() msiResultSet is null")

    val msiResultSetId = msiResultSet.getId.intValue

    val knownResultSets = getEntityCache(classOf[ResultSet])

    val knownResultSet = knownResultSets.get(msiResultSetId)

    if (knownResultSet.isDefined) {
      knownResultSet.get
    } else {
      val msiEm = msiDbCtx.getEntityManager

      /* Peptides & PeptideMatches */
      val msiPeptideMatches = peptideMatchRepo.findPeptideMatchByResultSet(msiEm, msiResultSetId)

      val omPeptideMatches =
        for (msiPeptideMatch <- msiPeptideMatches) yield {
          buildPeptideMatch(msiPeptideMatch, msiResultSetId, msiEm)
        }

      val peptides = getEntityCache(classOf[Peptide]).values.toArray[Peptide]
      logger.trace("Loaded Peptides: " + peptides.size)

      val peptideMatches = omPeptideMatches.toArray[PeptideMatch]
      logger.trace("Loaded PeptideMatches: " + peptideMatches.size)

      /* ProteinMaches */
      val msiProteinMatches = proteinMatchRepo.findProteinMatchesForResultSet(msiEm, msiResultSet)

      val omProteinMatches =
        for (msiProteinMatch <- msiProteinMatches) yield {
          buildProteinMatch(msiProteinMatch, msiResultSetId, msiEm)
        }

      val proteinMatches = omProteinMatches.toArray[ProteinMatch]
      logger.trace("Loaded ProteinMatches: " + proteinMatches.size)

      val rsType = msiResultSet.getType

      // TODO Decoy and Native bools must be coherant with JPARsStorer parseType()
      val isDecoy = if (rsType == Type.DECOY_SEARCH) {
        true
      } else {
        false
      }

      val isNative = if ((rsType == Type.SEARCH) || (rsType == Type.DECOY_SEARCH)) {
        true
      } else {
        false
      }

      /* MsiSearch */
      var msiSearchId: Int = 0
      var optionalMsiSearch: Option[MSISearch] = None

      val msiMsiSearch = msiResultSet.getMsiSearch
      if (msiMsiSearch != null) {
        val omMsiSearch = buildMsiSearch(msiMsiSearch)

        msiSearchId = omMsiSearch.id
        optionalMsiSearch = Some(omMsiSearch)
      }

      /* Decoy RS */
      var decoyRSId: Int = 0
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
        false, // TODO handle isQuantified
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

  private def buildPeptideMatch(msiPeptideMatch: MsiPeptideMatch, resultSetId: Int, msiEm: EntityManager): PeptideMatch = {
    assert(msiPeptideMatch != null, "buildPeptideMatch() msiPeptideMatch is null")

    val msiPeptideMatchId = msiPeptideMatch.getId.intValue

    val knownPeptideMatches = getEntityCache(classOf[PeptideMatch])

    val knownPeptideMatch = knownPeptideMatches.get(msiPeptideMatchId)

    if (knownPeptideMatch.isDefined) {
      knownPeptideMatch.get
    } else {
      /* Handle best child */
      var bestChildId: Int = 0
      var optionalBestChild: Option[PeptideMatch] = None

      val msiBestChild = msiPeptideMatch.getBestPeptideMatch
      if (msiBestChild != null) {
        val omBestChild = buildPeptideMatch(msiBestChild, resultSetId, msiEm)

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
        msiPeptideMatch.getScore,
        scoringRepo.getScoreTypeForId(msiEm, msiPeptideMatch.getScoringId),
        msiPeptideMatch.getDeltaMoz,
        msiPeptideMatch.getIsDecoy.booleanValue,
        retrievePeptide(msiPeptideMatch.getPeptideId.intValue),
        msiPeptideMatch.getMissedCleavage.intValue,
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

  def retrievePeptide(peptideId: Int): Peptide = {
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
        throw new IllegalArgumentException("Peptide #" + peptideId + " NOT found in Ps Db")
      }

    }

  }

  private def buildMsQuery(msiMsQuery: MsiMsQuery): MsQuery = {

    if (msiMsQuery == null) {
      null
    } else {
      val msiMsQueryId = msiMsQuery.getId.intValue

      val knownMsQueries = getEntityCache(classOf[MsQuery])

      val knownMsQuery = knownMsQueries.get(msiMsQueryId)

      if (knownMsQuery.isDefined) {
        knownMsQuery.get
      } else {
        /* Always a Ms2Querry, handle Spectrum */
        var spectrumId: Int = 0
        var spectrumTitle: String = "Unknown"

        val msiSpectrum = msiMsQuery.getSpectrum
        if (msiSpectrum != null) {
          spectrumId = msiSpectrum.getId.intValue
          spectrumTitle = msiSpectrum.getTitle
        }

        val serializedProperties = msiMsQuery.getSerializedProperties

        val msQueryProperties: Option[MsQueryProperties] = if (StringUtils.isEmpty(serializedProperties)) {
          None
        } else {
          Some(Json.parse[MsQueryProperties](serializedProperties))
        }

        val msQuery = new Ms2Query(msiMsQueryId,
          msiMsQuery.getInitialId.intValue,
          msiMsQuery.getMoz,
          msiMsQuery.getCharge.intValue,
          spectrumTitle,
          spectrumId,
          msQueryProperties)

        knownMsQueries += msiMsQueryId -> msQuery

        msQuery
      }

    }

  }

  private def buildProteinMatch(msiProteinMatch: MsiProteinMatch, resultSetId: Int, msiEm: EntityManager): ProteinMatch = {
    assert(msiProteinMatch != null, "buildProteinMatch() msiProteinMatch is null")

    def retrieveProtein(proteinId: Int): Protein = {
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
          throw new IllegalArgumentException("Protein #" + proteinId + " NOT found in Pdi Db")
        }

      }

    }

    val msiProteinMatchId = msiProteinMatch.getId.intValue

    /* TaxonId java Integer to Scala Int... */
    var taxonId: Int = 0

    val msiTaxonId = msiProteinMatch.getTaxonId
    if (msiTaxonId != null) {
      taxonId = msiTaxonId.intValue
    }

    /* Optional BioSequence (Protein) */
    var proteinId: Int = 0
    var optionalProtein: Option[Protein] = None

    val msiProteinId = msiProteinMatch.getBioSequenceId
    if (msiProteinId != null) {

      val numericProteinId = msiProteinId.intValue
      if (numericProteinId > 0) {
        val omProtein = retrieveProtein(numericProteinId)

        proteinId = omProtein.id
        optionalProtein = Some(omProtein)
      }

    }

    /* SeqDatabase Ids */
    val seqDatabasesIds = Array.newBuilder[Int]

    val msiSeqDatabaseIds = seqDatabaseRepo.findSeqDatabaseIdsForProteinMatch(msiEm, msiProteinMatchId)
    if ((msiSeqDatabaseIds != null) && !msiSeqDatabaseIds.isEmpty) {

      for (msiSeqDatabaseId <- msiSeqDatabaseIds) {
        seqDatabasesIds += msiSeqDatabaseId.intValue
      }

    }

    /* SequenceMatches */
    val sequenceMatches = Array.newBuilder[SequenceMatch]

    val msiSequenceMatches = sequenceMatchRepo.findSequenceMatchForProteinMatch(msiEm, msiProteinMatchId)

    if ((msiSequenceMatches != null) && !msiSequenceMatches.isEmpty) {

      for (msiSequenceMatch <- msiSequenceMatches) {
        sequenceMatches += buildSequenceMatch(msiSequenceMatch)
      }

    }

    new ProteinMatch(msiProteinMatch.getAccession,
      msiProteinMatch.getDescription,
      msiProteinMatch.getIsDecoy.booleanValue,
      msiProteinMatch.getIsLastBioSequence.booleanValue,
      msiProteinMatchId,
      taxonId,
      resultSetId,
      proteinId,
      optionalProtein,
      seqDatabasesIds.result,
      msiProteinMatch.getGeneName,
      msiProteinMatch.getScore,
      scoringRepo.getScoreTypeForId(msiEm, msiProteinMatch.getScoringId),
      msiProteinMatch.getCoverage,
      msiProteinMatch.getPeptideMatchCount.intValue,
      sequenceMatches.result,
      None // TODO handle properties
    )
  }

  private def buildMsiSearch(msiSearch: MsiSearch): MSISearch = {
    assert(msiSearch != null, "buildMsiSearch() msiSearch is null")

    new MSISearch(msiSearch.getId.intValue,
      msiSearch.getResultFileName,
      msiSearch.getSubmittedQueriesCount.intValue,
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

    new SearchSettings(msiSearchSetting.getId.intValue,
      msiSearchSetting.getSoftwareName,
      msiSearchSetting.getSoftwareVersion,
      msiSearchSetting.getTaxonomy,
      msiSearchSetting.getMaxMissedCleavages.intValue,
      msiSearchSetting.getPeptideChargeStates,
      msiSearchSetting.getPeptideMassErrorTolerance,
      msiSearchSetting.getPeptideMassErrorToleranceUnit,
      msiSearchSetting.getIsDecoy.booleanValue,
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

    val msiSeqDatabaseId = msiSeqDatabase.getId.intValue

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

  private def buildSequenceMatch(msiSequenceMatch: MsiSequenceMatch): SequenceMatch = {
    assert(msiSequenceMatch != null, "buildSequenceMatch() msiSeqDatabase is null")

    val msiSequenceMatchId = msiSequenceMatch.getId

    /* Peptide */
    val peptide = retrievePeptide(msiSequenceMatchId.getPeptideId.intValue)

    /* BestPeptideMatch */
    val msiBestPeptideMatchId = msiSequenceMatch.getBestPeptideMatchId.intValue

    val knownPeptideMatches = getEntityCache(classOf[PeptideMatch])

    val knownBestPeptideMatch = knownPeptideMatches.get(msiBestPeptideMatchId)

    if (knownBestPeptideMatch.isEmpty) {
      throw new IllegalArgumentException("Unknown best PeptideMatch Id: " + msiBestPeptideMatchId)
    }

    new SequenceMatch(msiSequenceMatchId.getStart.intValue,
      msiSequenceMatchId.getStop.intValue,
      StringUtils.convertStringResidueToChar(msiSequenceMatch.getResidueBefore),
      StringUtils.convertStringResidueToChar(msiSequenceMatch.getResidueAfter),
      msiSequenceMatch.getIsDecoy.booleanValue,
      msiSequenceMatch.getResultSetId.intValue,
      peptide.id,
      Some(peptide),
      knownBestPeptideMatch.get.id,
      knownBestPeptideMatch,
      None) // TODO handle properties
  }

  private def buildInstrumentConfig(msiInstrumentConfig: MsiInstrumentConfig): InstrumentConfig = {
    assert(msiInstrumentConfig != null, "buildInstrumentConfig() msiInstrumentConfig is null")

    val msiInstrumentConfigId = msiInstrumentConfig.getId.intValue

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

    new Peaklist(msiPeaklist.getId.intValue,
      msiPeaklist.getType,
      msiPeaklist.getPath,
      msiPeaklist.getRawFileName,
      msiPeaklist.getMsLevel.intValue,
      msiPeaklist.getSpectrumDataCompression,
      buildPeaklistSoftware(msiPeaklist.getPeaklistSoftware))
  }

  private def buildPeaklistSoftware(msiPeaklistSoftware: MsiPeaklistSoftware): PeaklistSoftware = {
    assert(msiPeaklistSoftware != null, "buildPeaklistSoftware() msiPeaklistSoftware is null")

    val msiPeaklistSoftwareId = msiPeaklistSoftware.getId.intValue

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