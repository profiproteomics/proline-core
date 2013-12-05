package fr.proline.core.om.storer.msi.impl

import java.sql.Timestamp
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable
import com.codahale.jerkson.Json
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.{ InstrumentConfig, LocatedPtm, MSISearch, Ms2Query, MsQuery, PeaklistSoftware, Peptide, PeptideMatch, ProteinMatch, PtmDefinition, ResultSet, SeqDatabase, SequenceMatch }
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.core.om.utils.PeptideIdent
import fr.proline.core.orm.msi.{ MsiSearch, ProteinMatchSeqDatabaseMapPK }
import fr.proline.core.orm.msi.ResultSet.Type
import fr.proline.core.orm.msi.SequenceMatchPK
import fr.proline.core.orm.msi.repository.{ MsiEnzymeRepository => msiEnzymeRepo, MsiInstrumentConfigRepository => msiInstrumentConfigRepo, MsiPeaklistSoftwareRepository => msiPeaklistSoftwareRepo, MsiPeptideRepository => msiPeptideRepo, MsiSeqDatabaseRepository => msiSeqDatabaseRepo, ScoringRepository => scoringRepo }
import fr.proline.core.orm.pdi.repository.{ PdiSeqDatabaseRepository => pdiSeqDatabaseRepo }
import fr.proline.core.orm.ps.repository.{ PsPeptideRepository => psPeptideRepo, PsPtmRepository => psPtmRepo }
import fr.proline.core.orm.uds.repository.{ UdsEnzymeRepository => udsEnzymeRepo, UdsInstrumentConfigurationRepository => udsInstrumentConfigRepo, UdsPeaklistSoftwareRepository => udsPeaklistSoftwareRepo }
import fr.proline.util.StringUtils
import fr.proline.core.utils.ResidueUtils._
import fr.proline.core.orm.msi.PeptideReadablePtmString

/**
 * JPA implementation of ResultSet storer.
 *
 * @param dbManagement DatabaseManagement : From which connection to Ps Db,  Uds Db and Pdi Db is retrieve
 * @param projectID Id of the project to save information to
 */
class JPARsStorer(override val pklWriter: Option[IPeaklistWriter] = None) extends AbstractRsStorer(pklWriter) with Logging {

  type MsiPeaklist = fr.proline.core.orm.msi.Peaklist
  type MsiPeaklistSoftware = fr.proline.core.orm.msi.PeaklistSoftware
  type MsiSearchSetting = fr.proline.core.orm.msi.SearchSetting
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

  type PsPeptide = fr.proline.core.orm.ps.Peptide
  type PsPtm = fr.proline.core.orm.ps.Ptm
  type PsPtmSpecificity = fr.proline.core.orm.ps.PtmSpecificity
  type PsPeptidePtm = fr.proline.core.orm.ps.PeptidePtm

  type PdiBioSequence = fr.proline.core.orm.pdi.BioSequence

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

    if (msQueries == null) {
      throw new IllegalArgumentException("MsQueries seq is null")
    }

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
   * @param msiEm Msi EntityManager must have a valid transaction started.
   */
  def createResultSet(resultSet: ResultSet, storerContext: StorerContext): Long = {

    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }

    checkStorerContext(storerContext)

    // TODO Decoy, Native and Quantified bools must be coherant with ORMResultSetProvider.buildResultSet()
    def parseType(resultSet: ResultSet): Type = {

      if (resultSet.isQuantified) {
        Type.QUANTITATION
      } else {

        if (resultSet.isNative) {

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

        if (foundMsiResultSet == null) {
          throw new IllegalArgumentException("ResultSet #" + omResultSetId + " NOT found in MSIdb")
        }

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
          msiResultSet.setSerializedProperties(Json.generate(resultSetProperties.get))
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

        msiEm.persist(msiResultSet)

        knownResultSets += omResultSetId -> msiResultSet

        logger.trace("ResultSet {" + omResultSetId + "} persisted in MSI")

        /* Peptides & PeptideMatches */
        retrievePeptides(storerContext, msiResultSet, resultSet.peptides)

        for (peptMatch <- resultSet.peptideMatches) {
          createPeptideMatch(storerContext,
            peptMatch, msiResultSet, storedMsiSearch)
        }

        logger.info(resultSet.peptideMatches.length + " PeptideMatches have been stored")

        /* Fill proteinMatchSeqDatabases and proteinMatchSequenceMatches Maps for postponed handling
         * (after flushing of Msi EntityManager) */
        val proteinMatchSeqDatabases = mutable.Map.empty[MsiProteinMatch, Array[Long]]

        val proteinMatchSequenceMatches = mutable.Map.empty[MsiProteinMatch, Array[SequenceMatch]]

        /* Proteins (BioSequence) & ProteinMatches */
        for (protMatch <- resultSet.proteinMatches) {
          val msiProteinMatch = createProteinMatch(storerContext, protMatch, msiResultSet)

          val seqDatabaseIds = protMatch.seqDatabaseIds
          if ((seqDatabaseIds != null) && !seqDatabaseIds.isEmpty) {
            proteinMatchSeqDatabases += msiProteinMatch -> seqDatabaseIds
          }

          val sequenceMatches = protMatch.sequenceMatches
          if ((sequenceMatches != null) && !sequenceMatches.isEmpty) {
            proteinMatchSequenceMatches += msiProteinMatch -> sequenceMatches
          }

        } // End loop for each proteinMatch

        logger.info(resultSet.proteinMatches.length + " ProteinMatches have been stored")

        // TODO handle ResultSet.children    Uniquement pour le grouping ?

        msiEm.flush() // FLUSH to handle ProteinMatchSeqDatabaseMap and proteinMatchSequenceMatches and retrieve Msi ResultSet Id

        val generatedPK = msiResultSet.getId // Try to retrieve persisted Primary Key (after FLUSH)

        if (generatedPK > 0L) {
          msiResultSetPK = generatedPK

          resultSet.id = msiResultSetPK

          knownResultSets += msiResultSetPK -> msiResultSet // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of ResultSet " + omResultSetId)
        }

        if (!proteinMatchSeqDatabases.isEmpty) {
          logger.trace("Handling proteinMatchSeqDatabases after flushing of MSI EntityManager")

          /* Handle proteinMatchSeqDatabaseMap after having persisted MsiProteinMatches, SeqDatabases and current MsiResultSet */
          for (pMSDEntry <- proteinMatchSeqDatabases; seqDatabaseId <- pMSDEntry._2) {
            bindProteinMatchSeqDatabaseMap(storerContext, pMSDEntry._1.getId, seqDatabaseId, msiResultSet)
          }

        } // End if (proteinMatchSeqDatabases is not empty)

        if (!proteinMatchSequenceMatches.isEmpty) {
          logger.trace("Handling proteinMatchSequenceMatches and ProteinMatch.peptideCount after flushing of Msi EntityManager")

          /* Handle proteinMatchSequenceMatches after having persisted MsiProteinMatches, MsiPeptideMatches and current MsiResultSet */
          var sequenceMatchCount = 0

          for (pMSMEntry <- proteinMatchSequenceMatches) {
            val msiProteinMatch = pMSMEntry._1
            val msiProteinMatchId = msiProteinMatch.getId

            val peptideIds = mutable.Set.empty[Long]

            for (sequenceMatch <- pMSMEntry._2) {
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

      if (msiResultSet == null) {
        throw new IllegalArgumentException("ResultSet #" + msiResultSet + " NOT found in MSIdb")
      } else {
        knownResultSets += resultSetId -> msiResultSet

        msiResultSet
      }

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

    if (search == null) {
      throw new IllegalArgumentException("MsiSearch is mandatory")
    }

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

        if (foundMsiSearch == null) {
          throw new IllegalArgumentException("MsiSearch #" + omMsiSearchId + " NOT found in MSIdb")
        }

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

        msiSearch.setSubmittedQueriesCount(search.submittedQueriesCount)
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

        logger.trace("MsiSearch {" + omMsiSearchId + "} persisted")

        val generatedPK = msiSearch.getId // Try to retrieve persisted Primary Key (after FLUSH)

        if (generatedPK > 0L) {
          msiSearchPK = generatedPK

          search.id = msiSearchPK

          knownMsiSearchs += msiSearchPK -> msiSearch // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of MSI Search " + omMsiSearchId)
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

      if (msiSearch == null) {
        throw new IllegalArgumentException("MsiSearch #" + msiSearchId + " NOT found in MSIdb")
      } else {
        knownMsiSearches += msiSearchId -> msiSearch

        msiSearch
      }

    }

  }

  /**
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

      if (msiPeaklist == null) {
        throw new IllegalArgumentException("Peaklist #" + peaklistId + " NOT found in MSIdb")
      } else {
        knownPeaklists += peaklistId -> msiPeaklist

        msiPeaklist
      }

    }

  }

  /**
   * Retrieves an already persisted PeaklistSoftware or persists a new PeaklistSoftware entity into Msi Db from an existing Uds Db entity.
   *
   * @param peaklistSoftware PeaklistSoftware object, must not be {{{null}}}.
   */
  def loadOrCreatePeaklistSoftware(storerContext: StorerContext,
                                   peaklistSoftware: PeaklistSoftware): MsiPeaklistSoftware = {

    checkStorerContext(storerContext)

    if (peaklistSoftware == null) {
      throw new IllegalArgumentException("PeaklistSoftware is null")
    }

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

      if (udsPeaklistSoftware == null) {
        throw new IllegalArgumentException("PeaklistSoftware [" + peaklistSoftware.name + "] [" + peaklistSoftware.version + "] NOT found in Uds Db")
      } else {
        msiPeaklistSoftware = new MsiPeaklistSoftware(udsPeaklistSoftware)

        msiEm.persist(msiPeaklistSoftware)
        logger.trace("Msi PeaklistSoftware #" + udsPeaklistSoftware.getId + " persisted")

        peaklistSoftware.id = udsPeaklistSoftware.getId // Update OM entity with persisted Primary key        
      }

    }

    msiPeaklistSoftware
  }

  /**
   * Retrieves an already persisted SearchSetting or persists a new SearchSetting entity into Msi Db.
   *
   * @param search Associated MSISearch object, must not be {{{null}}} and must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  def loadOrCreateSearchSetting(storerContext: StorerContext,
                                search: MSISearch): MsiSearchSetting = {

    checkStorerContext(storerContext)

    if (search == null) {
      throw new IllegalArgumentException("Search is null")
    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val udsEm = storerContext.getUDSDbConnectionContext.getEntityManager

    val searchSettings = search.searchSettings
    val omSearchSettingsId = searchSettings.id

    if (omSearchSettingsId > 0L) {
      msiEm.getReference(classOf[MsiSearchSetting], omSearchSettingsId) // Must exist in Msi Db if OM Id > 0
    } else {
      val msiSearchSetting = new MsiSearchSetting()
      msiSearchSetting.setIsDecoy(searchSettings.isDecoy)
      msiSearchSetting.setMaxMissedCleavages(Integer.valueOf(searchSettings.maxMissedCleavages))
      msiSearchSetting.setPeptideChargeStates(searchSettings.ms1ChargeStates)
      msiSearchSetting.setPeptideMassErrorTolerance(java.lang.Double.valueOf(searchSettings.ms1ErrorTol))
      msiSearchSetting.setPeptideMassErrorToleranceUnit(searchSettings.ms1ErrorTolUnit)
      msiSearchSetting.setQuantitation(searchSettings.quantitation)

      val searchSettingProperties = searchSettings.properties
      if (searchSettingProperties.isDefined) {
        msiSearchSetting.setSerializedProperties(Json.generate(searchSettingProperties.get))
      }

      msiSearchSetting.setSoftwareName(searchSettings.softwareName)
      msiSearchSetting.setSoftwareVersion(searchSettings.softwareVersion)
      msiSearchSetting.setTaxonomy(searchSettings.taxonomy)

      msiSearchSetting.setInstrumentConfig(loadOrCreateInstrumentConfig(storerContext, searchSettings.instrumentConfig))

      for (enzyme <- searchSettings.usedEnzymes) {
        msiSearchSetting.addEnzyme(loadOrCreateEnzyme(storerContext, enzyme.name))
      }

      msiEm.persist(msiSearchSetting)
      logger.trace("Msi SearchSetting {" + omSearchSettingsId + "} persisted")

      val generatedPK = msiSearchSetting.getId // Try to retrieve persisted Primary Key

      if (generatedPK > 0L) {
        searchSettings.id = generatedPK
      } else {
        logger.warn("Cannot retrieve Primary Key of SearchSettings " + omSearchSettingsId)
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
          logger.trace("Msi SettingsSeqDatabaseMap SearchSetting {" + omSearchSettingsId + "} SeqDatabase #" + msiSeqDatabase.getId + " persisted")
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
  def loadOrCreateInstrumentConfig(storerContext: StorerContext,
                                   instrumentConfig: InstrumentConfig): MsiInstrumentConfig = {

    checkStorerContext(storerContext)

    if (instrumentConfig == null) {
      throw new IllegalArgumentException("InstrumentConfig is null")
    }

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

      if (udsInstrumentConfiguration == null) {
        throw new IllegalArgumentException("InstrumentConfiguration [" + instrumentConfig.name +
          "] [" + instrumentConfig.ms1Analyzer + "] NOT found in Uds Db")
      } else {
        msiInstrumentConfig = new MsiInstrumentConfig(udsInstrumentConfiguration)

        msiEm.persist(msiInstrumentConfig)
        logger.trace("Msi InstrumentConfig #" + udsInstrumentConfiguration.getId + " persisted")
      }

    }

    msiInstrumentConfig
  }

  /**
   * Retrieves an already persisted Enzyme or persists a new Enzyme entity into Msi Db from an existing Uds Db entity.
   *
   * @param enzymeName Name of the Enzyme (ignoring case), must not be empty.
   */
  def loadOrCreateEnzyme(storerContext: StorerContext,
                         enzymeName: String): MsiEnzyme = {

    checkStorerContext(storerContext)

    if (StringUtils.isEmpty(enzymeName)) {
      throw new IllegalArgumentException("Invalid enzymeName")
    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    var msiEnzyme: MsiEnzyme = msiEnzymeRepo.findEnzymeForName(msiEm, enzymeName)

    if (msiEnzyme == null) {
      val udsEnzyme = udsEnzymeRepo.findEnzymeForName(storerContext.getUDSDbConnectionContext.getEntityManager, enzymeName)

      if (udsEnzyme == null) {
        throw new IllegalArgumentException("Enzyme [" + enzymeName + "] NOT found in Uds Db")
      } else {
        msiEnzyme = new MsiEnzyme(udsEnzyme)

        msiEm.persist(msiEnzyme)
        logger.trace("Msi Enzyme #" + udsEnzyme.getId + " persisted")
      }

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

    if (seqDatabase == null) {
      throw new IllegalArgumentException("SeqDatabase is null")
    }

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
          /* Try to load from Pdi Db by name and Fasta file path */
          val pdiSeqDatabaseInstance = pdiSeqDatabaseRepo.findSeqDbInstanceWithNameAndFile(storerContext.getPDIDbConnectionContext.getEntityManager, seqDatabase.name, seqDatabase.filePath)

          if (pdiSeqDatabaseInstance == null) {
            logger.warn("SeqDatabase [" + seqDatabase.name + "] [" + seqDatabase.filePath + "] NOT found in PDIdb, create one from OM")

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

            msiEm.persist(msiSeqDatabase);
            logger.trace("Msi SeqDatabase {" + omSeqDatabaseId + "} persisted")
          } else {
            /* Create derived Msi entity from Pdi */
            msiSeqDatabase = new MsiSeqDatabase(pdiSeqDatabaseInstance);

            msiEm.persist(msiSeqDatabase);
            logger.trace("Msi SeqDatabase #" + pdiSeqDatabaseInstance.getId + " persisted")
          } // End if (pdiSeqDatabaseInstance is not null)

        } // End if (msiSeqDatabase is null)

      } // End if (msiSeqDatabase is null)

      knownSeqDatabases += omSeqDatabaseId -> msiSeqDatabase

      val generatedPK = msiSeqDatabase.getId // Try to retrieve persisted Primary Key

      if (generatedPK > 0L) {
        val msiSeqDatabasePK = generatedPK

        seqDatabase.id = msiSeqDatabasePK

        knownSeqDatabases += msiSeqDatabasePK -> msiSeqDatabase // Cache with MSI Primary Key
      } else {
        logger.warn("Cannot retrieve Primary Key of SeqDatabase " + omSeqDatabaseId)
      }

      msiSeqDatabase
    } // End if (msiSeqDatabase not in knownSeqDatabases)

  }

  /**
   * Retrieves a known PtmSpecificity or an already persisted PtmSpecificity or persists a new PtmSpecificity entity into Msi Db from an existing Ps Db entity.
   *
   * @param ptmDefinition PtmDefinition object, must not be {{{null}}}.
   */
  def loadOrCreatePtmSpecificity(storerContext: StorerContext,
                                 ptmDefinition: PtmDefinition): MsiPtmSpecificity = {

    checkStorerContext(storerContext)

    if (ptmDefinition == null) {
      throw new IllegalArgumentException("PtmDefinition is null")
    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val knownPtmSpecificities = storerContext.getEntityCache(classOf[MsiPtmSpecificity])

    val knownMsiPtmSpecificity = knownPtmSpecificities.get(ptmDefinition.id)

    if (knownMsiPtmSpecificity.isDefined) {
      knownMsiPtmSpecificity.get
    } else {
      var msiPtmSpecificity: MsiPtmSpecificity = null

      if (ptmDefinition.id > 0L) {
        /* Try to load from Msi Db by Id */
        msiPtmSpecificity = msiEm.find(classOf[MsiPtmSpecificity], ptmDefinition.id)
      }

      if (msiPtmSpecificity == null) {
        /* Try to load from Ps Db by name, location and residue */
        val psPtmSpecificity = psPtmRepo.findPtmSpecificityForNameLocResidu(storerContext.getPSDbConnectionContext.getEntityManager, ptmDefinition.names.shortName, ptmDefinition.location,
          scalaCharToCharacter(ptmDefinition.residue))

        if (psPtmSpecificity == null) {
          logger.warn("PtmSpecificity [" + ptmDefinition.names.shortName + "] NOT found in PSdb")
        } else {
          /* Avoid duplicate msiPtmSpecificity entities creation */
          msiPtmSpecificity = msiEm.find(classOf[MsiPtmSpecificity], psPtmSpecificity.getId)

          if (msiPtmSpecificity == null) {
            /* Create derived Msi entity */
            msiPtmSpecificity = new MsiPtmSpecificity(psPtmSpecificity)

            msiEm.persist(msiPtmSpecificity)
            logger.trace("Msi PtmSpecificity #" + psPtmSpecificity.getId + " persisted")
          }

        } // End if (psPtmSpecificity is not null)

      } // End if (msiPtmSpecificity is null)

      if (msiPtmSpecificity != null) {
        knownPtmSpecificities += ptmDefinition.id -> msiPtmSpecificity
      } // End if (msiPtmSpecificity is not null)

      msiPtmSpecificity
    } // End if (msiPtmSpecificity not found in knownPtmSpecificities)

  }

  /**
   * Retrieves Peptides from Msi Db or persists new Peptide entities into Msi Db from existing or '''created''' Ps Db entities.
   *
   * @param msiEm Msi EntityManager must have a valid transaction started.
   * @param psEm A transaction may be started on psEm to persist new Peptides in Ps Db.
   * @param peptides Array of Peptide objects to fetch, must not be {{{null}}}.
   * @param msiPeptides Mutable Map will contain fetched and created Msi Peptide entities accessed by PeptideIdent(sequence, ptmString). Map must not be {{{null}}}.
   * The map can contain already fetched Peptides in current Msi transaction.
   */
  def retrievePeptides(storerContext: StorerContext, msiResultSet: MsiResultSet,
                       peptides: Array[Peptide]) {

    checkStorerContext(storerContext)

    require(msiResultSet != null, "MsiResultSet is null")

    require(peptides != null, "Peptides array is null")

    /**
     * Build a Java List<Long> from a Scala Collection[Long].
     */
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
    val remainingOmPeptidesIds = mutable.Set.empty[Long] // Keep OM Peptides whose Id > 0

    for (peptide <- peptides) {
      val peptIdent = new PeptideIdent(peptide.sequence, peptide.ptmString)

      if (!msiPeptides.contains(peptIdent)) {
        remainingPeptides += peptIdent -> peptide

        val omPeptideId = peptide.id

        if (omPeptideId > 0L) {
          remainingOmPeptidesIds += omPeptideId
        }

      }

    }

    /* Retrieve all known Peptides from Msi Db by OM Ids > 0 */
    if (!remainingOmPeptidesIds.isEmpty) {
      logger.debug("Trying to retrieve " + remainingOmPeptidesIds.size + " Peptides from MSIdb by Ids")

      val foundMsiPeptides = msiPeptideRepo.findPeptidesForIds(msiEm, buildIdsList(remainingOmPeptidesIds))

      if ((foundMsiPeptides != null) && !foundMsiPeptides.isEmpty) {

        for (msiPeptide <- foundMsiPeptides) {
          val peptideId = msiPeptide.getId
          val peptIdent = new PeptideIdent(msiPeptide.getSequence, msiPeptide.getPtmString)

          msiPeptides += peptIdent -> msiPeptide

          remainingPeptides.remove(peptIdent)
          remainingOmPeptidesIds.remove(peptideId)
        }

      }

    }

    /* Retrieve all known Peptides from Ps Db by OM Ids > 0 */

    if (!remainingOmPeptidesIds.isEmpty) {
      logger.debug("Trying to retrieve " + remainingOmPeptidesIds.size + " Peptides from PSdb by Ids")

      val foundPsPeptides = psPeptideRepo.findPeptidesForIds(storerContext.getPSDbConnectionContext.getEntityManager, buildIdsList(remainingOmPeptidesIds))
      if ((foundPsPeptides != null) && !foundPsPeptides.isEmpty) {

        for (psPeptide <- foundPsPeptides) {
          val peptideId = psPeptide.getId
          val peptIdent = new PeptideIdent(psPeptide.getSequence, psPeptide.getPtmString)

          var msiPeptide: MsiPeptide = msiEm.find(classOf[MsiPeptide], peptideId)

          if (msiPeptide == null) {
            /* Create derived Msi entity */
            msiPeptide = new MsiPeptide(psPeptide)

            msiEm.persist(msiPeptide)
            logger.trace("Msi Peptide #" + peptideId + " persisted")
          }

          msiPeptides += peptIdent -> msiPeptide

          remainingPeptides.remove(peptIdent)
          remainingOmPeptidesIds.remove(peptideId)
        }

      }

    }

    if (!remainingOmPeptidesIds.isEmpty) {
      throw new IllegalArgumentException("Peptides (" + remainingOmPeptidesIds.mkString(", ") + ") NOT found in PSdb")
    }

    /* Do not retrieve Peptides by (sequence, ptmString) from Ps Db : Already done by parser implementation */

    if (!remainingPeptides.isEmpty) {
      /* Create new Peptides into Ps Db */
      val createdPsPeptides = persistPsPeptides(storerContext, remainingPeptides.toMap[PeptideIdent, Peptide])

      for (peptideEntry <- createdPsPeptides) {
        val peptIdent = peptideEntry._1
        val psPeptide = peptideEntry._2

        /* Create derived Msi entity */
        val msiPeptide = new MsiPeptide(psPeptide)

        msiEm.persist(msiPeptide)

        msiPeptides += peptIdent -> msiPeptide

        logger.trace("Msi Peptide #" + psPeptide.getId + " persisted")

        val omPeptide = remainingPeptides.remove(peptIdent)
        if (omPeptide.isDefined) {
          /* PS transaction is committed by persistPsPeptides() : Primary Keys of persisted PS Peptide must be OK here */
          omPeptide.get.id = psPeptide.getId // Update OM entity with persisted Primary key
        }

      } // End loop for each createdPsPeptide => create in Msi

    } // End if (remainingPeptides is not empty)

    logger.info(msiPeptides.size + " new Peptides stored in the MSIdb")

    /* Create PeptideReadablePtmString entities */
    for (peptide <- peptides) {

      if (!StringUtils.isEmpty(peptide.readablePtmString)) {
        val peptIdent = new PeptideIdent(peptide.sequence, peptide.ptmString)

        val optionalMsiPeptide = msiPeptides.get(peptIdent)

        if (optionalMsiPeptide.isEmpty) {
          logger.warn("Unable to retrieve Peptide [" + peptIdent + "] from cache")
        } else {
          val readablePtmStringEntity = new PeptideReadablePtmString()
          readablePtmStringEntity.setPeptide(optionalMsiPeptide.get)
          readablePtmStringEntity.setResultSet(msiResultSet)
          readablePtmStringEntity.setReadablePtmString(peptide.readablePtmString)

          msiEm.persist(readablePtmStringEntity)
          logger.trace("PeptideReadablePtmString [" + peptide.readablePtmString + "] persisted")
        }

      }

    }

    if (!remainingPeptides.isEmpty) {
      logger.error("There are " + remainingPeptides.size + " unknown Peptides in ResultSet")
    } // End if (remainingPeptides is not empty)

  }

  /**
   * Persists new Peptide entities into Ps Db.
   *
   * @param psEm A transaction will be started on psEm to persist new Peptides in Ps Db.
   * @param peptides Map of Peptide objects to create, accessed by PeptideIdent(sequence, ptmString). Must not be {{{null}}}.
   * @return Map of created Ps Peptide entities accessed by PeptideIdent.
   */
  def persistPsPeptides(storerContext: StorerContext, peptides: Map[PeptideIdent, Peptide]): Map[PeptideIdent, PsPeptide] = {

    checkStorerContext(storerContext)

    if (peptides == null) {
      throw new IllegalArgumentException("Peptides map is null")
    }

    val psEm = storerContext.getPSDbConnectionContext.getEntityManager

    logger.debug("Storing " + peptides.size + " new Peptides into PSdb")

    val createdPsPeptides = Map.newBuilder[PeptideIdent, PsPeptide]

    val psTransaction = psEm.getTransaction
    var psTransacOk: Boolean = false

    try {
      psTransaction.begin()
      psTransacOk = false

      for (peptideEntry <- peptides.toMap[PeptideIdent, Peptide]) {
        val peptIdent = peptideEntry._1
        val peptide = peptideEntry._2

        val newPsPeptide = new PsPeptide()
        newPsPeptide.setSequence(peptIdent.sequence)
        newPsPeptide.setPtmString(peptIdent.ptmString)
        newPsPeptide.setCalculatedMass(peptide.calculatedMass)

        // TODO handle serializedProperties
        // TODO handle atomLabel

        psEm.persist(newPsPeptide)

        /*  PsPeptide must be in persistence context before calling bindPeptidePtm() method */
        if ((peptide.ptms != null) && !peptide.ptms.isEmpty) {

          for (locatedPtm <- peptide.ptms) {
            bindPeptidePtm(storerContext, newPsPeptide, locatedPtm)
          }

        }

        createdPsPeptides += peptIdent -> newPsPeptide

        logger.trace("Ps Peptide {" + peptide.id + "} persisted")
      } // End loop for each Peptides

      psTransaction.commit()
      psTransacOk = true

      createdPsPeptides.result
    } finally {

      /* Check psTransaction integrity */
      if ((psTransaction != null) && !psTransacOk) {

        try {
          psTransaction.rollback()
        } catch {
          case ex: Exception => logger.error("Error rollbacking PSdb transaction", ex)
        }

      }

    } // End try - finally block on psTransaction

  }

  /**
   * Retrieves a known PeptideMatch or persists a new PeptideMatch entity into Msi Db.
   *
   * @param peptideMatch PeptideMatch object, must not be {{{null}}}.
   * @param msiPeptides Map of already fetched Msi Peptide entities accessed by PeptideIdent, must not be {{{null}}}.
   * @param msiResultSet Associated Msi ResultSet entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   * @param msiSearch Associated MsiSearch entity, must be attached to {{{msiEm}}} persistence context before calling this method.
   */
  def createPeptideMatch(storerContext: StorerContext,
                         peptideMatch: PeptideMatch,
                         msiResultSet: MsiResultSet,
                         msiSearch: MsiSearch): MsiPeptideMatch = {

    checkStorerContext(storerContext)

    if (peptideMatch == null) {
      throw new IllegalArgumentException("PeptideMatch is null")
    }

    if (msiResultSet == null) {
      throw new IllegalArgumentException("MsiResultSet is null")
    }

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

        if (msiPeptideId <= 0L) {
          throw new IllegalArgumentException("Unknown MSIdb Peptide Id: " + msiPeptideId)
        } else {
          msiPeptideMatch.setPeptideId(msiPeptideId)
        }

        msiPeptideMatch.setRank(Integer.valueOf(peptideMatch.rank))

        msiPeptideMatch.setResultSet(msiResultSet) // msiResultSet must be in persistence context

        msiPeptideMatch.setScore(java.lang.Float.valueOf(peptideMatch.score))

        val msiScoringId = scoringRepo.getScoringIdForType(msiEm, peptideMatch.scoreType)

        if (msiScoringId == null) {
          throw new IllegalArgumentException("Scoring [" + peptideMatch.scoreType + "] NOT found in MSIdb")
        } else {
          msiPeptideMatch.setScoringId(msiScoringId.longValue)
        }

        val peptideMatchProperties = peptideMatch.properties
        if (peptideMatchProperties.isDefined) {
          msiPeptideMatch.setSerializedProperties(Json.generate(peptideMatchProperties.get))
        }

        if (peptideMatch.msQuery == null) {
          throw new IllegalArgumentException("MsQuery is mandatory in PeptideMatch")
        }

        val msiMsQuery = loadOrCreateMsQuery(storerContext, peptideMatch.msQuery, msiSearch) // msiSearch must be in persistence context

        msiPeptideMatch.setMsQuery(msiMsQuery) // msiMsQuery must be in persistence context
        msiMsQuery.addPeptideMatch(msiPeptideMatch) // Reverse association

        msiPeptideMatch.setCharge(msiMsQuery.getCharge)
        msiPeptideMatch.setExperimentalMoz(msiMsQuery.getMoz)

        /* Check associated best PeptideMatch */
        val bestOmPeptideMatchId = peptideMatch.getBestChildId

        val knownMsiBestChild = knownPeptideMatches.get(bestOmPeptideMatchId)

        val msiBestChild = if (knownMsiBestChild.isDefined) {
          knownMsiBestChild.get
        } else {

          if (bestOmPeptideMatchId > 0L) {
            val foundBestChild = msiEm.getReference(classOf[MsiPeptideMatch], bestOmPeptideMatchId) // Must exist in Msi Db if OM Id > 0

            knownPeptideMatches += bestOmPeptideMatchId -> foundBestChild

            foundBestChild
          } else {
            val bestChild = peptideMatch.bestChild

            if ((bestChild != null) && bestChild.isDefined) {
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

        logger.trace("Msi PeptideMatch {" + omPeptideMatchId + "} persisted")

        val generatedPK = msiPeptideMatch.getId // Try to retrieve persisted Primary Key

        if (generatedPK > 0L) {
          val msiPeptideMatchPK = generatedPK

          peptideMatch.id = msiPeptideMatchPK

          knownPeptideMatches += msiPeptideMatchPK -> msiPeptideMatch // Cache with MSI Primary Key
        } else {
          logger.warn("Cannot retrieve Primary Key of PeptideMatch " + omPeptideMatchId)
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
  def loadOrCreateMsQuery(storerContext: StorerContext,
                          msQuery: MsQuery,
                          msiSearch: MsiSearch): MsiMsQuery = {

    checkStorerContext(storerContext)

    if (msQuery == null) {
      throw new IllegalArgumentException("MsQuery is null")
    }

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
          logger.warn("MsQuery {" + omMsQueryId + "} has no associated MsiSearch")
        } else {
          msiMsQuery.setMsiSearch(msiSearch) // msiSearch must be in persistence context
        }

        val msQueryProperties = msQuery.properties
        if (msQueryProperties.isDefined) {
          msiMsQuery.setSerializedProperties(Json.generate(msQueryProperties.get))
        }

        if (msQuery.isInstanceOf[Ms2Query]) {
          val ms2Query = msQuery.asInstanceOf[Ms2Query]

          var omSpectrumId: Long = ms2Query.spectrumId

          /* Try to load Spectrum.id from knownSpectrumIdByTitle */
          val spectrumIdByTitle = storerContext.spectrumIdByTitle
          if ((spectrumIdByTitle != null) && !StringUtils.isEmpty(ms2Query.spectrumTitle)) {
            val knownSpectrumId = spectrumIdByTitle.get(ms2Query.spectrumTitle)

            if (knownSpectrumId.isDefined) {
              omSpectrumId = knownSpectrumId.get
            }

          }

          // TODO Spectrums should be persisted before RsStorer (with PeakList entity)
          if (omSpectrumId > 0L) {
            val msiSpectrum = msiEm.find(classOf[MsiSpectrum], omSpectrumId)

            if (msiSpectrum == null) {
              throw new IllegalArgumentException("Spectrum #" + omSpectrumId + " NOT found in MSIdb")
            } else {
              val spectrumTitle = msiSpectrum.getTitle

              if ((spectrumTitle != null) && spectrumTitle.equals(ms2Query.spectrumTitle)) {
                msiMsQuery.setSpectrum(msiSpectrum)
              } else {
                throw new IllegalArgumentException("Invalid Spectrum.title")
              }

            }

          } else {
            logger.warn("Invalid Spectrum Id: " + omSpectrumId)
          }

        } // End if (msQuery is a Ms2Query)

        msiEm.persist(msiMsQuery)

        knownMsQueries += omMsQueryId -> msiMsQuery

        logger.trace("Msi MsQuery {" + omMsQueryId + "} persisted")

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
  def createProteinMatch(storerContext: StorerContext,
                         proteinMatch: ProteinMatch,
                         msiResultSet: MsiResultSet): MsiProteinMatch = {

    checkStorerContext(storerContext)

    if (proteinMatch == null) {
      throw new IllegalArgumentException("ProteinMatch is null")
    }

    val omProteinMatchId = proteinMatch.id

    if (omProteinMatchId > 0L) {
      throw new UnsupportedOperationException("Updating existing ProteinMatch #" + omProteinMatchId + " is not supported")
    }

    if (msiResultSet == null) {
      throw new IllegalArgumentException("MsiResultSet is null")
    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    /* Create new MsiProteinMatch */
    val msiProteinMatch = new MsiProteinMatch()
    msiProteinMatch.setAccession(proteinMatch.accession)

    val omProteinId = proteinMatch.getProteinId

    if (omProteinId > 0L) {

      val msiBioSequence = loadOrCreateBioSequence(storerContext, omProteinId)
      if (msiBioSequence != null) {
        msiProteinMatch.setBioSequenceId(java.lang.Long.valueOf(msiBioSequence.getId))
      }

    } else {
      val optionalProtein = proteinMatch.protein

      if ((optionalProtein != null) && optionalProtein.isDefined) {
        val protein = optionalProtein.get

        logger.warn("Unknown Protein {" + omProteinId + "} sequence [" + protein.sequence + ']')
      }

    }

    msiProteinMatch.setIsLastBioSequence(proteinMatch.isLastBioSequence)
    msiProteinMatch.setCoverage(proteinMatch.coverage)
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
      val msiScoringId = scoringRepo.getScoringIdForType(msiEm, scoreType)

      if (msiScoringId == null) {
        throw new IllegalArgumentException("Scoring [" + scoreType + "] NOT found in Msi Db")
      } else {
        msiProteinMatch.setScoringId(msiScoringId.longValue)
      }

    }

    // TODO handle serializedProperties

    val omTaxonId = proteinMatch.taxonId

    if (omTaxonId != 0L) {
      msiProteinMatch.setTaxonId(java.lang.Long.valueOf(omTaxonId))
    }

    msiEm.persist(msiProteinMatch)
    logger.trace("Msi ProteinMatch {" + omProteinMatchId + "} persisted")

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

    if (proteinId <= 0L) {
      throw new IllegalArgumentException("Invalid proteinId")
    }

    val msiEm = storerContext.getMSIDbConnectionContext.getEntityManager

    val knownBioSequences = storerContext.getEntityCache(classOf[MsiBioSequence])

    val knownMsiBioSequence = knownBioSequences.get(proteinId)

    if (knownMsiBioSequence.isDefined) {
      knownMsiBioSequence.get
    } else {
      var msiBioSequence: MsiBioSequence = msiEm.find(classOf[MsiBioSequence], proteinId)

      if (msiBioSequence == null) {
        val pdiBioSequence = storerContext.getPDIDbConnectionContext.getEntityManager.find(classOf[PdiBioSequence], proteinId)

        if (pdiBioSequence == null) {
          throw new IllegalArgumentException("BioSequence #" + proteinId + " NOT found in PDIdb")
        } else {
          msiBioSequence = new MsiBioSequence(pdiBioSequence)

          msiEm.persist(msiBioSequence)
          logger.trace("Msi BioSequence #" + pdiBioSequence.getId + " persisted")
        }

      }

      knownBioSequences += proteinId -> msiBioSequence

      msiBioSequence
    }

  }

  /**
   * Persists a new SequenceMatch entity into Msi Db.
   *
   * @param sequenceMatch SequenceMatch object, must not be {{{null}}}.
   * @param msiProteinMatchId ProteinMatch Primary key, must be > 0 and denote an existing ProteinMatch entity in Msi Db (Msi transaction committed).
   * @param msiPeptides Map of already fetched Msi Peptide entities accessed by PeptideIdent, must not be {{{null}}}.
   * Peptide ids must be effective Msi Db Primary keys (Msi transaction committed).
   * @param knownPeptideMatches Map of already created Msi PeptideMatch entities accessed by OM Ids, must not be {{{null}}}.
   * PeptideMatch ids must be effective Msi Db Primary keys (Msi transaction committed).
   * @param msiResultSetId ResultSet Primary key, must be > 0 and denote an existing ResultSet entity in Msi Db (Msi transaction committed).
   */
  def createSequenceMatch(storerContext: StorerContext,
                          sequenceMatch: SequenceMatch,
                          msiProteinMatchId: Long,
                          msiResultSetId: Long): MsiSequenceMatch = {

    checkStorerContext(storerContext)

    if (sequenceMatch == null) {
      throw new IllegalArgumentException("SequenceMatch is null")
    }

    if (msiProteinMatchId <= 0L) {
      throw new IllegalArgumentException("Invalid Msi ProteinMatch Id")
    }

    if (msiResultSetId <= 0L) {
      throw new IllegalArgumentException("Invalid Msi ResultSet Id")
    }

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
    val msiPeptideId = retrieveMsiPeptideId(sequenceMatch.getPeptideId, sequenceMatch.peptide)

    if (msiPeptideId > 0L) {
      msiSequenceMatchPK.setPeptideId(msiPeptideId)
    } else {
      throw new IllegalArgumentException("Unknown MSIdb Peptide Id: " + msiPeptideId)
    }

    msiSequenceMatchPK.setStart(sequenceMatch.start)
    msiSequenceMatchPK.setStop(sequenceMatch.end)

    val msiSequenceMatch = new MsiSequenceMatch()
    msiSequenceMatch.setId(msiSequenceMatchPK)

    /* Retrieve best PeptideMatch Id from Msi */
    val msiPeptideMatchId = retrieveMsiPeptideMatchId(sequenceMatch.getBestPeptideMatchId)

    if (msiPeptideMatchId > 0L) {
      msiSequenceMatch.setBestPeptideMatchId(msiPeptideMatchId)
    } else {
      throw new IllegalArgumentException("Unknown MSIdb best PeptideMatch Id: " + msiPeptideMatchId)
    }

    msiSequenceMatch.setIsDecoy(sequenceMatch.isDecoy)
    msiSequenceMatch.setResidueAfter(scalaCharToCharacter(sequenceMatch.residueAfter))
    msiSequenceMatch.setResidueBefore(scalaCharToCharacter(sequenceMatch.residueBefore))
    msiSequenceMatch.setResultSetId(msiResultSetId)

    // TODO handle serializedProperties

    storerContext.getMSIDbConnectionContext.getEntityManager.persist(msiSequenceMatch)
    logger.trace("Msi SequenceMatch for ProteinMatch #" + msiProteinMatchId + " Peptide #" + msiPeptideId + " persisted")

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
  private def bindUsedPtm(storerContext: StorerContext,
                          ptmDefinition: PtmDefinition, isFixed: Boolean,
                          msiSearchSetting: MsiSearchSetting) {

    checkStorerContext(storerContext)

    assert(ptmDefinition != null, "PtmDefinition is null")

    assert(msiSearchSetting != null, "MsiSearchSetting is null")

    val msiUsedPtm = new MsiUsedPtm()
    msiUsedPtm.setIsFixed(isFixed)
    msiUsedPtm.setShortName(ptmDefinition.names.shortName)
    // TODO UsedPtm.type field should be removed from Msi Db schema

    val msiPtmSpecificity = loadOrCreatePtmSpecificity(storerContext, ptmDefinition)

    if (msiPtmSpecificity == null) {
      throw new IllegalArgumentException("Unknown PtmSpecificity [" + ptmDefinition.names.shortName + ']')
    } else {
      msiUsedPtm.setPtmSpecificity(msiPtmSpecificity)
      msiPtmSpecificity.addUsedPtm(msiUsedPtm) // Reverse association
    }

    msiUsedPtm.setSearchSetting(msiSearchSetting) // msiSearchSetting must be in persistence context
    msiSearchSetting.addUsedPtms(msiUsedPtm) // Reverse association

    storerContext.getMSIDbConnectionContext.getEntityManager.persist(msiUsedPtm)
    logger.trace("Msi UsedPtm Specificity #" + msiPtmSpecificity.getId + " for current SearchSetting persisted")
  }

  private def bindPeptidePtm(storerContext: StorerContext,
                             psPeptide: PsPeptide,
                             locatedPtm: LocatedPtm) {

    checkStorerContext(storerContext)

    assert(psPeptide != null, "PsPeptide is null")

    assert(locatedPtm != null, "LocatedPtm is null")

    val psEm = storerContext.getPSDbConnectionContext.getEntityManager

    val ptmDefinition = locatedPtm.definition

    val omSpecificityId = ptmDefinition.id // Immutable val here

    var psPtmSpecificity: PsPtmSpecificity = null

    if (omSpecificityId > 0L) {
      psPtmSpecificity = psEm.getReference(classOf[PsPtmSpecificity], omSpecificityId) // Must exist in Ps Db if OM Id > 0
    }

    if (psPtmSpecificity == null) {
      /* Try to load from Ps Db by name, location and residue */
      psPtmSpecificity = psPtmRepo.findPtmSpecificityForNameLocResidu(psEm, ptmDefinition.names.shortName, ptmDefinition.location,
        scalaCharToCharacter(ptmDefinition.residue))
    }

    if (psPtmSpecificity == null) {
      throw new IllegalArgumentException("PtmSpecificity [" + ptmDefinition.names.shortName + "] NOT found in PSdb")
    } else {
      val psPeptidePtm = new PsPeptidePtm()
      psPeptidePtm.setAverageMass(locatedPtm.averageMass)
      psPeptidePtm.setMonoMass(locatedPtm.monoMass)
      psPeptidePtm.setSeqPosition(locatedPtm.seqPosition)

      // TODO handle AtomLabel

      psPeptidePtm.setPeptide(psPeptide) // psPeptide must be in persistence context
      psPeptidePtm.setSpecificity(psPtmSpecificity)

      psEm.persist(psPeptide)
      logger.trace("PSdb PeptidePtm Specificity #" + psPtmSpecificity.getId + " Peptide sequence [" + psPeptide.getSequence + "] persisted")
    }

  }

  private def bindProteinMatchSeqDatabaseMap(storerContext: StorerContext,
                                             msiProteinMatchId: Long,
                                             seqDatabaseId: Long,
                                             msiResultSet: MsiResultSet) {

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
      logger.trace("Msi ProteinMatchSeqDatabase for ProteinMatch #" + msiProteinMatchId + " SeqDatabase #" + msiSeqDatabase.getId + " persisted")
    }

  }

}
