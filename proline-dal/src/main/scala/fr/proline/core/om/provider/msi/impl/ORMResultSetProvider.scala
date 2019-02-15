package fr.proline.core.om.provider.msi.impl

import javax.persistence.EntityManager

import com.typesafe.scalalogging.LazyLogging
import fr.profi.chemistry.model.Enzyme
import fr.profi.util.StringUtils
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbProteinMatchSeqDatabaseMapTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.ResultSetFilter
import fr.proline.core.orm.msi.MsiSearch
import fr.proline.core.orm.msi.ResultSet.Type
import fr.proline.core.orm.msi.repository.{PeptideMatchRepository => peptideMatchRepo}
import fr.proline.core.orm.msi.repository.{ProteinMatchRepository => proteinMatchRepo}
import fr.proline.core.orm.msi.repository.{ScoringRepository => scoringRepo}
import fr.proline.core.orm.msi.repository.{SequenceMatchRepository => sequenceMatchRepo}
import fr.proline.core.util.ResidueUtils._
import fr.proline.repository.util.JPAUtils

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

class ORMResultSetProvider(
  val msiDbCtx: MsiDbConnectionContext
) extends IResultSetProvider with LazyLogging {

  require((msiDbCtx != null) && msiDbCtx.isJPA, "Invalid MSI Db Context")

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

  val peptideProvider = new ORMPeptideProvider(msiDbCtx)

  /* OM Entity caches */
  private val entityCaches = mutable.Map.empty[Class[_], mutable.Map[Long, _]]

  def getResultSetsAsOptions(resultSetIds: Seq[Long], resultSetFilter: Option[ResultSetFilter] = None): Array[Option[ResultSet]] = {
    val resultSets = Array.newBuilder[Option[ResultSet]]

    for (resultSetId <- resultSetIds) {
      resultSets += getResultSet(resultSetId)
    }

    resultSets.result
  }

  def getResultSets(resultSetIds: Seq[Long], resultSetFilter: Option[ResultSetFilter] = None): Array[ResultSet] = {
    val resultSets = Array.newBuilder[ResultSet]

    for (resultSetId <- resultSetIds) {
      val resultSet = getResultSet(resultSetId)

      if (resultSet.isDefined) {
        resultSets += resultSet.get
      }

    }

    resultSets.result
  }

  override def getResultSet(resultSetId: Long, resultSetFilter: Option[ResultSetFilter] = None): Option[ResultSet] = {
    val start = System.currentTimeMillis()
    val msiEM = msiDbCtx.getEntityManager
    JPAUtils.checkEntityManager(msiEM)
    val msiResultSet = msiEM.find(classOf[MsiResultSet], resultSetId)

    if (msiResultSet == null) {
      None
    } else {
      val rs = buildResultSet(msiResultSet)
      logger.info("ResultSet #" + msiResultSet.getId + " ["+rs.proteinMatches.length+" PMs, "+rs.peptides.length+" Pes, "+rs.peptideMatches.length+" PeMs] loaded in "+(System.currentTimeMillis()-start)+" ms")
      Some(rs)
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
    logger.trace(" BUILD RS "+msiResultSetId)
    
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
      logger.trace("Loaded Peptides: " + peptides.length)

      val peptideMatches = omPeptideMatches.toArray[PeptideMatch]
      logger.trace("Loaded PeptideMatches: " + peptideMatches.length)

      /* ProteinMaches */
      val msiProteinMatches = proteinMatchRepo.findProteinMatchesForResultSet(msiEM, msiResultSet)
      
      
      val seqDatabasesIdsByProtMatchId  = new HashMap[Long, ArrayBuffer[Long]]()
	  DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      	val sqIDProtMatchIDQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      		List(t.PROTEIN_MATCH_ID, t.SEQ_DATABASE_ID) -> "WHERE "~ t.RESULT_SET_ID ~" = "~ msiResultSetId ~" "
		)
      
		msiEzDBC.selectAndProcess(sqIDProtMatchIDQuery){ r =>
         	val( protMatchId, seqDBId ) = (toLong(r.nextAny), r.nextInt)
         	val seqDbs  = seqDatabasesIdsByProtMatchId.getOrElseUpdate(protMatchId, new ArrayBuffer[Long]())
         	seqDbs += seqDBId
        	seqDatabasesIdsByProtMatchId.put(protMatchId, seqDbs)
	 	} 
	 
	  })
 
	 	  
	  val msiSequenceMatchesByProtMatchID = new HashMap[Long, ArrayBuffer[MsiSequenceMatch]]()
      sequenceMatchRepo.findSequenceMatchForResultSet(msiEM, msiResultSetId).foreach(msiSM => {
         val seqMatches  = msiSequenceMatchesByProtMatchID.getOrElseUpdate(msiSM.getId.getProteinMatchId, new ArrayBuffer[MsiSequenceMatch]())
         seqMatches += msiSM
		  msiSequenceMatchesByProtMatchID.put(msiSM.getId.getProteinMatchId,seqMatches)
      })
	  
	  
      val omProteinMatches = new ArrayBuffer[ProteinMatch]()
      for (msiProteinMatch <- msiProteinMatches)  {
         omProteinMatches += buildProteinMatch(msiProteinMatch, msiResultSetId, seqDatabasesIdsByProtMatchId, msiSequenceMatchesByProtMatchID, msiEM)
      }
      

      val proteinMatches = omProteinMatches.toArray[ProteinMatch]
      logger.trace("Loaded ProteinMatches: " + proteinMatches.length)

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
      
      val isValidatedContent = false

      val isQuantified = rsType == Type.QUANTITATION

      /* MsiSearch */
      var msiSearchId: Long = 0L
      var optionalMsiSearch: Option[MSISearch] = None

      val msiMsiSearch = msiResultSet.getMsiSearch
      if (msiMsiSearch != null) {
        val omMsiSearch = buildMsiSearch(msiMsiSearch)

        msiSearchId = omMsiSearch.id
        optionalMsiSearch = Some(omMsiSearch)
      }
      
      /* Child MsiSearches */
      val childMsiSearches = new ArrayBuffer[MSISearch]
      
      val msiChildResultSets = msiResultSet.getChildren
      if( msiChildResultSets != null ) {
        
        for( msiChildResultSet <- msiChildResultSets ) {
          val msiChildMsiSearch = msiChildResultSet.getMsiSearch
          
          if (msiChildMsiSearch != null) {
            childMsiSearches += buildMsiSearch(msiChildMsiSearch)
          }
        }
      }

      /* Merged RSM Id*/
      var mergedRSMId: Long = if(msiResultSet.getMergedRsmId != null) { msiResultSet.getMergedRsmId()}  else { 0L}
      
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
        Some(ProfiJson.deserialize[ResultSetProperties](serializedProperties))
      }

      val resultSet = new ResultSet(
        peptides,
        peptideMatches,
        proteinMatches,
        isDecoy,
        isNative,
        isValidatedContent,
        mergedRSMId,
        msiResultSetId,
        msiResultSet.getName,
        msiResultSet.getDescription,
        isQuantified,
        msiSearchId,
        optionalMsiSearch,
        childMsiSearches.toArray,
        decoyRSId,
        optionalDecoyRS,
        resultSetProperties
      )

      knownResultSets += msiResultSetId -> resultSet

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
        Some(ProfiJson.deserialize[PeptideMatchProperties](serializedProperties))
      }
      
      val cdPrettyRank = msiPeptideMatch.getCDPrettyRank
      val sdPrettyRank = msiPeptideMatch.getSDPrettyRank
      val scoreTypeAsStr = scoringRepo.getScoreTypeForId(msiEM, msiPeptideMatch.getScoringId)
      
      // FIXME: sometimes getScoreTypeForId return null values
      val scoreType = try {
        PeptideMatchScoreType.withName( scoreTypeAsStr )
      } catch {
        case e: Exception => {
          logger.error(s"can't convert $scoreTypeAsStr into PeptideMatchScoreType enum value, default to mascot:ions score")
          PeptideMatchScoreType.MASCOT_IONS_SCORE
        }
      }

      val peptideMatch = new PeptideMatch(
        msiPeptideMatchId,
        msiPeptideMatch.getRank.intValue,
        msiPeptideMatch.getScore.floatValue,
        scoreType,
        msiPeptideMatch.getCharge,
        msiPeptideMatch.getDeltaMoz.floatValue,
        msiPeptideMatch.getIsDecoy,
        retrievePeptide(msiPeptideMatch.getPeptideId),
        msiPeptideMatch.getMissedCleavage,
        msiPeptideMatch.getFragmentMatchCount.intValue,
        buildMsQuery(msiPeptideMatch.getMsQuery),
        true, // isValidated always true when loading from ORM
        resultSetId,
        if( cdPrettyRank != null ) cdPrettyRank.intValue else 0,
        if( sdPrettyRank != null ) sdPrettyRank.intValue else 0,
        null, // TODO handle children
        optionalBestChild.map( Array(_) ), // TODO handle children (only the best child is loaded now)
        bestChildId,
        peptideMatchProperties,
        None
      )

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

      require((optionalPeptide != null) && optionalPeptide.isDefined, s"Peptide #$peptideId NOT found in MSI Db")

      val peptide = optionalPeptide.get

      knownPeptides += peptideId -> peptide

      peptide
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
          Some(ProfiJson.deserialize[MsQueryProperties](serializedProperties))
        }

        val msQuery = new Ms2Query(
         id = msiMsQueryId,
         initialId = msiMsQuery.getInitialId,
         moz = msiMsQuery.getMoz,
         charge = msiMsQuery.getCharge,
         spectrumTitle = spectrumTitle,
         spectrumId = spectrumId,
         msiSearchId = msiMsQuery.getMsiSearch.getId.longValue(),
         properties = msQueryProperties
       )

        knownMsQueries += msiMsQueryId -> msQuery

        msQuery
      }

    }

  }
  
  private def buildProteinMatch(msiProteinMatch: MsiProteinMatch, resultSetId: Long,  seqDatabasesIdsByProtMatchId :  HashMap[Long, ArrayBuffer[Long]], msiSequenceMatchesByProtMatchID : HashMap[Long, ArrayBuffer[MsiSequenceMatch]], msiEM: EntityManager): ProteinMatch = {
    assert(msiProteinMatch != null, "buildProteinMatch() msiProteinMatch is null")

    def retrieveProtein(proteinId: Long): Option[Protein] = {
      val knownProteins = getEntityCache(classOf[Protein])
      knownProteins.get(proteinId)
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
        val omProteinOpt = retrieveProtein(numericProteinId)

        proteinId = numericProteinId
        optionalProtein = omProteinOpt
      }

    }

    /* SeqDatabase Ids */
	val seqDatabasesIds = if(seqDatabasesIdsByProtMatchId.get(msiProteinMatchId).isEmpty) null else {seqDatabasesIdsByProtMatchId.get(msiProteinMatchId).get.toArray }

    /* SequenceMatches */
    val sequenceMatches = Array.newBuilder[SequenceMatch]
    val msiSequenceMatches = if(msiSequenceMatchesByProtMatchID.get(msiProteinMatchId).isEmpty) null else {msiSequenceMatchesByProtMatchID.get(msiProteinMatchId).get.toArray}
    
    if ((msiSequenceMatches != null) && !msiSequenceMatches.isEmpty) {

      for (msiSequenceMatch <- msiSequenceMatches) {
        sequenceMatches += buildSequenceMatch(msiSequenceMatch, resultSetId, msiEM)
      }

    }
    

    new ProteinMatch(
      msiProteinMatch.getAccession,
      msiProteinMatch.getDescription,
      msiProteinMatch.getIsDecoy,
      msiProteinMatch.getIsLastBioSequence,
      msiProteinMatchId,
      taxonId,
      resultSetId,
      proteinId,
      optionalProtein,
      seqDatabasesIds,
      msiProteinMatch.getGeneName,
      msiProteinMatch.getScore.floatValue,
      scoringRepo.getScoreTypeForId(msiEM, msiProteinMatch.getScoringId),
      msiProteinMatch.getPeptideMatchCount.intValue,
      sequenceMatches.result,
      None // TODO handle properties
    )
  }

  private def buildMsiSearch(msiSearch: MsiSearch): MSISearch = {
    assert(msiSearch != null, "buildMsiSearch() msiSearch is null")

    new MSISearch(msiSearch.getId,
      msiSearch.getResultFileName,
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
      Some(ProfiJson.deserialize[SearchSettingsProperties](serializedProperties))
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
      None, //VDS TODO Read Fragmentation Rule Set
      None,
      None,
      searchSettingsProperties)
  }

  private def buildPtmDefinition(msiUsedPtm: MsiUsedPtm): PtmDefinition = {
    assert(msiUsedPtm != null, "buildPtmDefinition() msiUsedPtm is null")

    val msiPtmSpecificity = msiUsedPtm.getPtmSpecificity

    val names = new PtmNames(msiUsedPtm.getShortName, null) // TODO handle fullName

    new PtmDefinition(
      msiPtmSpecificity.getId,
      msiPtmSpecificity.getLocation,
      names,
      new Array[PtmEvidence](0), // TODO handle ptmEvidences
      characterToScalaChar(msiPtmSpecificity.getResidue),
      null, // TODO handle classification
      0 // TODO handle PTM Id (MSI PTM)
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
      characterToScalaChar(msiSequenceMatch.getResidueBefore),
      characterToScalaChar(msiSequenceMatch.getResidueAfter),
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
      msiPeaklist.getRawFileIdentifier,
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
