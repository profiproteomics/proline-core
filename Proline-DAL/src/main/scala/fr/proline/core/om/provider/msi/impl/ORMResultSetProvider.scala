package fr.proline.core.om.provider.msi.impl

import scala.collection.JavaConversions.{asScalaSet, asScalaBuffer}
import scala.collection.mutable
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.{SequenceMatch, SeqDatabase, SearchSettings, ResultSet, PtmNames, PtmEvidence, PtmDefinition, ProteinMatch, Protein, PeptideMatch, Peptide, PeaklistSoftware, Peaklist, MsQuery, Ms2Query, MSISearch, InstrumentConfig}
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.orm.msi.ResultSet.Type
import fr.proline.core.orm.msi.repository.{SequenceMatchRepository, ScoringRepository, ProteinMatchRepository, PeptideMatchRepository, MsiSeqDatabaseRepository}
import fr.proline.core.orm.msi.MsiSearch
import fr.proline.repository.util.JPAUtils
import fr.proline.util.DateUtils
import fr.proline.util.StringUtils
import javax.persistence.EntityManager

class ORMResultSetProvider(private val msiEm: EntityManager,
  private val psEm: EntityManager,
  private val pdiEm: EntityManager) extends IResultSetProvider with Logging {

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

  JPAUtils.checkEntityManager(msiEm)
  JPAUtils.checkEntityManager(psEm)
  JPAUtils.checkEntityManager(pdiEm)

  val scoringRepo = new ScoringRepository(msiEm)

  val seqDatabaseRepo = new MsiSeqDatabaseRepository(msiEm)

  /* Ps providers */
  val peptideProvider = new ORMPeptideProvider(psEm)

  /* Pdi providers */
  val proteinProvider = new ORMProteinProvider(pdiEm)

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
    val msiResultSet = msiEm.find(classOf[MsiResultSet], resultSetId)

    if (msiResultSet == null) {
      None
    } else {
      Some(buildResultSet(msiResultSet))
    }

  }

  /* Private methods */
  private def getEntityCache[T](classifier: Class[T]): mutable.Map[Int, T] = {
    assert(classifier != null, "JPAResultSetProvider.getEntityCache() classifier is null")

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
    assert(msiResultSet != null, "JPAResultSetProvider.buildResultSet() msiResultSet is null")

    val msiResultSetId = msiResultSet.getId.intValue

    val knownResultSets = getEntityCache(classOf[ResultSet])

    val knownResultSet = knownResultSets.get(msiResultSetId)

    if (knownResultSet.isDefined) {
      knownResultSet.get
    } else {
      /* Peptides & PeptideMatches */
      val peptideMatchRepo = new PeptideMatchRepository(msiEm)

      val msiPeptideMatches = peptideMatchRepo.findPeptideMatchByResultSet(msiResultSetId)

      val omPeptideMatches =
        for (msiPeptideMatch <- msiPeptideMatches) yield {
          buildPeptideMatch(msiPeptideMatch, msiResultSetId)
        }

      val peptides = getEntityCache(classOf[Peptide]).values.toArray[Peptide]
      logger.info("Loaded Peptides: " + peptides.size)

      val peptideMatches = omPeptideMatches.toArray[PeptideMatch]
      logger.info("Loaded PeptideMatches: " + peptideMatches.size)

      /* ProteinMaches */
      val proteinMatchRepo = new ProteinMatchRepository(msiEm)

      val msiProteinMatches = proteinMatchRepo.findProteinMatchesForResultSet(msiResultSet)

      val sequenceMatchRepo = new SequenceMatchRepository(msiEm)

      val omProteinMatches =
        for (msiProteinMatch <- msiProteinMatches) yield {
          buildProteinMatch(msiProteinMatch, sequenceMatchRepo, msiResultSetId)
        }

      val proteinMatches = omProteinMatches.toArray[ProteinMatch]
      logger.info("Loaded ProteinMatches: " + proteinMatches.size)

      val rsType = msiResultSet.getType()

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
      val msiSearch = buildMsiSearch(msiResultSet.getMsiSearch)

      /* Decoy RS */
      var decoyRsId: Int = 0
      var decoyRs: Option[ResultSet] = None

      val msiDecoyRs = msiResultSet.getDecoyResultSet
      if (msiDecoyRs != null) {
        val definedDecoyRs = buildResultSet(msiDecoyRs)

        decoyRsId = definedDecoyRs.id
        decoyRs = Some(definedDecoyRs)
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
        msiSearch.id,
        msiSearch,
        decoyRsId,
        decoyRs,
        null // TODO handle properties
        )

      knownResultSets += msiResultSetId -> resultSet

      resultSet
    }

  }

  private def buildPeptideMatch(msiPeptideMatch: MsiPeptideMatch, resultSetId: Int): PeptideMatch = {
    assert(msiPeptideMatch != null, "JPAResultSetProvider.buildPeptideMatch() msiPeptideMatch is null")

    val msiPeptideMatchId = msiPeptideMatch.getId.intValue

    val knownPeptideMatches = getEntityCache(classOf[PeptideMatch])

    val knownPeptideMatch = knownPeptideMatches.get(msiPeptideMatchId)

    if (knownPeptideMatch.isDefined) {
      knownPeptideMatch.get
    } else {
      /* Handle best child */
      var bestChildId: Int = 0
      var bestChild: Option[PeptideMatch] = None

      val msiBestChild = msiPeptideMatch.getBestPeptideMatch
      if (msiBestChild != null) {
        val definedBestChild = buildPeptideMatch(msiBestChild, resultSetId)

        bestChildId = definedBestChild.id
        bestChild = Some(definedBestChild)
      }

      val peptideMatch = new PeptideMatch(msiPeptideMatchId,
        msiPeptideMatch.getRank,
        msiPeptideMatch.getScore,
        scoringRepo.getScoreTypeForId(msiPeptideMatch.getScoringId),
        msiPeptideMatch.getDeltaMoz,
        msiPeptideMatch.getIsDecoy,
        retrievePeptide(msiPeptideMatch.getPeptideId),
        msiPeptideMatch.getMissedCleavage,
        msiPeptideMatch.getFragmentMatchCount,
        buildMsQuery(msiPeptideMatch.getMsQuery),
        false, // isValidated always false when loading from ORM
        resultSetId,
        null, // TODO handle children
        null, // TODO handle children
        bestChildId,
        bestChild,
        None, // TODO handle properties
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
      val peptide = peptideProvider.getPeptide(peptideId)

      if ((peptide != null) && peptide.isDefined) {
        val definedPeptide = peptide.get

        knownPeptides += peptideId -> definedPeptide

        definedPeptide
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
          spectrumId = msiSpectrum.getId
          spectrumTitle = msiSpectrum.getTitle
        }

        val msQuery = new Ms2Query(msiMsQueryId,
          msiMsQuery.getInitialId,
          msiMsQuery.getMoz,
          msiMsQuery.getCharge,
          spectrumTitle,
          spectrumId,
          None) // TODO handle properties

        knownMsQueries += msiMsQueryId -> msQuery

        msQuery
      }

    }

  }

  private def buildProteinMatch(msiProteinMatch: MsiProteinMatch, sequenceMatchRepo: SequenceMatchRepository, resultSetId: Int): ProteinMatch = {
    assert(msiProteinMatch != null, "JPAResultSetProvider.buildProteinMatch() msiProteinMatch is null")

    def retrieveProtein(proteinId: Int): Protein = {
      val knownProteins = getEntityCache(classOf[Protein])

      val knownProtein = knownProteins.get(proteinId)

      if (knownProtein.isDefined) {
        knownProtein.get
      } else {
        val protein = proteinProvider.getProtein(proteinId)

        if ((protein != null) && protein.isDefined) {
          val definedProtein = protein.get

          knownProteins += proteinId -> definedProtein

          definedProtein
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
    var protein: Option[Protein] = None

    val msiProteinId = msiProteinMatch.getBioSequenceId
    if (msiProteinId != null) {

      val numericProteinId = msiProteinId.intValue
      if (numericProteinId > 0) {
        val definedProtein = retrieveProtein(numericProteinId)

        proteinId = definedProtein.id
        protein = Some(definedProtein)
      }

    }

    /* SeqDatabase Ids */
    val seqDatabasesIds = Array.newBuilder[Int]

    val msiSeqDatabaseIds = seqDatabaseRepo.findSeqDatabaseIdsForProteinMatch(msiProteinMatchId)
    if ((msiSeqDatabaseIds != null) && !msiSeqDatabaseIds.isEmpty) {
      for (msiSeqDatabaseId <- msiSeqDatabaseIds) {
        seqDatabasesIds += msiSeqDatabaseId.intValue
      }
    }

    /* SequenceMatches */
    val sequenceMatches = Array.newBuilder[SequenceMatch]

    val msiSequenceMatches = sequenceMatchRepo.findSequenceMatchForProteinMatch(msiProteinMatchId)

    if ((msiSequenceMatches != null) && !msiSequenceMatches.isEmpty) {

      for (msiSequenceMatch <- msiSequenceMatches) {
        sequenceMatches += buildSequenceMatch(msiSequenceMatch)
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
      protein,
      seqDatabasesIds.result,
      msiProteinMatch.getGeneName,
      msiProteinMatch.getScore,
      scoringRepo.getScoreTypeForId(msiProteinMatch.getScoringId),
      msiProteinMatch.getCoverage,
      msiProteinMatch.getPeptideMatchCount,
      sequenceMatches.result,
      None // TODO handle properties
      )
  }

  private def buildMsiSearch(msiSearch: MsiSearch): MSISearch = {
    assert(msiSearch != null, "JPAResultSetProvider.buildMsiSearch() msiSearch is null")

    new MSISearch(msiSearch.getId,
      msiSearch.getResultFileName,
      msiSearch.getSubmittedQueriesCount,
      buildSearchSettings(msiSearch.getSearchSetting),
      buildPeaklist(msiSearch.getPeaklist),
      msiSearch.getDate,
      msiSearch.getTitle,
      msiSearch.getResultFileDirectory,
      msiSearch.getJobNumber,
      msiSearch.getUserName,
      msiSearch.getUserEmail,
      msiSearch.getQueriesCount,
      msiSearch.getSearchedSequencesCount)
  }

  private def buildSearchSettings(msiSearchSetting: MsiSearchSetting): SearchSettings = {
    assert(msiSearchSetting != null, "JPAResultSetProvider.buildSearchSettings() msiSearchSetting is null")

    /* Enzymes */
    val msiEnzymes = msiSearchSetting.getEnzymes

    val enzymes = if ((msiEnzymes == null) || msiEnzymes.isEmpty) {
      new Array[String](0) // An empty array
    } else {

      (for (msiEnzyme <- msiEnzymes) yield {
        msiEnzyme.getName
      }).toArray[String]

    }

    /* Used PTMs */
    val variablePtmDefs = Array.newBuilder[PtmDefinition]
    val fixedPtmDefs = Array.newBuilder[PtmDefinition]

    val msiUsedPtms = msiSearchSetting.getUsedPtms
    if ((msiUsedPtms != null) && !msiUsedPtms.isEmpty()) {
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

    new SearchSettings(msiSearchSetting.getId,
      msiSearchSetting.getSoftwareName,
      msiSearchSetting.getSoftwareVersion,
      msiSearchSetting.getTaxonomy,
      msiSearchSetting.getMaxMissedCleavages,
      msiSearchSetting.getPeptideChargeStates,
      msiSearchSetting.getPeptideMassErrorTolerance,
      msiSearchSetting.getPeptideMassErrorToleranceUnit,
      msiSearchSetting.getIsDecoy,
      enzymes,
      variablePtmDefs.result,
      fixedPtmDefs.result,
      seqDatabases.result,
      buildInstrumentConfig(msiSearchSetting.getInstrumentConfig),
      msiSearchSetting.getQuantitation)
  }

  private def buildPtmDefinition(msiUsedPtm: MsiUsedPtm): PtmDefinition = {
    assert(msiUsedPtm != null, "JPAResultSetProvider.buildPtmDefinition() msiUsedPtm is null")

    val msiPtmSpecificity = msiUsedPtm.getPtmSpecificity

    val names = new PtmNames(msiUsedPtm.getShortName(), null) // TODO handle fullName

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
    assert(msiSeqDatabase != null, "JPAResultSetProvider.buildSeqDatabase() msiSeqDatabase is null")

    val msiSeqDatabaseId = msiSeqDatabase.getId.intValue

    val knownSeqDatabases = getEntityCache(classOf[SeqDatabase])

    val knownSeqDatabase = knownSeqDatabases.get(msiSeqDatabaseId)

    if (knownSeqDatabase.isDefined) {
      knownSeqDatabase.get
    } else {
      val seqDatabase = new SeqDatabase(msiSeqDatabaseId,
        msiSeqDatabase.getName,
        msiSeqDatabase.getFastaFilePath,
        msiSeqDatabase.getSequenceCount,
        DateUtils.formatReleaseDate(msiSeqDatabase.getReleaseDate),
        msiSeqDatabase.getVersion)

      knownSeqDatabases += msiSeqDatabaseId -> seqDatabase

      seqDatabase
    }

  }

  private def buildSequenceMatch(msiSequenceMatch: MsiSequenceMatch): SequenceMatch = {
    assert(msiSequenceMatch != null, "JPAResultSetProvider.buildSequenceMatch() msiSeqDatabase is null")

    val msiSequenceMatchId = msiSequenceMatch.getId

    /* Peptide */
    val peptide = retrievePeptide(msiSequenceMatchId.getPeptideId)

    /* BestPeptideMatch */
    val msiBestPeptideMatchId = msiSequenceMatch.getBestPeptideMatchId.intValue

    val knownPeptideMatches = getEntityCache(classOf[PeptideMatch])

    val knownBestPeptideMatch = knownPeptideMatches.get(msiBestPeptideMatchId)

    if (knownBestPeptideMatch.isEmpty) {
      throw new IllegalArgumentException("Unknown best PeptideMatch Id: " + msiBestPeptideMatchId)
    }

    new SequenceMatch(msiSequenceMatchId.getStart,
      msiSequenceMatchId.getStop,
      StringUtils.convertStringResidueToChar(msiSequenceMatch.getResidueBefore),
      StringUtils.convertStringResidueToChar(msiSequenceMatch.getResidueAfter),
      msiSequenceMatch.getIsDecoy,
      msiSequenceMatch.getResultSetId,
      peptide.id,
      Some(peptide),
      knownBestPeptideMatch.get.id,
      knownBestPeptideMatch,
      None) // TODO handle properties
  }

  private def buildInstrumentConfig(msiInstrumentConfig: MsiInstrumentConfig): InstrumentConfig = {
    assert(msiInstrumentConfig != null, "JPAResultSetProvider.buildInstrumentConfig() msiInstrumentConfig is null")

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
    assert(msiPeaklist != null, "JPAResultSetProvider.buildPeaklist() msiPeaklist is null")

    new Peaklist(msiPeaklist.getId,
      msiPeaklist.getType,
      msiPeaklist.getPath,
      msiPeaklist.getRawFileName,
      msiPeaklist.getMsLevel,
      buildPeaklistSoftware(msiPeaklist.getPeaklistSoftware))
  }

  private def buildPeaklistSoftware(msiPeaklistSoftware: MsiPeaklistSoftware): PeaklistSoftware = {
    assert(msiPeaklistSoftware != null, "JPAResultSetProvider.buildPeaklistSoftware() msiPeaklistSoftware is null")

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