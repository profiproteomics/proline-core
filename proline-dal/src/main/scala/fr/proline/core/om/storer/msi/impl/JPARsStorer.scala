package fr.proline.core.om.storer.msi.impl

import java.sql.Timestamp
import scala.collection.JavaConverters._
import scala.collection.mutable
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.om.model.msi.{ InstrumentConfig, LocatedPtm, MSISearch, Ms2Query, MsQuery, PeaklistSoftware, Peptide, PeptideMatch, ProteinMatch, PtmDefinition, ResultSet, SeqDatabase, SequenceMatch }
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.core.om.util.PeptideIdent
import fr.proline.core.orm.msi.MsiSearch
import fr.proline.core.orm.msi.PeptideReadablePtmString
import fr.proline.core.orm.msi.PeptideReadablePtmStringPK
import fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMapPK
import fr.proline.core.orm.msi.ResultSet.Type
import fr.proline.core.orm.msi.SequenceMatchPK
import fr.proline.core.orm.msi.repository.{
  MsiEnzymeRepository => msiEnzymeRepo,
  MsiInstrumentConfigRepository => msiInstrumentConfigRepo,
  MsiPeaklistSoftwareRepository => msiPeaklistSoftwareRepo,
  MsiSeqDatabaseRepository => msiSeqDatabaseRepo,
  ScoringRepository => msiScoringRepo
}
import fr.proline.core.orm.msi.repository.{ MsiPeptideRepository => msiPeptideRepo }
import fr.proline.core.orm.uds.repository.{
  UdsEnzymeRepository => udsEnzymeRepo,
  UdsInstrumentConfigurationRepository => udsInstrumentConfigRepo,
  UdsPeaklistSoftwareRepository => udsPeaklistSoftwareRepo
}
import fr.profi.util.StringUtils
import fr.proline.core.util.ResidueUtils._
import scala.collection.mutable.ArrayBuffer


/**
 * JPA implementation of ResultSet storer.
 *
 */
class JPARsStorer(override val pklWriter: Option[IPeaklistWriter] = None) extends AbstractRsStorer(pklWriter) with LazyLogging {

  type MsiPeaklist = fr.proline.core.orm.msi.Peaklist
  type MsiPeaklistSoftware = fr.proline.core.orm.msi.PeaklistSoftware
  type MsiSearchSetting = fr.proline.core.orm.msi.SearchSetting
  type MsiMSMSSearchSetting = fr.proline.core.orm.msi.MsmsSearch
  type MsiInstrumentConfig = fr.proline.core.orm.msi.InstrumentConfig
  type MsiEnzyme = fr.proline.core.orm.msi.Enzyme
  type MsiSearchSettingsSeqDatabaseMap = fr.proline.core.orm.msi.SearchSettingsSeqDatabaseMap
  type MsiSeqDatabase = fr.proline.core.orm.msi.SeqDatabase
  type MsiPtmSpecificity = fr.proline.core.orm.msi.PtmSpecificity
  type MsiUsedPtm = fr.proline.core.orm.msi.UsedPtm
  type MsiPeptideMatch = fr.proline.core.orm.msi.PeptideMatch
  type MsiPeptide = fr.proline.core.orm.msi.Peptide
  type MsiMsQuery = fr.proline.core.orm.msi.MsQuery
  type MsiSpectrum = fr.proline.core.orm.msi.Spectrum
  type MsiProteinMatch = fr.proline.core.orm.msi.ProteinMatch
  type MsiBioSequence = fr.proline.core.orm.msi.BioSequence
  type MsiProteinMatchSeqDatabaseMap = fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMap
  type MsiSequenceMatch = fr.proline.core.orm.msi.SequenceMatch

  type MsiPtm = fr.proline.core.orm.msi.Ptm
  type MsiPeptidePtm = fr.proline.core.orm.msi.PeptidePtm

  /**
   * Persists a sequence of MsQuery objects into Msi Db. (already persisted MsQueries
   * are cached into {{{storerContext}}} object).
   *
   * Transaction on MSI {{{EntityManager}}} should be opened by client code.
   *
   * @param msiSearchId Id (from StoreContext cache or Msi Primary key) of associated MsiSearch entity,
   * must be attached to {{{msiEm}}} persistence context before calling this method.
   * @param msQueries Sequence of MsQuery object, the sequence must not be {{{null}}}.
   *
   */
  override def storeMsQueries(msiSearchId: Long, msQueries: Seq[MsQuery], storerContext: StorerContext): StorerContext = {

    require(msQueries != null, "MsQueries seq is null")

    checkStorerContext(storerContext)

    val storedMsiSearch = retrieveStoredMsiSearch(storerContext, msiSearchId)

    msQueries.foreach(loadOrCreateMsQuery(storerContext, _, storedMsiSearch))

    storerContext
  }

  /**
   * Retrieves a known ResultSet or an already persisted ResultSet or persists a new ResultSet entity into Msi Db.
   *
   * This create method '''flush''' Msi {{{EntityManager}}}.
   *
   * @param resultSet ResultSet object, must not be {{{null}}}.
   * @param storerContext Msi EntityManager must have a valid transaction started.
   */
  def createResultSet(resultSet: ResultSet, storerContext: StorerContext): Long = {

    require(resultSet != null, "ResultSet is null")

    checkStorerContext(storerContext)

    // TODO Decoy, Native and Quantified bools must be coherant with ORMResultSetProvider.buildResultSet()
    def parseType(resultSet: ResultSet): Type = {

      if (resultSet.isQuantified) {
        Type.QUANTITATION
      } else {

        if (resultSet.isSearchResult) {

          if (resultSet.isDecoy) {
            Type.DECOY_SEARCH
          } else {
            Type.SEARCH
          }

        } else {

          if (resultSet.isDecoy) {
            Type.DECOY_USER
          } else {
            Type.USER
          }

        }

      }

    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val omResultSetId = resultSet.id

    val knownResultSets = storerContext.getEntityCache(classOf[MsiResultSet])

    val knownMsiResultSet = knownResultSets.get(omResultSetId)

    if (knownMsiResultSet.isDefined) {
      knownMsiResultSet.get.getId
    } else {

      if (omResultSetId > 0L) {
        val foundMsiResultSet = msiEm.find(classOf[MsiResultSet], omResultSetId)

        require(foundMsiResultSet != null, s"ResultSet #$omResultSetId NOT found in MSIdb")

        knownResultSets += omResultSetId -> foundMsiResultSet

        foundMsiResultSet.getId
      } else {
        var msiResultSetPK: Long = -1L

        val msiResultSet = new MsiResultSet()
        msiResultSet.setDescription(resultSet.description)
        // ResultSet.modificationTimestamp field is initialized by MsiResultSet constructor
        msiResultSet.setName(resultSet.name)

        val resultSetProperties = resultSet.properties
        if (resultSetProperties.isDefined) {
          msiResultSet.setSerializedProperties(ProfiJson.serialize(resultSetProperties.get))
        }

        msiResultSet.setType(parseType(resultSet))

        /* Store MsiSearch and retrieve persisted ORM entity */
        var storedMsiSearch: MsiSearch = null

        val optionalMsiSearch = resultSet.msiSearch
        if ((optionalMsiSearch != null) && optionalMsiSearch.isDefined) { // ResultSet.msiSearch can be None for merged ResultSet
          val msiSearch = optionalMsiSearch.get
          val omMsiSearchId = msiSearch.id

          storeMsiSearch(msiSearch, storerContext)

          storedMsiSearch = retrieveStoredMsiSearch(storerContext, omMsiSearchId)

          /* Stored MSI Search OM Id is updated with PK by storeMsiSearch() */

          if (storedMsiSearch != null) {
            msiResultSet.setMsiSearch(storedMsiSearch)
          }

        }

        /* Check associated decoy ResultSet */
        val omDecoyResultSetId = resultSet.getDecoyResultSetId

        val msiDecoyRs = if (omDecoyResultSetId > 0L) {
          retrieveCreatedResultSet(storerContext, omDecoyResultSetId)
        } else {
          val optionalDecoyResultSet = resultSet.decoyResultSet

          if ((optionalDecoyResultSet != null) && optionalDecoyResultSet.isDefined) {
            /* Store Msi decoy ResultSet and retrieve persisted ORM entity */
            val decoyResutSet = optionalDecoyResultSet.get
            val omDecoyResultSetId = decoyResutSet.id

            createResultSet(decoyResutSet, storerContext)

            retrieveCreatedResultSet(storerContext, omDecoyResultSetId)
          } else {
            null
          }

        }

        msiResultSet.setDecoyResultSet(msiDecoyRs)
        if(resultSet.mergedResultSummaryId > 0L)
          msiResultSet.setMergedRsmId(resultSet.mergedResultSummaryId)
        
        msiEm.persist(msiResultSet)

        knownResultSets += omResultSetId -> msiResultSet

        logger.trace(s"ResultSet #$omResultSetId persisted in MSIdb")

        /* Peptides & PeptideMatches */
        val peptideByOldId = resultSet.getPeptideById()
        retrievePeptides(storerContext, msiResultSet, resultSet.peptides)

        for (peptMatch <- resultSet.peptideMatches) {
          createPeptideMatch(storerContext,
            peptMatch, msiResultSet, storedMsiSearch)
        }

        logger.info(resultSet.peptideMatches.length + " peptide matches have been stored")

        /* Fill proteinMatchSeqDatabases, proteinMatchOMORMMap and proteinMatchSequenceMatches Maps for postponed handling
         * (after flushing of Msi EntityManager) */
        val proteinMatchSeqDatabases = mutable.Map.empty[MsiProteinMatch, Array[Long]]
        val proteinMatchOMORMMap =  mutable.Map.empty[MsiProteinMatch, ProteinMatch]
        val proteinMatchSequenceMatches = mutable.Map.empty[MsiProteinMatch, Array[SequenceMatch]]

        /* Proteins (BioSequence) & ProteinMatches */
        for (protMatch <- resultSet.proteinMatches) {
          val msiProteinMatch = createProteinMatch(storerContext, protMatch, msiResultSet)
          proteinMatchOMORMMap += msiProteinMatch -> protMatch
          
          val seqDatabaseIds = protMatch.seqDatabaseIds
          if ((seqDatabaseIds != null) && seqDatabaseIds.nonEmpty) {
            proteinMatchSeqDatabases += msiProteinMatch -> seqDatabaseIds
          }

          val sequenceMatches = protMatch.sequenceMatches
          if ((sequenceMatches != null) && sequenceMatches.nonEmpty) {
            proteinMatchSequenceMatches += msiProteinMatch -> sequenceMatches
          }

        } // End loop for each proteinMatch

        logger.info(resultSet.proteinMatches.length + " protein matches have been stored")

        // TODO handle ResultSet.children    Uniquement pour le grouping ?

        msiEm.flush() // FLUSH to handle ProteinMatchSeqDatabaseMap and proteinMatchSequenceMatches and retrieve Msi ResultSet Id

        val generatedPK = msiResultSet.getId // Try to retrieve persisted Primary Key (after FLUSH)

        if (generatedPK > 0L) {
          msiResultSetPK = generatedPK

          resultSet.id = msiResultSetPK

          knownResultSets += msiResultSetPK -> msiResultSet // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of ResultSet #" + omResultSetId)
        }

        if (proteinMatchSeqDatabases.nonEmpty) {
          logger.trace("Handling proteinMatchSeqDatabases after flushing of MSI EntityManager")

          /* Handle proteinMatchSeqDatabaseMap after having persisted MsiProteinMatches, SeqDatabases and current MsiResultSet */
          for (pMSDEntry <- proteinMatchSeqDatabases) {
            val msiSeqDbIds = new ArrayBuffer[Long]
            for( seqDatabaseId <- pMSDEntry._2){
               val msiSD = bindProteinMatchSeqDatabaseMap(storerContext, pMSDEntry._1.getId, seqDatabaseId, msiResultSet)
               if(msiSD != null)
                 msiSeqDbIds += msiSD.getId
            }
            proteinMatchOMORMMap.get(pMSDEntry._1).get.seqDatabaseIds = msiSeqDbIds.toArray
          }

        } // End if (proteinMatchSeqDatabases is not empty)

        if (proteinMatchSequenceMatches.nonEmpty) {
          logger.trace("Handling proteinMatchSequenceMatches and ProteinMatch.peptideCount after flushing of Msi EntityManager")

          /* Handle proteinMatchSequenceMatches after having persisted MsiProteinMatches, MsiPeptideMatches and current MsiResultSet */
          var sequenceMatchCount = 0

          for (pMSMEntry <- proteinMatchSequenceMatches) {
            val msiProteinMatch = pMSMEntry._1
            val msiProteinMatchId = msiProteinMatch.getId

            val peptideIds = mutable.Set.empty[Long]

            for (sequenceMatch <- pMSMEntry._2) {

              // Link peptide to sequenceMatch
              val peptideOpt = peptideByOldId.get(sequenceMatch.getPeptideId())
              if( peptideOpt.isDefined ) {
                sequenceMatch.peptide = peptideOpt
              }

              val msiSequenceMatch = createSequenceMatch(storerContext, sequenceMatch, msiProteinMatchId, msiResultSetPK)
              sequenceMatchCount += 1
              peptideIds += msiSequenceMatch.getId.getPeptideId
            } // End loop for each sequenceMatch

            /* Update ProteinMatch.peptideCount after having persisted sequenceMatches for current MsiProteinMatch  */
            msiProteinMatch.setPeptideCount(peptideIds.size)
          } // End loop for each Msi ProteinMatch

          logger.info(sequenceMatchCount + " SequenceMatches have been stored")

        } // End if (proteinMatchSequenceMatches is not empty)

        msiResultSetPK
      } // End if (omResultSetId <= 0)

    } // End if (msiResultSet is not in knownResultSets)

  }

  /**
   * @param resultSetId Id of ResultSet, can accept "In memory" OM Id or Msi ResultSet Primary key.
   */
  private def retrieveCreatedResultSet(storerContext: StorerContext, resultSetId: Long): MsiResultSet = {
    val knownResultSets = storerContext.getEntityCache(classOf[MsiResultSet])

    val knownMsiResultSet = knownResultSets.get(resultSetId)

    if (knownMsiResultSet.isDefined) {
      knownMsiResultSet.get
    } else {
      val msiResultSet = storerContext.getMSIDbConnectionContext.getEntityManager.find(classOf[MsiResultSet], resultSetId)

      require(msiResultSet != null, s"ResultSet #$msiResultSet NOT found in MSIdb")

      knownResultSets += resultSetId -> msiResultSet

      msiResultSet
    }

  }

  /**
   * Persists a MSISearch object into Msi Db. (already persisted MSISearches are cached into {{{storerContext}}} object
   * and can be retrieved via retrieveStoredMsiSearch() method).
   *
   * StoreXXX() methods '''flush''' Msi {{{EntityManager}}}.
   *
   * @param search MSISearch object, must not be {{{null}}}
   *
   */
  def storeMsiSearch(search: MSISearch, storerContext: StorerContext): Long = {

    require(search != null, "MsiSearch is mandatory")

    checkStorerContext(storerContext)

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val omMsiSearchId = search.id

    val knownMsiSearchs = storerContext.getEntityCache(classOf[MsiSearch])

    val knownMsiSearch = knownMsiSearchs.get(omMsiSearchId)

    if (knownMsiSearch.isDefined) {
      knownMsiSearch.get.getId
    } else {

      if (omMsiSearchId > 0L) {
        val foundMsiSearch = msiEm.find(classOf[MsiSearch], omMsiSearchId)

        require(foundMsiSearch != null, s"MsiSearch #$omMsiSearchId NOT found in MSIdb")

        knownMsiSearchs += omMsiSearchId -> foundMsiSearch

        foundMsiSearch.getId
      } else {
        var msiSearchPK: Long = -1L

        val msiSearch = new MsiSearch()
        msiSearch.setDate(new Timestamp(search.date.getTime))
        msiSearch.setQueriesCount(Integer.valueOf(search.queriesCount))
        msiSearch.setResultFileName(search.resultFileName)
        msiSearch.setResultFileDirectory(search.resultFileDirectory)
        msiSearch.setSearchedSequencesCount(Integer.valueOf(search.searchedSequencesCount))
        msiSearch.setJobNumber(Integer.valueOf(search.jobNumber))

        // TODO handle serializedProperties

        msiSearch.setTitle(search.title)
        msiSearch.setUserEmail(search.userEmail)
        msiSearch.setUserName(search.userName)

        /* Store Msi Peaklist and retrieve persisted ORM entity */
        val peaklist = search.peakList
        val omPeaklistId = peaklist.id

        if (omPeaklistId <= 0L) {
          this.getOrBuildPeaklistWriter(storerContext).insertPeaklist(peaklist, storerContext)
        }

        val storedPeaklist = retrieveStoredPeaklist(storerContext, omPeaklistId)

        msiSearch.setPeaklist(storedPeaklist)

        msiSearch.setSearchSetting(loadOrCreateSearchSetting(storerContext, search))

        msiEm.persist(msiSearch)

        msiEm.flush() // FLUSH to retrieve Msi MsiSearch Id

        knownMsiSearchs += omMsiSearchId -> msiSearch

        logger.trace(s"MsiSearch #$omMsiSearchId persisted")

        val generatedPK = msiSearch.getId // Try to retrieve persisted Primary Key (after FLUSH)

        if (generatedPK > 0L) {
          msiSearchPK = generatedPK

          search.id = msiSearchPK

          knownMsiSearchs += msiSearchPK -> msiSearch // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of MSI Search #" + omMsiSearchId)
        }

        msiSearchPK
      }

    } // End if (msiSearch is not in knownMsiSearchs)

  }

  /**
   * @param msiSearchId Id of MsiSearch, can accept "In memory" OM Id or Msi MsiSearch Primary key.
   */
  private def retrieveStoredMsiSearch(storerContext: StorerContext, msiSearchId: Long): MsiSearch = {
    val knownMsiSearches = storerContext.getEntityCache(classOf[MsiSearch])

    val knownMsiSearch = knownMsiSearches.get(msiSearchId)

    if (knownMsiSearch.isDefined) {
      knownMsiSearch.get
    } else {
      val msiSearch = storerContext.getMSIDbConnectionContext.getEntityManager.find(classOf[MsiSearch], msiSearchId)

      require(msiSearch != null, "MsiSearch #$msiSearchId NOT found in MSIdb" )

      knownMsiSearches += msiSearchId -> msiSearch

      msiSearch
    }

  }

  /*
   * Persists a Peaklist object into Msi Db. (already persisted Peaklist are cached into {{{storerContext}}} object
   * and can be retrieved via retrieveStoredPeaklist() method).
   *
   * StoreXXX() methods '''flush''' Msi {{{EntityManager}}}.
   *
   * @param peakList Peaklist object, must not be {{{null}}}
   *
   */
  //  def storePeaklist(peakList: Peaklist, storerContext: StorerContext): Int = {
  //
  //    if (peakList == null) {
  //      throw new IllegalArgumentException("PeakList is null")
  //    }
  //    checkStorerContext(storerContext)

  //    val omPeakListId = peakList.id
  //
  //    val knownPeaklists = storerContext.getEntityCache(classOf[MsiPeaklist])
  //
  //    val knownMsiPeakList = knownPeaklists.get(omPeakListId)
  //
  //    if (knownMsiPeakList.isDefined) {
  //      knownMsiPeakList.get.getId
  //    } else {
  //
  //      if (omPeakListId > 0) {
  //        val foundMsiPeakList = storerContext.msiEm.find(classOf[MsiPeaklist], omPeakListId)
  //
  //      if (foundMsiPeaklist == null) {
  //        throw new IllegalArgumentException("Peaklist #" + omPeaklistId + " NOT found in Msi Db")
  //        }

  //        knownPeaklists += omPeakListId -> foundMsiPeakList
  //
  //        foundMsiPeakList.getId
  //      } else {
  //        val msiPeakList = new MsiPeaklist()
  //        msiPeakList.setMsLevel(Integer.valueOf(peakList.msLevel))
  //        msiPeakList.setPath(peakList.path)
  //        msiPeakList.setRawFileName(peakList.rawFileName)
  //
  //        // TODO handle serializedProperties
  //
  //        // TODO Set meaningful value in PeakList.spectrumDataCompression field
  //        msiPeakList.setSpectrumDataCompression("none")
  //        msiPeakList.setType(peakList.fileType)
  //
  //        val peaklistSoftware = peakList.peaklistSoftware
  //        if (peaklistSoftware != null) {
  //          msiPeakList.setPeaklistSoftware(loadOrCreatePeaklistSoftware(storerContext, peaklistSoftware))
  //        } else{
  //         throw new IllegalArgumentException("peaklistSoftware can't be null !")
  //        }
  //
  //        // TODO handle PeakList.children    Uniquement pour le grouping ?
  //        storerContext.msiEm.persist(msiPeakList)
  //        storerContext.msiEm.flush() // FLUSH to retrieve Msi Peaklist Id
  //
  //        knownPeaklists += omPeakListId -> msiPeakList
  //
  //        logger.debug("Msi PeakList {" + omPeakListId + "} persisted")
  //
  //        msiPeakList.getId
  //      } // End if (omPeakListId <= 0)
  //
  //    } // End if (msiPeakList is not in knownPeakLists)
  //
  //  }

  /**
   * @param peaklistId Id of PeakList, can accept "In memory" OM Id or Msi PeakList Primary key.
   */
  private def retrieveStoredPeaklist(storerContext: StorerContext, peaklistId: Long): MsiPeaklist = {
    val knownPeaklists = storerContext.getEntityCache(classOf[MsiPeaklist])

    val knownPeaklist = knownPeaklists.get(peaklistId)

    if (knownPeaklist.isDefined) {
      knownPeaklist.get
    } else {
      val msiPeaklist = storerContext.getMSIDbConnectionContext.getEntityManager.find(classOf[MsiPeaklist], peaklistId)

      require(msiPeaklist != null, s"Peaklist #$peaklistId  NOT found in MSIdb")

      knownPeaklists += peaklistId -> msiPeaklist

      msiPeaklist
    }

  }

  /**
   * Retrieves an already persisted PeaklistSoftware or persists a new PeaklistSoftware entity into Msi Db from an existing Uds Db entity.
   *
   * @param peaklistSoftware PeaklistSoftware object, must not be {{{null}}}.
   */
  def loadOrCreatePeaklistSoftware(
    storerContext: StorerContext,
    peaklistSoftware: PeaklistSoftware
  ): MsiPeaklistSoftware = {

    checkStorerContext(storerContext)

    require(peaklistSoftware != null, "PeaklistSoftware is null" )

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val omPeakListSoftwareId = peaklistSoftware.id

    var msiPeaklistSoftware: MsiPeaklistSoftware = null

    if (omPeakListSoftwareId > 0L) {
      msiPeaklistSoftware = msiEm.find(classOf[MsiPeaklistSoftware], omPeakListSoftwareId)
    }

    if (msiPeaklistSoftware == null) {
      msiPeaklistSoftware = msiPeaklistSoftwareRepo.findPeaklistSoftForNameAndVersion(msiEm, peaklistSoftware.name, peaklistSoftware.version)

      if (msiPeaklistSoftware != null) {
        peaklistSoftware.id = msiPeaklistSoftware.getId // Update OM entity with persisted Primary key
      }

    }

    if (msiPeaklistSoftware == null) {
      val udsPeaklistSoftware = udsPeaklistSoftwareRepo.findPeaklistSoftForNameAndVersion(storerContext.getUDSDbConnectionContext.getEntityManager, peaklistSoftware.name, peaklistSoftware.version)

      require(
        udsPeaklistSoftware != null,
        s"PeaklistSoftware [${peaklistSoftware.name}] [${peaklistSoftware.version}] NOT found in Uds Db"
      )

      msiPeaklistSoftware = new MsiPeaklistSoftware(udsPeaklistSoftware)

      msiEm.persist(msiPeaklistSoftware)
      logger.trace(s"Msi PeaklistSoftware #${ udsPeaklistSoftware.getId} persisted")

      peaklistSoftware.id = udsPeaklistSoftware.getId // Update OM entity with persisted Primary key
    }

    msiPeaklistSoftware
  }

  /**
   * Retrieves an already persisted SearchSetting or persists a new SearchSetting entity into Msi Db.
   *
   * @param search Associated MSISearch object, must not be {{{null}}} and must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  def loadOrCreateSearchSetting(
    storerContext: StorerContext,
    search: MSISearch
  ): MsiSearchSetting = {

    checkStorerContext(storerContext)

    require(search != null,"Search is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val udsEm = storerContext.getUDSDbConnectionContext.getEntityManager

    val searchSettings = search.searchSettings
    val omSearchSettingsId = searchSettings.id

    if (omSearchSettingsId > 0L) {
      // Must exist in Msi Db if OM Id > 0
      if(searchSettings.msmsSearchSettings.isDefined)
         return msiEm.getReference(classOf[MsiMSMSSearchSetting], omSearchSettingsId)
       else
         return msiEm.getReference(classOf[MsiSearchSetting], omSearchSettingsId) 
    } else {
      
      var msiSearchSetting = if(searchSettings.msmsSearchSettings.isDefined){ 
          val msmsS = new  MsiMSMSSearchSetting()
          msmsS.setFragmentChargeStates(searchSettings.msmsSearchSettings.get.ms2ChargeStates);
          msmsS.setFragmentMassErrorTolerance(searchSettings.msmsSearchSettings.get.ms2ErrorTol);
          msmsS.setFragmentMassErrorToleranceUnit(searchSettings.msmsSearchSettings.get.ms2ErrorTolUnit);
          msmsS          
        } else { 
          new MsiSearchSetting()
        }
      
      
      msiSearchSetting.setIsDecoy(searchSettings.isDecoy)
      msiSearchSetting.setMaxMissedCleavages(Integer.valueOf(searchSettings.maxMissedCleavages))
      msiSearchSetting.setPeptideChargeStates(searchSettings.ms1ChargeStates)
      msiSearchSetting.setPeptideMassErrorTolerance(java.lang.Double.valueOf(searchSettings.ms1ErrorTol))
      msiSearchSetting.setPeptideMassErrorToleranceUnit(searchSettings.ms1ErrorTolUnit)

      val searchSettingProperties = searchSettings.properties
      if (searchSettingProperties.isDefined) {
        msiSearchSetting.setSerializedProperties(ProfiJson.serialize(searchSettingProperties.get))
      }

      msiSearchSetting.setSoftwareName(searchSettings.softwareName)
      msiSearchSetting.setSoftwareVersion(searchSettings.softwareVersion)
      msiSearchSetting.setTaxonomy(searchSettings.taxonomy)

      msiSearchSetting.setInstrumentConfig(loadOrCreateInstrumentConfig(storerContext, searchSettings.instrumentConfig))

      for (enzyme <- searchSettings.usedEnzymes) {
        msiSearchSetting.addEnzyme(loadOrCreateEnzyme(storerContext, enzyme.name))
      }

      msiEm.persist(msiSearchSetting)
      logger.trace(s"Msi SearchSetting #$omSearchSettingsId persisted")

      val generatedPK = msiSearchSetting.getId // Try to retrieve persisted Primary Key

      if (generatedPK > 0L) {
        searchSettings.id = generatedPK
      } else {
        logger.warn("Cannot retrieve Primary Key of SearchSettings #" + omSearchSettingsId)
      }

      /* Task done after persisting msiSearchSetting */
      for (seqDatabase <- searchSettings.seqDatabases) {
        val msiSearchSettingsSeqDatabaseMap = new MsiSearchSettingsSeqDatabaseMap()
        msiSearchSettingsSeqDatabaseMap.setSearchedSequencesCount(search.searchedSequencesCount)

        // TODO handle serializedProperties

        msiSearchSettingsSeqDatabaseMap.setSearchSetting(msiSearchSetting) // msiSearchSetting must be in persistence context
        msiSearchSetting.addSearchSettingsSeqDatabaseMap(msiSearchSettingsSeqDatabaseMap) // Reverse association

        val msiSeqDatabase = loadOrCreateSeqDatabase(storerContext, seqDatabase)
        if (msiSeqDatabase != null) {
          msiSearchSettingsSeqDatabaseMap.setSeqDatabase(msiSeqDatabase) // msiSeqDatabase must be in persistence context
          msiSeqDatabase.addSearchSettingsSeqDatabaseMap(msiSearchSettingsSeqDatabaseMap) // Reverse association

          msiEm.persist(msiSearchSettingsSeqDatabaseMap)
          logger.trace(s"Msi SettingsSeqDatabaseMap SearchSetting #$omSearchSettingsId SeqDatabase #${msiSeqDatabase.getId} persisted")
        }

      }

      /* MsiSearchSetting must be in persistence context before calling bindUsedPtm() methods */

      for (variablePtmDef <- searchSettings.variablePtmDefs) {
        bindUsedPtm(storerContext, variablePtmDef, false, msiSearchSetting)
      }

      for (fixedPtmDef <- searchSettings.fixedPtmDefs) {
        bindUsedPtm(storerContext, fixedPtmDef, true, msiSearchSetting)
      }

      msiSearchSetting
    } // End if (omSearchSettingsId <= 0)

  }

  /**
   * Retrieves an already persisted InstrumentConfig or persists a new InstrumentConfig entity into Msi Db from an existing Uds Db entity.
   *
   * @param instrumentConfig InstrumentConfig object, must not be {{{null}}}.
   */
  def loadOrCreateInstrumentConfig(
    storerContext: StorerContext,
    instrumentConfig: InstrumentConfig
  ): MsiInstrumentConfig = {

    checkStorerContext(storerContext)

    require(instrumentConfig != null, "InstrumentConfig is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    var msiInstrumentConfig: MsiInstrumentConfig = null

    if (instrumentConfig.id > 0L) {
      msiInstrumentConfig = msiEm.find(classOf[MsiInstrumentConfig], instrumentConfig.id)
    }

    if (msiInstrumentConfig == null) {
      msiInstrumentConfig = msiInstrumentConfigRepo.findInstrumConfForNameAndMs1AndMsn(msiEm, instrumentConfig.name,
        instrumentConfig.ms1Analyzer, instrumentConfig.msnAnalyzer)
    }

    if (msiInstrumentConfig == null) {
      val udsInstrumentConfiguration = udsInstrumentConfigRepo.findInstrumConfForNameAndMs1AndMsn(storerContext.getUDSDbConnectionContext.getEntityManager, instrumentConfig.name,
        instrumentConfig.ms1Analyzer, instrumentConfig.msnAnalyzer)

      require(
        udsInstrumentConfiguration != null,
        s"InstrumentConfiguration [${instrumentConfig.name}] [${instrumentConfig.ms1Analyzer}] NOT found in Uds Db"
      )

      msiInstrumentConfig = new MsiInstrumentConfig(udsInstrumentConfiguration)

      msiEm.persist(msiInstrumentConfig)
      logger.trace(s"Msi InstrumentConfig #${udsInstrumentConfiguration.getId} persisted")
    }

    msiInstrumentConfig
  }

  /**
   * Retrieves an already persisted Enzyme or persists a new Enzyme entity into Msi Db from an existing Uds Db entity.
   *
   * @param enzymeName Name of the Enzyme (ignoring case), must not be empty.
   */
  def loadOrCreateEnzyme(
    storerContext: StorerContext,
    enzymeName: String
  ): MsiEnzyme = {

    checkStorerContext(storerContext)

    require(StringUtils.isNotEmpty(enzymeName),"Invalid enzymeName")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    var msiEnzyme: MsiEnzyme = msiEnzymeRepo.findEnzymeForName(msiEm, enzymeName)

    if (msiEnzyme == null) {
      val udsEnzyme = udsEnzymeRepo.findEnzymeForName(storerContext.getUDSDbConnectionContext.getEntityManager, enzymeName)

      require(udsEnzyme != null, s"Enzyme [$enzymeName] NOT found in Uds Db")

      msiEnzyme = new MsiEnzyme(udsEnzyme)

      msiEm.persist(msiEnzyme)
      logger.trace(s"Msi Enzyme #${udsEnzyme.getId} persisted")
    }

    msiEnzyme
  }

  /**
   * Retrieves a known SeqDatabase or an already persisted SeqDatabase or persists a new SeqDatabase entity into Msi Db from an existing Pdi Db entity
   * or directly from OM SeqDatabase.
   *
   * @param seqDatabase SeqDatabase object, must not be {{{null}}}.
   * @return Msi SeqDatabase entity or {{{null}}} if SeqDatabase does not exist in Pdi Db.
   */
  def loadOrCreateSeqDatabase(storerContext: StorerContext,
                              seqDatabase: SeqDatabase): MsiSeqDatabase = {

    checkStorerContext(storerContext)

    require(seqDatabase != null, "SeqDatabase is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager
    val omSeqDatabaseId = seqDatabase.id
    val knownSeqDatabases = storerContext.getEntityCache(classOf[MsiSeqDatabase])
    val knownMsiSeqDatabase = knownSeqDatabases.get(omSeqDatabaseId)

    if (knownMsiSeqDatabase.isDefined) {
      knownMsiSeqDatabase.get // Return null if omSeqDatabaseId exists and MsiSeqDatabase value is null
    } else {
      var msiSeqDatabase: MsiSeqDatabase = null

      if (omSeqDatabaseId > 0L) {
        /* Try to load from Msi Db by Id */
        msiSeqDatabase = msiEm.find(classOf[MsiSeqDatabase], omSeqDatabaseId)
      }

      if (msiSeqDatabase == null) {
        
        /* Try to load from Msi Db by name and Fasta file path */
        msiSeqDatabase = msiSeqDatabaseRepo.findSeqDatabaseForNameAndFastaAndVersion(msiEm, seqDatabase.name, seqDatabase.filePath)

        if (msiSeqDatabase == null) {
          logger.warn(s"SeqDatabase '${seqDatabase.name}' '{seqDatabase.filePath}' NOT found in PDIdb, create one from OM")

          /* Create a SeqDatabase into MSI directly from OM SeqDatabase  */
          msiSeqDatabase = new MsiSeqDatabase()
          msiSeqDatabase.setFastaFilePath(seqDatabase.filePath)
          msiSeqDatabase.setName(seqDatabase.name)

          if (seqDatabase.releaseDate != null) {
            msiSeqDatabase.setReleaseDate(new Timestamp(seqDatabase.releaseDate.getTime))
          }

          msiSeqDatabase.setSequenceCount(Integer.valueOf(seqDatabase.sequencesCount))

          // TODO handle  serializedProperties

          msiSeqDatabase.setVersion(seqDatabase.version)

          msiEm.persist(msiSeqDatabase)
          logger.trace(s"Msi SeqDatabase #$omSeqDatabaseId persisted")
        } // End if (msiSeqDatabase is null)

      } // End if (msiSeqDatabase is null)

      knownSeqDatabases += omSeqDatabaseId -> msiSeqDatabase

      val generatedPK = msiSeqDatabase.getId // Try to retrieve persisted Primary Key

      if (generatedPK > 0L) {
        val msiSeqDatabasePK = generatedPK

        seqDatabase.id = msiSeqDatabasePK

        knownSeqDatabases += msiSeqDatabasePK -> msiSeqDatabase // Cache with MSI Primary Key
      } else {
        logger.warn("Cannot retrieve Primary Key of SeqDatabase #" + omSeqDatabaseId)
      }

      msiSeqDatabase
    } // End if (msiSeqDatabase not in knownSeqDatabases)

  }

  /**
   * Retrieves a known PtmSpecificity or an already persisted PtmSpecificity.
   *
   * @param ptmDefinition PtmDefinition object, must not be {{{null}}}.
   */
  def loadPtmSpecificity(
    storerContext: StorerContext,
    ptmDefinition: PtmDefinition
  ): MsiPtmSpecificity = {

    checkStorerContext(storerContext)

    require(ptmDefinition != null, "PtmDefinition is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val knownPtmSpecificities = storerContext.getEntityCache(classOf[MsiPtmSpecificity])

    val knownMsiPtmSpecificity = knownPtmSpecificities.get(ptmDefinition.id)

    if (knownMsiPtmSpecificity.isDefined) {
      knownMsiPtmSpecificity.get
    } else {

      val msiPtmSpecificity = if (ptmDefinition.id <= 0L) null
      else {
        /* Try to load from Msi Db by Id */
        msiEm.find(classOf[MsiPtmSpecificity], ptmDefinition.id)
      }

      if (msiPtmSpecificity != null) {
        knownPtmSpecificities += ptmDefinition.id -> msiPtmSpecificity
      } else {
        logger.warn(s"PtmSpecificity '${ptmDefinition.names.shortName}' NOT found in MSIdb")
      }

      msiPtmSpecificity
    } // End if (msiPtmSpecificity not found in knownPtmSpecificities)

  }

  /**
   * Retrieves Peptides from Msi Db.
   *
   * @param storerContext Storer ExecutionContext containing EntityManagers for MSI entity caches.
   * @param msiResultSet MSI persisted entity.
   * @param peptides Array of Peptide objects to fetch, must not be {{{null}}}.
   */
  def retrievePeptides(
    storerContext: StorerContext,
    msiResultSet: MsiResultSet,
    peptides: Array[Peptide]
  ) {

    checkStorerContext(storerContext)

    require(msiResultSet != null, "MsiResultSet is null")

    require(peptides != null, "Peptides array is null")

    /**
     * Build a Java List<Long> from a Scala Collection[Long].
     */
    // TODO: use built-in JavaConversions from Scala library
    def buildIdsList(omIds: Traversable[Long]): java.util.List[java.lang.Long] = {
      val javaIds = new java.util.ArrayList[java.lang.Long](omIds.size)

      for (omId <- omIds) {
        javaIds.add(java.lang.Long.valueOf(omId))
      }

      javaIds
    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val msiPeptides = storerContext.msiPeptides

    /* These are mutable Collections : found and created Peptides are removed by the algo */
    val remainingPeptides = mutable.Map.empty[PeptideIdent, Peptide]
    val persistedPeptidesIdSet = mutable.Set.empty[Long] // Keep OM Peptides whose Id > 0

    for (peptide <- peptides) {
      val peptIdent = new PeptideIdent(peptide.sequence, peptide.ptmString)

      if (!msiPeptides.contains(peptIdent)) {
        remainingPeptides += peptIdent -> peptide

        val peptideId = peptide.id

        if (peptideId > 0L) {
          persistedPeptidesIdSet += peptideId
        }
      }
    }

    /* Retrieve all known Peptides from Msi Db by OM Ids > 0 */
    if (persistedPeptidesIdSet.nonEmpty) {
      logger.debug(s"Trying to retrieve ${persistedPeptidesIdSet.size} Peptides from MSIdb by Ids")

      val foundMsiPeptides = msiPeptideRepo.findPeptidesForIds(msiEm, buildIdsList(persistedPeptidesIdSet)).asScala

      if ((foundMsiPeptides != null) && foundMsiPeptides.nonEmpty) {

        for (msiPeptide <- foundMsiPeptides) {
          val peptideId = msiPeptide.getId
          val peptIdent = new PeptideIdent(msiPeptide.getSequence, msiPeptide.getPtmString)

          // Update msiPeptides Map in StorerContext
          msiPeptides += peptIdent -> msiPeptide

          remainingPeptides.remove(peptIdent)
          persistedPeptidesIdSet.remove(peptideId)
        }
      }
    }

    require(persistedPeptidesIdSet.isEmpty, s"Peptides having ids [${persistedPeptidesIdSet.mkString(", ")}] NOT found in MSIdb")

    if (remainingPeptides.nonEmpty) {

      /* Create new Peptides into Msi Db */
      val createdMsiPeptides = persistMsiPeptides(storerContext, remainingPeptides)

      for ( (peptIdent, msiPeptide) <- createdMsiPeptides) {

        // Update msiPeptides Map in StorerContext
        msiPeptides.put( peptIdent, msiPeptide )

        remainingPeptides.remove(peptIdent)

      } // End loop for each createdMsiPeptides

    } // End if (remainingPeptides is not empty)

    logger.info(msiPeptides.size + " new Peptides stored in the MSIdb")

    if (remainingPeptides.nonEmpty) {
      logger.error(s"There are ${remainingPeptides.size} unknown Peptides in ResultSet")
    }

    /* Create PeptideReadablePtmString entities */
    val readablePtmStringByPepId: Map[Long, PeptideReadablePtmString] = storerContext.getEntityCache(classOf[PeptideReadablePtmString]).toMap
    for (peptide <- peptides) {
      val pepReadablePtm =  if(readablePtmStringByPepId.contains(peptide.id)) readablePtmStringByPepId(peptide.id).getReadablePtmString else  peptide.readablePtmString

      if (StringUtils.isNotEmpty(pepReadablePtm)) {
        val peptIdent = new PeptideIdent(peptide.sequence, peptide.ptmString)

        val optionalMsiPeptide = msiPeptides.get(peptIdent)

        if (optionalMsiPeptide.isEmpty) {
          logger.warn(s"Unable to retrieve Peptide $peptIdent from cache")
        } else {
          val msiPeptide = optionalMsiPeptide.get
          val readablePtmStringEntityPK = new PeptideReadablePtmStringPK()
          readablePtmStringEntityPK.setPeptideId(msiPeptide.getId)
          readablePtmStringEntityPK.setResultSetId(msiResultSet.getId)
          
          val readablePtmStringEntity = new PeptideReadablePtmString()
          readablePtmStringEntity.setId(readablePtmStringEntityPK)
          readablePtmStringEntity.setPeptide(msiPeptide) // MSI Peptide must be in persistence context
          readablePtmStringEntity.setResultSet(msiResultSet) // MSI ResultSet must be in persistence context
          readablePtmStringEntity.setReadablePtmString(pepReadablePtm)

          msiEm.persist(readablePtmStringEntity)
          logger.trace(s"PeptideReadablePtmString '${peptide.readablePtmString}' persisted")
        }
      }
    }

  }

  /**
   * Persists new Peptide entities into Msi Db.
   *
   * @param storerContext A transaction will be started on StorerContext to persist new Peptides in Msi Db.
   * @param peptides Map of Peptide objects to create, accessed by PeptideIdent(sequence, ptmString). Must not be {{{null}}}.
   * @return Map of created Msi Peptide entities accessed by PeptideIdent.
   */
  def persistMsiPeptides(storerContext: StorerContext, peptides: mutable.Map[PeptideIdent, Peptide]): Map[PeptideIdent, MsiPeptide] = {

    checkStorerContext(storerContext)

    require(peptides != null, "Peptides map is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    logger.debug(s"Storing ${peptides.size} new Peptides into MSIdb")

    val createdMsiPeptides = Map.newBuilder[PeptideIdent, MsiPeptide]

    for ( (peptIdent,peptide) <- peptides) {

      val newMsiPeptide = new MsiPeptide()
      newMsiPeptide.setSequence(peptIdent.sequence)
      newMsiPeptide.setPtmString(peptIdent.ptmString)
      newMsiPeptide.setCalculatedMass(peptide.calculatedMass)

      // TODO: handle serializedProperties
      // TODO: handle atomLabel

      msiEm.persist(newMsiPeptide)

      peptide.id = newMsiPeptide.getId // Update OM entity with persisted Primary key

      /*  MsiPeptide must be in persistence context before calling bindPeptidePtm() method */
      if ((peptide.ptms != null) && peptide.ptms.nonEmpty) {
        for (locatedPtm <- peptide.ptms) {
          bindPeptidePtm(storerContext, newMsiPeptide, locatedPtm)
        }
      }

      createdMsiPeptides += peptIdent -> newMsiPeptide

      logger.trace(s"Msi Peptide #${newMsiPeptide.getId} persisted")

    } // End loop for each Peptides

    createdMsiPeptides.result

  }

  /**
   * Retrieves a known PeptideMatch or persists a new PeptideMatch entity into Msi Db.
   *
   * @param peptideMatch PeptideMatch object, must not be {{{null}}}.
   * @param msiResultSet Associated Msi ResultSet entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   * @param msiSearch Associated MsiSearch entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  def createPeptideMatch(
    storerContext: StorerContext,
    peptideMatch: PeptideMatch,
    msiResultSet: MsiResultSet,
    msiSearch: MsiSearch
  ): MsiPeptideMatch = {

    checkStorerContext(storerContext)

    require(peptideMatch != null, "PeptideMatch is null")
    require(msiResultSet != null, "MsiResultSet is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val omPeptideMatchId = peptideMatch.id

    val knownPeptideMatches = storerContext.getEntityCache(classOf[MsiPeptideMatch])

    val knownMsiPeptideMatch = knownPeptideMatches.get(omPeptideMatchId)

    if (knownMsiPeptideMatch.isDefined) {
      knownMsiPeptideMatch.get
    } else {

      if (omPeptideMatchId > 0L) {
        throw new UnsupportedOperationException("Updating existing PeptideMatch #" + omPeptideMatchId + " is not supported")
      } else {
        val msiPeptideMatch = new MsiPeptideMatch()
        msiPeptideMatch.setDeltaMoz(java.lang.Float.valueOf(peptideMatch.deltaMoz))
        msiPeptideMatch.setFragmentMatchCount(Integer.valueOf(peptideMatch.fragmentMatchesCount))
        msiPeptideMatch.setIsDecoy(peptideMatch.isDecoy)
        msiPeptideMatch.setMissedCleavage(peptideMatch.missedCleavage)

        val msiPeptide = storerContext.msiPeptides.get(new PeptideIdent(peptideMatch.peptide.sequence, peptideMatch.peptide.ptmString))

        val msiPeptideId: Long =
          if (msiPeptide.isDefined) {
            msiPeptide.get.getId
          } else {
            -1L
          }

        require (msiPeptideId > 0L, "Unknown MSIdb Peptide Id: " + msiPeptideId)

        msiPeptideMatch.setPeptideId(msiPeptideId)

        msiPeptideMatch.setRank(Integer.valueOf(peptideMatch.rank))
        
        msiPeptideMatch.setSDPrettyRank(Integer.valueOf(peptideMatch.sdPrettyRank))
        
        msiPeptideMatch.setCDPrettyRank(Integer.valueOf(peptideMatch.cdPrettyRank))

        msiPeptideMatch.setResultSet(msiResultSet) // msiResultSet must be in persistence context

        msiPeptideMatch.setScore(java.lang.Float.valueOf(peptideMatch.score))

        val msiScoringId = msiScoringRepo.getScoringIdForType(msiEm, peptideMatch.scoreType.toString)

        require(msiScoringId != null, s"Scoring [${peptideMatch.scoreType}] NOT found in MSIdb")

        msiPeptideMatch.setScoringId(msiScoringId.longValue)

        val peptideMatchProperties = peptideMatch.properties
        if (peptideMatchProperties.isDefined) {
          msiPeptideMatch.setSerializedProperties(ProfiJson.serialize(peptideMatchProperties.get))
        }

        require(peptideMatch.msQuery != null, "MsQuery is mandatory in PeptideMatch")

        val msiMsQuery = loadOrCreateMsQuery(storerContext, peptideMatch.msQuery, msiSearch) // msiSearch must be in persistence context

        msiPeptideMatch.setMsQuery(msiMsQuery) // msiMsQuery must be in persistence context
        msiMsQuery.addPeptideMatch(msiPeptideMatch) // Reverse association

        var pmCharge = msiMsQuery.getCharge
        if(peptideMatch.properties.isDefined && peptideMatch.properties.get.getOmssaProperties.isDefined) {
          pmCharge = peptideMatch.properties.get.getOmssaProperties.get.getCorrectedCharge
        }
        msiPeptideMatch.setCharge(pmCharge)
        msiPeptideMatch.setExperimentalMoz(msiMsQuery.getMoz)

        /* Check associated best PeptideMatch */
        val bestOmPeptideMatchId = peptideMatch.bestChildId

        val knownMsiBestChild = knownPeptideMatches.get(bestOmPeptideMatchId)

        val msiBestChild = if (knownMsiBestChild.isDefined) {
          knownMsiBestChild.get
        } else {

          if (bestOmPeptideMatchId > 0L) {
            val foundBestChild = msiEm.getReference(classOf[MsiPeptideMatch], bestOmPeptideMatchId) // Must exist in Msi Db if OM Id > 0

            knownPeptideMatches += bestOmPeptideMatchId -> foundBestChild

            foundBestChild
          } else {
            val bestChild = peptideMatch.getBestChild()

            if (bestChild.isDefined) {
              createPeptideMatch(storerContext,
                bestChild.get, msiResultSet, msiSearch)
            } else {
              null
            }

          }

        }

        msiPeptideMatch.setBestPeptideMatch(msiBestChild)

        // TODO handle PeptideMatch.children    Uniquement pour le grouping ?

        msiEm.persist(msiPeptideMatch)

        knownPeptideMatches += omPeptideMatchId -> msiPeptideMatch

        logger.trace(s"Msi PeptideMatch #$omPeptideMatchId persisted")

        val generatedPK = msiPeptideMatch.getId // Try to retrieve persisted Primary Key

        if (generatedPK > 0L) {
          val msiPeptideMatchPK = generatedPK

          peptideMatch.id = msiPeptideMatchPK

          knownPeptideMatches += msiPeptideMatchPK -> msiPeptideMatch // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of PeptideMatch #" + omPeptideMatchId)
        }

        msiPeptideMatch
      } // End if (omPeptideMatchId <= 0)

    } // End if (msiPeptideMatch is not in knownPeptideMatches)

  }

  /**
   * Retrieves a known MsQuery or an already persisted MsQuery or persists a new MsQuery entity into Msi Db.
   *
   * @param msQuery MsQuery object, must not be {{{null}}}.
   * @param msiSearch Associated MsiSearch entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  def loadOrCreateMsQuery(
    storerContext: StorerContext,
    msQuery: MsQuery,
    msiSearch: MsiSearch
  ): MsiMsQuery = {

    checkStorerContext(storerContext)

    require(msQuery != null, "MsQuery is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val omMsQueryId = msQuery.id

    val knownMsQueries = storerContext.getEntityCache(classOf[MsiMsQuery])

    val knownMsiMsQuery = knownMsQueries.get(omMsQueryId)

    if (knownMsiMsQuery.isDefined) {
      knownMsiMsQuery.get
    } else {

      if (omMsQueryId > 0L) {
        val foundMsiMsQuery = msiEm.getReference(classOf[MsiMsQuery], omMsQueryId) // Must exist in Msi Db if OM Id > 0

        if (msiSearch != null) {
          foundMsiMsQuery.setMsiSearch(msiSearch)
        }

        knownMsQueries += omMsQueryId -> foundMsiMsQuery

        foundMsiMsQuery
      } else {
        val msiMsQuery = new MsiMsQuery()
        msiMsQuery.setCharge(msQuery.charge)
        msiMsQuery.setInitialId(msQuery.initialId)
        msiMsQuery.setMoz(msQuery.moz)

        if (msiSearch == null) {
          logger.warn(s"MsQuery #$omMsQueryId has no associated MsiSearch")
        } else {
          msiMsQuery.setMsiSearch(msiSearch) // msiSearch must be in persistence context
        }

        val msQueryProperties = msQuery.properties
        if (msQueryProperties.isDefined) {
          msiMsQuery.setSerializedProperties(ProfiJson.serialize(msQueryProperties.get))
        }

        if (msQuery.isInstanceOf[Ms2Query]) {
          val ms2Query = msQuery.asInstanceOf[Ms2Query]

          var omSpectrumId: Long = ms2Query.spectrumId

          /* Try to load Spectrum.id from knownSpectrumIdByTitle */
          val spectrumIdByTitle = storerContext.spectrumIdByTitle
          if ((spectrumIdByTitle != null) && StringUtils.isNotEmpty(ms2Query.spectrumTitle)) {
            val knownSpectrumId = spectrumIdByTitle.get(ms2Query.spectrumTitle)

            if (knownSpectrumId.isDefined) {
              omSpectrumId = knownSpectrumId.get
            }

          }

          // TODO Spectrums should be persisted before RsStorer (with PeakList entity)
          if (omSpectrumId > 0L) {
            val msiSpectrum = msiEm.find(classOf[MsiSpectrum], omSpectrumId)

            require(msiSpectrum != null, s"Spectrum #$omSpectrumId NOT found in MSIdb")

            val spectrumTitle = msiSpectrum.getTitle

            if ((spectrumTitle != null) && spectrumTitle.equals(ms2Query.spectrumTitle)) {
              msiMsQuery.setSpectrum(msiSpectrum)
            } else {
              throw new IllegalArgumentException("Invalid Spectrum.title")
            }

          } else {
            logger.warn("Invalid Spectrum Id: " + omSpectrumId)
          }

        } // End if (msQuery is a Ms2Query)

        msiEm.persist(msiMsQuery)

        knownMsQueries += omMsQueryId -> msiMsQuery

        logger.trace(s"Msi MsQuery $omMsQueryId persisted")

        val generatedPK = msiMsQuery.getId // Try to retrieve persisted Primary Key

        if (generatedPK > 0L) {
          val msiMsQueryPK = generatedPK

          msQuery.id = msiMsQueryPK

          knownMsQueries += msiMsQueryPK -> msiMsQuery // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of MsQuery " + omMsQueryId)
        }

        msiMsQuery
      } // End if (omMsQueryId <= 0)

    } // End if (msiMsQuery is not in knownMsQueries)

  }

  /**
   * Persists a new ProteinMatch entity into Msi Db.
   *
   * @param proteinMatch ProteinMatch object, must not be {{{null}}}.
   * @param msiResultSet Associated Msi ResultSet entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  def createProteinMatch(
    storerContext: StorerContext,
    proteinMatch: ProteinMatch,
    msiResultSet: MsiResultSet
  ): MsiProteinMatch = {

    checkStorerContext(storerContext)

    require(proteinMatch != null, "ProteinMatch is null")

    val omProteinMatchId = proteinMatch.id

    if (omProteinMatchId > 0L) {
      throw new UnsupportedOperationException("Updating existing ProteinMatch #" + omProteinMatchId + " is not supported")
    }

    require(msiResultSet != null, "MsiResultSet is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    /* Create new MsiProteinMatch */
    val msiProteinMatch = new MsiProteinMatch()
    msiProteinMatch.setAccession(proteinMatch.accession)

    val omProteinId = proteinMatch.getProteinId()

    if (omProteinId > 0L) {

      val msiBioSequence = loadOrCreateBioSequence(storerContext, omProteinId)
      if (msiBioSequence != null) {
        msiProteinMatch.setBioSequenceId(java.lang.Long.valueOf(msiBioSequence.getId))
      }

    }
//    else {
//      val optionalProtein = proteinMatch.protein

//      if ((optionalProtein != null) && optionalProtein.isDefined) {
//        val protein = optionalProtein.get
//
//        logger.warn("Unknown Protein {" + omProteinId + "} sequence [" + protein.sequence + ']')
//      }

//    }

    msiProteinMatch.setIsLastBioSequence(proteinMatch.isLastBioSequence)
    msiProteinMatch.setDescription(proteinMatch.description)
    msiProteinMatch.setGeneName(proteinMatch.geneName)
    msiProteinMatch.setIsDecoy(proteinMatch.isDecoy)

    /* PeptideCount fields are handled by HQL query after Msi SequenceMatches creation */
    msiProteinMatch.setPeptideCount(-1)

    msiProteinMatch.setPeptideMatchCount(Integer.valueOf(proteinMatch.peptideMatchesCount))
    msiProteinMatch.setResultSet(msiResultSet) // msiResultSet must be in persistence context
    msiProteinMatch.setScore(java.lang.Float.valueOf(proteinMatch.score))

    val scoreType = proteinMatch.scoreType

    if (scoreType != null) {
      val msiScoringId = msiScoringRepo.getScoringIdForType(msiEm, scoreType)

      require(msiScoringId != null, s"Scoring [$scoreType] NOT found in Msi Db")

      msiProteinMatch.setScoringId(msiScoringId.longValue)
    }

    // TODO handle serializedProperties

    val omTaxonId = proteinMatch.taxonId

    if (omTaxonId != 0L) {
      msiProteinMatch.setTaxonId(java.lang.Long.valueOf(omTaxonId))
    }

    msiEm.persist(msiProteinMatch)
    logger.trace(s"Msi ProteinMatch $omProteinMatchId persisted")

    val generatedPK = msiProteinMatch.getId // Try to retrieve persisted Primary Key

    if (generatedPK > 0L) {
      proteinMatch.id = generatedPK
    } else {
      logger.warn("Cannot retrieve Primary Key of ProteinMatch " + omProteinMatchId)
    }

    msiProteinMatch
  }

  /**
   * Retrieves BioSequence (Protein) from Msi Db or persists new BioSequence entity into Msi Db from existing Pdi Db entity.
   *
   * @param proteinId BioSequence (Protein) Primary key, must be > 0 and denote on existing BioSequence entity in Pdi Db.
   */
  def loadOrCreateBioSequence(storerContext: StorerContext, proteinId: Long): MsiBioSequence = {

    checkStorerContext(storerContext)

    require(proteinId > 0L, "Invalid proteinId")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val knownBioSequences = storerContext.getEntityCache(classOf[MsiBioSequence])

    val knownMsiBioSequence = knownBioSequences.get(proteinId)

    if (knownMsiBioSequence.isDefined) {
      knownMsiBioSequence.get
    } else {
      var msiBioSequence: MsiBioSequence = msiEm.find(classOf[MsiBioSequence], proteinId)

      if (msiBioSequence != null) {
        knownBioSequences += proteinId -> msiBioSequence
      }

      msiBioSequence
    }

  }

  /**
   * Persists a new SequenceMatch entity into Msi Db.
   *
   * @param sequenceMatch SequenceMatch object, must not be {{{null}}}.
   * @param msiProteinMatchId ProteinMatch Primary key, must be > 0 and denote an existing ProteinMatch entity in Msi Db (Msi transaction committed).
   * @param msiResultSetId ResultSet Primary key, must be > 0 and denote an existing ResultSet entity in Msi Db (Msi transaction committed).
   */
  def createSequenceMatch(
    storerContext: StorerContext,
    sequenceMatch: SequenceMatch,
    msiProteinMatchId: Long,
    msiResultSetId: Long
  ): MsiSequenceMatch = {

    checkStorerContext(storerContext)

    require(sequenceMatch != null, "SequenceMatch is null")

    require(msiProteinMatchId > 0L, "Invalid Msi ProteinMatch Id")

    require(msiResultSetId > 0L, "Invalid Msi ResultSet Id")

    /**
     * Retrieves Msi Peptide entity Primary key from given OM Id or Peptide object.
     */
    def retrieveMsiPeptideId(peptideId: Long, optionalPeptide: Option[Peptide]): Long = {
      var msiPeptideId: Long = -1L

      if (peptideId > 0L) {
        msiPeptideId = peptideId
      } else {

        if ((optionalPeptide != null) && optionalPeptide.isDefined) {
          val peptide = optionalPeptide.get
          val peptideIdent = new PeptideIdent(peptide.sequence, peptide.ptmString)

          val knownMsiPeptide = storerContext.msiPeptides.get(peptideIdent)

          if (knownMsiPeptide.isDefined) {
            msiPeptideId = knownMsiPeptide.get.getId
          }

        }

      }

      msiPeptideId
    }

    /**
     * Retrieves Msi PeptideMatch entity Primary key from given OM Id.
     */
    def retrieveMsiPeptideMatchId(peptideMatchId: Long): Long = {
      var msiPeptideMatchId: Long = -1L

      if (peptideMatchId > 0L) {
        msiPeptideMatchId = peptideMatchId
      } else {
        val knownPeptideMatches = storerContext.getEntityCache(classOf[MsiPeptideMatch])

        val knownMsiPeptideMatch = knownPeptideMatches.get(peptideMatchId)

        if (knownMsiPeptideMatch.isDefined) {
          msiPeptideMatchId = knownMsiPeptideMatch.get.getId
        }

      }

      msiPeptideMatchId
    }

    val msiSequenceMatchPK = new SequenceMatchPK()
    msiSequenceMatchPK.setProteinMatchId(msiProteinMatchId)

    /* Retrieve Peptide Id from Msi */
    val msiPeptideId = retrieveMsiPeptideId(sequenceMatch.getPeptideId(), sequenceMatch.peptide)

    require(msiPeptideId > 0L, "Unknown MSIdb Peptide Id: " + msiPeptideId)

    msiSequenceMatchPK.setPeptideId(msiPeptideId)
    msiSequenceMatchPK.setStart(sequenceMatch.start)
    msiSequenceMatchPK.setStop(sequenceMatch.end)

    val msiSequenceMatch = new MsiSequenceMatch()
    msiSequenceMatch.setId(msiSequenceMatchPK)

    /* Retrieve best PeptideMatch Id from Msi */
    val msiPeptideMatchId = retrieveMsiPeptideMatchId(sequenceMatch.getBestPeptideMatchId())

    require(msiPeptideMatchId > 0L, "Unknown MSIdb best PeptideMatch Id: " + msiPeptideMatchId)

    msiSequenceMatch.setBestPeptideMatchId(msiPeptideMatchId)
    msiSequenceMatch.setIsDecoy(sequenceMatch.isDecoy)
    msiSequenceMatch.setResidueAfter(scalaCharToCharacter(sequenceMatch.residueAfter))
    msiSequenceMatch.setResidueBefore(scalaCharToCharacter(sequenceMatch.residueBefore))
    msiSequenceMatch.setResultSetId(msiResultSetId)

    // TODO handle serializedProperties

    storerContext.getMSIDbConnectionContext.getEntityManager.persist(msiSequenceMatch)
    logger.trace(s"Msi SequenceMatch for ProteinMatch #$msiProteinMatchId Peptide #$msiPeptideId persisted")

    msiSequenceMatch
  }

  /* Private methods */
  def checkStorerContext(storerContext: StorerContext) {

    if ((storerContext == null) || !storerContext.isJPA) { // TODO add a check on EntityManagers ?
      throw new IllegalArgumentException("Invalid StorerContext")
    }

  }

  /**
   *  @param msiSearchSetting Associated Msi SearchSetting entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  private def bindUsedPtm(
    storerContext: StorerContext,
    ptmDefinition: PtmDefinition, isFixed: Boolean,
    msiSearchSetting: MsiSearchSetting
  ) {

    checkStorerContext(storerContext)

    assert(ptmDefinition != null, "PtmDefinition is null")

    assert(msiSearchSetting != null, "MsiSearchSetting is null")

    val msiUsedPtm = new MsiUsedPtm()
    msiUsedPtm.setIsFixed(isFixed)
    msiUsedPtm.setShortName(ptmDefinition.names.shortName)
    // TODO UsedPtm.type field should be removed from Msi Db schema

    val msiPtmSpecificity = loadPtmSpecificity(storerContext, ptmDefinition)

    require(msiPtmSpecificity != null, s"Unknown PtmSpecificity [${ptmDefinition.names.shortName}]")

    msiUsedPtm.setPtmSpecificity(msiPtmSpecificity)
    msiPtmSpecificity.addUsedPtm(msiUsedPtm) // Reverse association

    msiUsedPtm.setSearchSetting(msiSearchSetting) // msiSearchSetting must be in persistence context
    msiSearchSetting.addUsedPtms(msiUsedPtm) // Reverse association

    storerContext.getMSIDbConnectionContext.getEntityManager.persist(msiUsedPtm)
    logger.trace(s"Msi UsedPtm Specificity #${ msiPtmSpecificity.getId} for current SearchSetting persisted")
  }

  private def bindPeptidePtm(
    storerContext: StorerContext,
    msiPeptide: MsiPeptide,
    locatedPtm: LocatedPtm
  ) {

    checkStorerContext(storerContext)

    assert(msiPeptide != null, "MsiPeptide is null")
    assert(locatedPtm != null, "LocatedPtm is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val ptmDefinition = locatedPtm.definition

    val omSpecificityId = ptmDefinition.id // Immutable val here

    val msiPtmSpecificity = if (omSpecificityId <= 0L) null
    else {
      msiEm.getReference(classOf[MsiPtmSpecificity], omSpecificityId) // Must exist in Ps Db if OM Id > 0
    }

    require(msiPtmSpecificity != null, s"PtmSpecificity '${ptmDefinition.names.shortName}' NOT found in MSIdb")

    val msiPeptidePtm = new MsiPeptidePtm()
    msiPeptidePtm.setAverageMass(locatedPtm.averageMass)
    msiPeptidePtm.setMonoMass(locatedPtm.monoMass)
    msiPeptidePtm.setSeqPosition(locatedPtm.seqPosition)

    // TODO handle AtomLabel

    msiPeptidePtm.setPeptide(msiPeptide) // msiPeptide must be in persistence context
    msiPeptidePtm.setSpecificity(msiPtmSpecificity)

    msiEm.persist(msiPeptidePtm)

    logger.trace(s"MSIdb PeptidePtm Specificity #${msiPtmSpecificity.getId} of Peptide '${msiPeptide.getSequence}' persisted")
  }

  private def bindProteinMatchSeqDatabaseMap(
    storerContext: StorerContext,
    msiProteinMatchId: Long,
    seqDatabaseId: Long,
    msiResultSet: MsiResultSet
  ): MsiSeqDatabase = {

    checkStorerContext(storerContext)

    assert(msiProteinMatchId > 0L, "Invalid Msi ProteinMatch Id")

    assert(msiResultSet != null, "MsiResultSet is null")

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    def retrieveMsiSeqDatabase(seqDatabseId: Long): MsiSeqDatabase = {

      val knownSeqDatabases = storerContext.getEntityCache(classOf[MsiSeqDatabase])
      val knownMsiSeqDatabase = knownSeqDatabases.get(seqDatabseId)

      if (knownMsiSeqDatabase.isDefined) {
        knownMsiSeqDatabase.get
      } else {

        if (seqDatabseId > 0L) {
          val foundMsiSeqDatabase = msiEm.find(classOf[MsiSeqDatabase], seqDatabseId)

          if (foundMsiSeqDatabase != null) {
            knownSeqDatabases += seqDatabseId -> foundMsiSeqDatabase
          }

          foundMsiSeqDatabase
        } else {
          null
        }

      }

    }

    val msiSeqDatabase = retrieveMsiSeqDatabase(seqDatabaseId)

    if (msiSeqDatabase == null) {
      logger.warn("Unknown Msi SeqDatabase Id: " + seqDatabaseId)
    } else {
      val proteinMatchSeqDatabaseMapPK = new ProteinMatchSeqDatabaseMapPK()
      proteinMatchSeqDatabaseMapPK.setProteinMatchId(msiProteinMatchId)
      proteinMatchSeqDatabaseMapPK.setSeqDatabaseId(msiSeqDatabase.getId)

      val msiProteinMatchSeqDatabase = new MsiProteinMatchSeqDatabaseMap()

      // TODO handle serializedProperties

      msiProteinMatchSeqDatabase.setId(proteinMatchSeqDatabaseMapPK)
      msiProteinMatchSeqDatabase.setResultSetId(msiResultSet)

      msiEm.persist(msiProteinMatchSeqDatabase)
      logger.trace(s"Msi ProteinMatchSeqDatabase for ProteinMatch #$msiProteinMatchId SeqDatabase #${msiSeqDatabase.getId} persisted")
    }
    msiSeqDatabase
  }

}
