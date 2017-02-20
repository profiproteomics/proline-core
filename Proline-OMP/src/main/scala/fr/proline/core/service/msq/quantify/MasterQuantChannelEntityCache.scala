package fr.proline.core.service.msq.quantify

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.mzdb.MzDbReader
import fr.profi.util.collection._
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.config.ILabelFreeQuantConfig
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureMs2EventTable
import fr.proline.core.dal.tables.lcms.LcmsDbRawMapTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.LcMsScan
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.lcms.RawFile
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.provider.lcms.impl.SQLRunProvider
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.provider.msi.impl.SQLLazyResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLSpectrumProvider
import fr.proline.core.orm.msi.{ ResultSet => MsiResultSet }
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.orm.uds.{RawFile => UdsRawFile}
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.lcms.IRunProvider
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext

class MasterQuantChannelEntityCache(
  executionContext: IExecutionContext,
  udsMasterQuantChannel: MasterQuantitationChannel
) extends LazyLogging {
  
  // Instantiated fields
  protected val udsDbCtx = executionContext.getUDSDbConnectionContext
  protected val udsEm = udsDbCtx.getEntityManager
  protected val msiDbCtx = executionContext.getMSIDbConnectionContext
  protected val msiEm = msiDbCtx.getEntityManager
  protected val psDbCtx = executionContext.getPSDbConnectionContext
  protected lazy val lcmsDbCtx = executionContext.getLCMSDbConnectionContext
  
  protected lazy val lcMsRunProvider: IRunProvider = {
    executionContext match {
      case providerExecCtx: ProviderDecoratedExecutionContext => {
        val runProviderClass = classOf[IRunProvider]
        if (providerExecCtx.hasProvider(runProviderClass)) {
          providerExecCtx.getProvider(runProviderClass)
        } else {
          new SQLRunProvider(udsDbCtx, None)
        }
      }
      case _ => new SQLRunProvider(udsDbCtx, None)
    }
  }
  
  val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels.toList
  val quantChannelIds = udsQuantChannels.map { _.getId } toArray
  val quantChannelsCount = quantChannelIds.length
  
  val identRsmIds = udsQuantChannels.map { udsQuantChannel =>
    val qcId = udsQuantChannel.getId()
    val identRsmId = udsQuantChannel.getIdentResultSummaryId
    require(identRsmId != 0, "the quant_channel with id='" + qcId + "' is not assocciated with an identification result summary")

    identRsmId

  }.toList.distinct

  require(identRsmIds.length > 0, "result sets have to be validated first")

  val identRsIdByRsmId = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.RESULT_SET_ID) -> "WHERE "~ t.ID ~" IN ("~ identRsmIds.mkString(",") ~")"
      )
      msiEzDBC.select(sqlQuery) { r => r.nextLong -> r.nextLong } toLongMap()
    }
  }

  val msiIdentResultSets = {
    val identRsIds = identRsIdByRsmId.values.asJavaCollection
    msiEm.createQuery("FROM fr.proline.core.orm.msi.ResultSet WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.msi.ResultSet])
      .setParameter("ids", identRsIds).getResultList().toList
  }
  
  val identResultSummaries = {

    // Load the result summaries corresponding to the quant channels
    this.logger.info("loading result summaries...")

    // Instantiate a RSM provider
    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx,udsDbCtx)
    rsmProvider.getResultSummaries( identRsmIds, true)
    
  }

  // Retrieve corresponding peaklist ids
  // TODO: update the ORM definition so that peaklistId is available from msiSearch object    
  //protected lazy val identRsIdByPeaklistId = msiIdentResultSets.toLongMap { rs => rs.getMsiSearch.getPeaklist.getId -> rs.getId }
  //protected lazy val peaklistIds = this.identRsIdByPeaklistId.keys.toList
  //val peaklistIds = msiIdentResultSets map { _.getMsiSearch().getPeaklist().getId() }
  
  lazy val peaklistIdByIdentRsId = msiIdentResultSets.toLongMapWith { rs => rs.getId -> rs.getMsiSearch.getPeaklist.getId }

  lazy val runIdByRsmId = {
    Map() ++ udsQuantChannels.map { udsQC =>
      udsQC.getIdentResultSummaryId() -> udsQC.getRun().getId()
    }
  }
  
  lazy val runById: LongMap[LcMsRun] = this.getLcMsRuns.mapByLong(_.id)
  
  lazy val rawFileByPeaklistId: LongMap[RawFile] = udsQuantChannels.toList.toLongMapWith { udsQuantChannel =>
    val identRsId = identRsIdByRsmId(udsQuantChannel.getIdentResultSummaryId)
    peaklistIdByIdentRsId(identRsId) -> runById(udsQuantChannel.getRun.getId).rawFile
    //peaklistIdByIdentRsId(identRsId) -> udsQuantChannel.getRun().getRawFile()
  }
  
  lazy val ms2SpectrumDescriptors = {
    new Ms2SpectrumDescriptorProvider(executionContext).loadMs2SpectrumDescriptors(rawFileByPeaklistId)
  }
  
  lazy val spectrumIdByRsIdAndScanNumber = {
    
    val identRsIdByPeaklistId = msiIdentResultSets.toLongMapWith { rs => rs.getMsiSearch.getPeaklist.getId -> rs.getId }

    // Map spectrum id by scan number and result set id
    val spectrumIdMap = LongMap[LongMap[Long]]()

    for (spectrumDescriptor <- this.ms2SpectrumDescriptors) {

      require(spectrumDescriptor.firstScan > 0,"a first_scan id must be defined for MS2 spectrum id="+spectrumDescriptor.id)

      val identRsId = identRsIdByPeaklistId(spectrumDescriptor.peaklistId)
      val scanNumber = spectrumDescriptor.firstScan
      val spectrumId = spectrumDescriptor.id

      spectrumIdMap.getOrElseUpdate(identRsId, new LongMap[Long])(scanNumber) = spectrumId
    }

    spectrumIdMap
  }
  
  lazy val scanNumberBySpectrumId = {

    // Map spectrum id by scan number and result set id
    val scanNumberBySpectrumId = new LongMap[Int]

    for (spectrumDescriptor <- this.ms2SpectrumDescriptors) {
      require(spectrumDescriptor.firstScan > 0, "a scan id must be defined for each MS2 spectrum")

      val scanNumber = spectrumDescriptor.firstScan
      val spectrumId = spectrumDescriptor.id
      
      scanNumberBySpectrumId += spectrumId -> scanNumber
    }

    scanNumberBySpectrumId.result
  }
  
  private var peptideByRunIdAndScanNumber: LongMap[LongMap[Peptide]] = null
  private var psmByRunIdAndScanNumber: LongMap[LongMap[ArrayBuffer[PeptideMatch]]] = null
  
  def getPepAndPsmByRunIdAndScanNumber(mergedRsm: ResultSummary) : (LongMap[LongMap[Peptide]], LongMap[LongMap[ArrayBuffer[PeptideMatch]]]) = {
    if(peptideByRunIdAndScanNumber == null) {
      val validPepIdSet = mergedRsm.peptideInstances.withFilter(_.validatedProteinSetsCount > 0).map(_.peptideId).toSet
      
      val peptideMap = new collection.mutable.LongMap[LongMap[Peptide]]()
      val psmMap = new collection.mutable.LongMap[LongMap[ArrayBuffer[PeptideMatch]]]()
      
      for( rsm <- this.identResultSummaries ) {
        val runId = runIdByRsmId(rsm.id)
        val valPepMatchIds = rsm.peptideInstances.withFilter(_.validatedProteinSetsCount > 0).flatMap( _.getPeptideMatchIds )
        val pepMatchById = rsm.resultSet.get.getPeptideMatchById
  
        for (valPepMatchId <- valPepMatchIds) {
          val valPepMatch = pepMatchById(valPepMatchId)
          if (validPepIdSet.contains(valPepMatch.peptideId)) {
            val spectrumId = valPepMatch.getMs2Query.spectrumId
            val scanNumber = this.scanNumberBySpectrumId(spectrumId)
            peptideMap.getOrElseUpdate(runId, new LongMap[Peptide])(scanNumber) = valPepMatch.peptide
            psmMap.getOrElseUpdate(runId, new LongMap[ArrayBuffer[PeptideMatch]]).getOrElseUpdate(scanNumber, ArrayBuffer[PeptideMatch]()) += valPepMatch
          }
        }
      }
      peptideByRunIdAndScanNumber = peptideMap
      psmByRunIdAndScanNumber = psmMap
    }
    
    (peptideByRunIdAndScanNumber, psmByRunIdAndScanNumber)
  }
  
  // TODO: load scan sequences here ???
  def getLcMsRuns(): Array[LcMsRun] = {
    
   // Retrieve master quant channels sorted by their number
    val sortedQuantChannels = udsMasterQuantChannel.getQuantitationChannels()

    // Retrieve run ids
    val runIds = sortedQuantChannels.map(_.getRun.getId).toList.distinct

    // Load the LC-MS runs
    lcMsRunProvider.getRuns(runIds, loadScanSequence = false)
  }
  
  // TODO: move to SQLScanSequenceProvider provider
  def getLcMsScans(rawMapIds: Array[Long]): Array[LcMsScan] = {
    this.logger.info("loading scan headers from LCMSdb...")
    
    val lcmsRunIds = {
      DoJDBCReturningWork.withEzDBC(lcmsDbCtx) { lcmsEzDBC =>
        lcmsEzDBC.selectLongs( new SelectQueryBuilder1(LcmsDbRawMapTable).mkSelectQuery( (t,c) =>
          List(t.SCAN_SEQUENCE_ID) -> "WHERE "~ t.ID ~" IN("~ rawMapIds.mkString(",") ~")"
        ) )
      }
    }
    
    val scanSeqProvider = new SQLScanSequenceProvider(lcmsDbCtx)
    scanSeqProvider.getScans(lcmsRunIds)
    
    /*DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { lcmsEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(LcmsDbScanTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.INITIAL_ID,t.CYCLE,t.TIME)
        -> "WHERE "~ t.MS_LEVEL ~" = 2 AND "~ t.RUN_ID ~" IN("~ this.lcmsRunIds.mkString(",") ~")"
      )
      lcmsEzDBC.selectAllRecordsAsMaps(sqlQuery)    
    })*/
    
  }
  
  // TODO: move to LcmsDbHelper
  def getMs2ScanNumbersByFtId(lcmsScans: Array[LcMsScan], rawMapIds: Array[Long]): LongMap[Array[Int]] = {
    
    /*val ms2ScanNumberById = ms2ScanHeaderRecords.map { r =>
      toLong(r("id")) -> r("initial_id").asInstanceOf[Int]
    } toMap*/
    val ms2ScanNumberById = lcmsScans.toLongMapWith( s => s.id -> s.initialId )

    val transientRawMapIdsCount = rawMapIds.count( _ <= 0 )
    require( transientRawMapIdsCount == 0, "raw map ids must be positive (persisted maps)" )
    
    this.logger.info("loading MS2 scans/features map...")
    val ms2ScanNumbersByFtId = new LongMap[ArrayBuffer[Int]]

    DoJDBCWork.withEzDBC(lcmsDbCtx) { lcmsEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(LcmsDbFeatureMs2EventTable).mkSelectQuery( (t,c) =>
        List(t.FEATURE_ID,t.MS2_EVENT_ID) -> "WHERE "~ t.RAW_MAP_ID ~" IN("~ rawMapIds.mkString(",") ~")"
      )
      
      lcmsEzDBC.selectAndProcess(sqlQuery) { r =>
        val (featureId, ms2ScanId) = (r.nextLong, r.nextLong)
        val ms2ScanNumber = ms2ScanNumberById(ms2ScanId)
        ms2ScanNumbersByFtId.getOrElseUpdate(featureId, new ArrayBuffer[Int]) += ms2ScanNumber
      }
    }

    ms2ScanNumbersByFtId.map { case (k,v) => (k, v.toArray) }
  }
  
}

class Ms2SpectrumDescriptorProvider(
  executionContext: IExecutionContext
) extends LazyLogging {
  
  protected val msiDbCtx = executionContext.getMSIDbConnectionContext
  protected val spectrumProvider = new SQLSpectrumProvider(msiDbCtx)
  
  /*def loadMs2SpectrumHeaders(): Array[Map[String,Any]] = {

    // Load MS2 spectrum headers
    this.logger.info("loading MS2 spectrum headers...")
    
    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.PRECURSOR_MOZ,t.FIRST_CYCLE,t.FIRST_SCAN,t.FIRST_TIME,t.PEAKLIST_ID)
        -> "WHERE "~ t.PEAKLIST_ID ~" IN("~ peaklistIds.mkString(",") ~")"
      )
      msiEzDBC.selectAllRecordsAsMaps(sqlQuery)
    })

  }*/
  def loadMs2SpectrumDescriptors(rawFileByPeaklistId: LongMap[RawFile]): Array[Spectrum] = {
    
    val pklIds = rawFileByPeaklistId.keys.toList

    // Load MS2 spectrum headers
    this.logger.info("loading MS2 spectrum headers...")
    
    val tmpMs2SDs = spectrumProvider.getPeaklistsSpectra(pklIds, loadPeaks = false)
    
    val fixedMs2SHs = try {
      // Check that spectrum.first_scan column is filled
      this._fixMs2SpectrumDescriptors(tmpMs2SDs, rawFileByPeaklistId)
    } catch {
      case e: Exception => {
        logger.error("Error during update of spectrum.first_scan column", e)
        throw e
      }
    }
    
    fixedMs2SHs
  }
  
  private def _fixMs2SpectrumDescriptors(ms2SHs: Array[Spectrum], rawFileByPeaklistId: LongMap[RawFile]): Array[Spectrum] = {

    logger.info("Checking spectra first scan id property")
    
    // Create a mapping between cycles and scan ids
    val incompleteShByPklIdAndSpecId = new LongMap[LongMap[Spectrum]]()
    
    for (sh <- ms2SHs) {

      if( sh.firstScan == 0 ) {
        val shId = sh.id

        require(
          sh.firstCycle > 0 || sh.firstTime > 0,
          "A scan id, a cycle number or a retention time must be defined for MS2 spectrum id="+shId
        )
        
        val shCycle = sh.firstCycle
        val shTime = sh.firstTime
        
        val peaklistId = sh.peaklistId
        val incompleteShBySpecId = incompleteShByPklIdAndSpecId.getOrElseUpdate(peaklistId, new LongMap[Spectrum]())

        incompleteShBySpecId += shId -> sh
      }
    }
    
    // If all spectra have a first scan id => return the provided ms2SHs
    if( incompleteShByPklIdAndSpecId.isEmpty ) return ms2SHs
    // If we have some missing first scan ids => try to retrieve them
    else {
      logger.warn("Spectrum table has missing first scan ids and will be now updated, cross fingers...")
      
      // Create some mappings
      val precMZBySpecId = ms2SHs.view.map { ms2Sh => ms2Sh.id -> ms2Sh.precursorMoz } toMap
      val scanIdBySpecId = new LongMap[Int]()
      
      // Iterate over quant channels to update incomplete spectra headers
      for( (peaklistId,rawFile) <- rawFileByPeaklistId ) {
        
        val incompleteShBySpecIdOpt = incompleteShByPklIdAndSpecId.get(peaklistId)
        if( incompleteShBySpecIdOpt.isDefined ) {
          val incompleteShBySpecId = incompleteShBySpecIdOpt.get
          
          val mzDbFilePathOpt = rawFile.getMzdbFilePath()
          assert( mzDbFilePathOpt.isDefined, s"mzDB file is not linked to the raw file '${rawFile.name}' in the UDSdb")
          
          val mzDbFilePath = mzDbFilePathOpt.get
          val mzDbReader = new MzDbReader(mzDbFilePath, true)
          
          try {
            val mzDbScanHeaders = mzDbReader.getMs2SpectrumHeaders()
            val mzDbScanHeadersByCycle = mzDbScanHeaders.groupBy(_.getCycle())
            
            for( (specId,sh) <- incompleteShBySpecId ) {
              
              // Try to retrieve the scan id by using the retention time information
              val matchingMzDbSh = if( sh.firstTime > 0 ) {
                // FIXME: how to know if the time has been stored in minutes ???
                val firstTimeInMin = sh.firstTime
                val firstTimeInSec = firstTimeInMin * 60
                val mzDbSH = mzDbReader.getSpectrumHeaderForTime(firstTimeInSec, 2)
                
                require(
                  math.abs(mzDbSH.getTime - firstTimeInSec) < 1,
                  s"can't determine the first scan id of spectrum with id=$specId (the retention time seems to be wrong)"
                )
                
                mzDbSH
              }
              // Try to retrieve the scan id by using the cycle information
              else if ( sh.firstCycle > 0 ) {
                val firstCycle = sh.firstCycle
                require(
                  mzDbScanHeadersByCycle.contains(firstCycle),
                  s"can't find cycle $firstCycle in mzDB file: " + mzDbFilePath
                )
                
                val refPrecMz = precMZBySpecId(specId)
                val mzDbSHsInCurCycle = mzDbScanHeadersByCycle(firstCycle)
                val closestSH = mzDbSHsInCurCycle.minBy { mzDbSH => 
                  math.abs(mzDbSH.getPrecursorMz - refPrecMz)
                }
                
                require(
                  math.abs(closestSH.getPrecursorMz - refPrecMz) < 10,
                  s"can't determine the first scan id of spectrum with id=$specId (the precursor m/z value seems to be wrong)"
                )
                
                closestSH
              }
              // Else it is not possible to perform the
              else {
                throw new Exception(s"can't determine the first scan id of this spectrum with id=$specId (not enough meta data)")
              }
              
              // Map the mzDb scan id by the MSIdb spectrum id
              scanIdBySpecId += specId -> matchingMzDbSh.getInitialId()

            }
          } finally {
            mzDbReader.close()
          }
        }
      }
      
      require( scanIdBySpecId.isEmpty == false, "scanIdBySpecId should not be empty")
      
      val specCols = MsiDbSpectrumTable.columns
      
      // Persist the new scan ids into the MSIdb
      DoJDBCReturningWork.withEzDBC( msiDbCtx) { msiEzDBC =>
        
        val updateSqlQuery = s"UPDATE ${MsiDbSpectrumTable.name} SET ${specCols.FIRST_SCAN} = ? WHERE ${specCols.ID} = ?"

        msiEzDBC.executeInBatch(updateSqlQuery) { stmt =>
          for( (specId,scanId) <- scanIdBySpecId ) {
            stmt.executeWith(scanId,specId)
          }
        }
      }
      
      // Update the provided ms2SHs
      for (sh <- ms2SHs) {
        val shId = sh.id
        if (scanIdBySpecId.contains(shId)){
          sh.firstScan = scanIdBySpecId(shId)
        }
      }
      
      ms2SHs
      
    } // end of if incompleteShByPklIdAndSpecId.isEmpty
  }


}