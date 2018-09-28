package fr.proline.core.service.lcms.io

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.control.Breaks._

import com.almworks.sqlite4java.SQLiteConnection
import com.github.davidmoten.rtree._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import fr.profi.chemistry.model.MolecularConstants
import fr.profi.jdbc.easy._
import fr.profi.ms.algo.IsotopePatternEstimator
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.algo.PeakelsPatternPredictor
import fr.profi.mzdb.io.reader.provider.RunSliceDataProvider
import fr.profi.mzdb.peakeldb.PeakelDbHelper
import fr.profi.mzdb.peakeldb.io.PeakelDbReader
import fr.profi.mzdb.peakeldb.io.PeakelDbWriter
import fr.profi.mzdb.model.{ Feature => MzDbFeature, Peakel => MzDbPeakel, SpectrumHeader }
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.mzdb.model.PutativeFeature
import fr.profi.mzdb.util.ms.MsUtils
import fr.profi.util.collection._
import fr.profi.util.metrics.Metric

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.om.model.msq.ExperimentalDesignSetup
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature }
import fr.proline.core.om.model.msi.{ Peptide, PeptideMatch }
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms._
import fr.proline.core.om.storer.lcms.impl.PgPeakelWriter
import fr.proline.core.om.storer.lcms.impl.SQLPeakelWriter
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer
import fr.proline.core.service.lcms.AlignMapSet
import fr.proline.core.service.lcms.CreateMapSet
import fr.proline.core.service.lcms.ILcMsService

import rx.lang.scala.Observable
import rx.lang.scala.Scheduler
import rx.lang.scala.schedulers
import rx.lang.scala.Subject
import rx.lang.scala.subjects.PublishSubject


/**
 * @author David Bouyssie
 *
 */

class ExtractMapSet(
  val lcmsDbCtx: LcMsDbConnectionContext,
  val mapSetName: String,
  val lcMsRuns: Seq[LcMsRun],
  val masterQcExpDesign: ExperimentalDesignSetup,
  val quantConfig: ILcMsQuantConfig,
  val peptideByRunIdAndScanNumber: Option[LongMap[LongMap[Peptide]]] = None, // sequence data may or may not be provided
  val peptideMatchByRunIdAndScanNumber: Option[LongMap[LongMap[ArrayBuffer[PeptideMatch]]]] = None
) extends ILcMsService with LazyLogging {
  
  // Do some requirements
  require(quantConfig.extractionParams.mozTolUnit matches "(?i)PPM")
  
  require(
    lcMsRuns.map(_.number).filter(_ > 0).distinct.length == lcMsRuns.length,
    "Invalid LC-MS run numbers: numbers should distinct and strictly positive"
  )
  
  protected val sortedLcMsRuns = lcMsRuns.sortBy(_.number)
  protected val scanSeqProvider = new SQLScanSequenceProvider(lcmsDbCtx)
  protected val normalizationMethod = quantConfig.normalizationMethod

  protected val pps = new PeakPickingSoftware(
    id = -1,
    name = "Proline",
    version = new fr.proline.core.service.Version().getVersion.split("-").head,
    algorithm = "ExtractMapSet"
  )

  var extractedMapSet: MapSet = null

  def runService(): Boolean = {

    val mapCount = lcMsRuns.length
    val nonNullLcMsRunCount = lcMsRuns.count(_ != null)
    this.logger.debug("LC-MS runs count = " + mapCount + " and non-null LC-MS runs count = " + nonNullLcMsRunCount)
    require(mapCount == nonNullLcMsRunCount, "the quantitation config contains null LC-MS runs")

    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if (!wasInTransaction) lcmsDbCtx.beginTransaction()

    val tmpMapSetId = MapSet.generateNewId()

    val mzDbFileByLcMsRunId = new LongMap[File](mapCount)
    for (lcmsRun <- sortedLcMsRuns) {

      val rawFile = lcmsRun.rawFile
      val mzDbFilePath = rawFile.getMzdbFilePath.get
      val mzDbFile = new File(mzDbFilePath)

      if (lcmsRun.scanSequence.isEmpty) {
        // Retrieve the corresponding LC-MS run
        // Or store it in the database if it doesn't exist
        lcmsRun.scanSequence = Some(this._fetchOrStoreScanSequence(lcmsRun, mzDbFile))
      }

      mzDbFileByLcMsRunId += lcmsRun.id -> mzDbFile
    }
    
    // New way of map set creation (concerted maps extraction)
    val (finalMapSet, alnResult) = if (quantConfig.detectionMethodName == DetectionMethod.DETECT_PEAKELS) {
      val extractor = new PeakelsDetector(lcmsDbCtx, mapSetName, lcMsRuns, masterQcExpDesign, quantConfig, peptideMatchByRunIdAndScanNumber.get)
      extractor.detectMapSetFromPeakels(mzDbFileByLcMsRunId, tmpMapSetId)
    } else {
      val extractor = new FeaturesDetector(lcmsDbCtx, mapSetName, lcMsRuns, quantConfig, peptideByRunIdAndScanNumber)
      extractor.extractMapSet(mzDbFileByLcMsRunId, tmpMapSetId)
    }

    // End of finalMapSet assignment block
    
    // Update peakel.feature_count column using an hand-made SQL query
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      val rawMapIds = finalMapSet.childMaps.flatMap(_.getRawMapIds())
      val whereClause = rawMapIds.map(id => s"map_id=$id").mkString(" OR ")
      val subQuery = "SELECT count(feature_id) AS ft_count, peakel_id " +
      s"FROM feature_peakel_item WHERE ($whereClause) GROUP BY peakel_id"
      
      logger.debug("Calculating peakel.feature_count column using SQL query:\n"+subQuery)
      
      val updateQuery = "UPDATE peakel SET feature_count = ? WHERE id = ?"
      ezDBC.executePrepared(updateQuery) { stmt =>
        ezDBC.selectAndProcess(subQuery) { r =>
          val featureCount = r.nextInt
          // Only update peakels having a feature_count different than default value (1)
          if (featureCount > 1) {
            stmt.executeWith(featureCount,r.nextLong)
          }
        }
      }
      
      logger.debug("Column peakel.feature_count successfully updated!")
    }
    
    // Update processed map id of each feature
    // FIXME: DBO => why is it sill necessary now (I mean after the introduction of detectPeakels mode) ???
    for (x2ProcessedMap <- finalMapSet.childMaps; ft <- x2ProcessedMap.features) {
      ft.relations.processedMapId = x2ProcessedMap.id

      if (ft.isCluster) {
        for (subFt <- ft.subFeatures) {
          subFt.relations.processedMapId = x2ProcessedMap.id
        }
      }
    }

    // --- Update and store the map alignment using processed maps with persisted ids ---
    // do not re-compute the alignments because they could be different with the tmpAlignemnt 
    AlignMapSet(lcmsDbCtx, finalMapSet, alnResult)

    // --- Normalize the processed maps ---
    if (normalizationMethod.isDefined && finalMapSet.childMaps.length > 1) {

      // Update the normalized intensities
      logger.info("Normalizing maps...")
      MapSetNormalizer(normalizationMethod.get).normalizeFeaturesIntensity(finalMapSet)

      // Re-build master map features using best child
      logger.info("Re-build master map features using best child...")
      finalMapSet.rebuildMasterFeaturesUsingBestChild()
    }

    // --- Store the processed maps features ---

    // Instantiate a processed map storer
    val processedMapStorer = ProcessedMapStorer(lcmsDbCtx)

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      for (processedMap <- finalMapSet.childMaps) {
        // Store the map
        logger.info("Storing the processed map...")
        processedMapStorer.storeProcessedMap(processedMap)
      }
    }

    // --- Store the master map ---
    logger.info("Saving the master map...")
    val masterMapStorer = MasterMapStorer(lcmsDbCtx)
    masterMapStorer.storeMasterMap(finalMapSet.masterMap)

    // Commit transaction if it was initiated locally
    if (!wasInTransaction) lcmsDbCtx.commitTransaction()

    logger.info(finalMapSet.masterMap.features.length + " master features have been created")

    this.extractedMapSet = finalMapSet

    // Check all features are persisted in childMaps
    for (x2ProcessedMap <- finalMapSet.childMaps; ft <- x2ProcessedMap.features) {
      require(ft.id > 0, "feature should be persisted")
      if (ft.isCluster) {
        for (subFt <- ft.subFeatures) {
          require(subFt.id > 0, "sub-feature should be persisted")
        }
      }
    }

    // Check all features are persisted in masterMap
    for (mft <- finalMapSet.masterMap.features) {
      require(mft.id > 0, "master feature should be persisted")
      for (ft <- mft.children) {
        require(ft.id > 0, "feature should be persisted")
      }
    }

    true
  }
  

  private def _fetchOrStoreScanSequence(lcmsRun: LcMsRun, mzDbFile: File): LcMsScanSequence = {

    val mzDbFileDir = mzDbFile.getParent()
    val mzDbFileName = mzDbFile.getName()
    // FIXME: it should be retrieved from the mzDB file meta-data
    val rawFileIdentifier = mzDbFileName.split("\\.").head

    // Check if the scan sequence already exists
    val scanSeqOpt = scanSeqProvider.getScanSequence(lcmsRun.id)

    if (scanSeqOpt.isDefined) scanSeqOpt.get
    else {

      val mzDb = new MzDbReader(mzDbFile, true)
      val mzDbScans = mzDb.getSpectrumHeaders()
      val scans = mzDbScans.map { mzDbScan =>

        val precMz = mzDbScan.getPrecursorMz
        val precCharge = mzDbScan.getPrecursorCharge

        new LcMsScan(
          id = LcMsScan.generateNewId(),
          initialId = mzDbScan.getInitialId,
          cycle = mzDbScan.getCycle,
          time = mzDbScan.getTime,
          msLevel = mzDbScan.getMsLevel,
          tic = mzDbScan.getTIC,
          basePeakMoz = mzDbScan.getBasePeakMz,
          basePeakIntensity = mzDbScan.getBasePeakIntensity,
          runId = lcmsRun.id,
          precursorMoz = if (precMz > 0) Some(precMz) else None,
          precursorCharge = if (precCharge > 0) Some(precCharge) else None
        )
      } sortBy(_.time) // FIXME: why scans are not sorted after call to mzDb.getScanHeaders ???

      val ms1ScansCount = scans.count(_.msLevel == 1)
      val ms2ScansCount = scans.count(_.msLevel == 2)

      val scanSeq = new LcMsScanSequence(
        runId = lcmsRun.id,
        rawFileIdentifier = rawFileIdentifier,
        minIntensity = 0.0, // TODO: compute this value ???
        maxIntensity = 0.0, // TODO: compute this value ???
        ms1ScansCount = ms1ScansCount,
        ms2ScansCount = ms2ScansCount,
        scans = scans
      )

      val scanSeqStorer = new SQLScanSequenceStorer(lcmsDbCtx)
      scanSeqStorer.storeScanSequence(scanSeq)

      // Close the mzDB file
      mzDb.close()

      scanSeq
    }

  }

  class LcMsMapDetectorEntityCache(
    val lcMsRuns: Seq[LcMsRun],
    val mzDbFileByLcMsRunId: LongMap[File],
    val mapSetId: Long
  ) {
    private type PeakelRTree = RTree[java.lang.Integer,geometry.Point]
    
    val runsCount = lcMsRuns.length
    
    // Create some immutable maps => they don't need to be synchronized
    private val m_metricsByRunId = new LongMap[Metric](runsCount)
    private val m_peakelIdByMzDbPeakelIdByRunId = new LongMap[LongMap[Long]](runsCount) // contains only assigned peakels
    private val m_mzDbPeakelIdsByPeptideAndChargeByRunId = new LongMap[HashMap[(Peptide, Int), ArrayBuffer[Int]]](runsCount)
    for (lcMsRun <- lcMsRuns)  {
      m_metricsByRunId += lcMsRun.id -> new Metric("LCMSRun_"+lcMsRun.id)
      m_peakelIdByMzDbPeakelIdByRunId += (lcMsRun.id, new LongMap[Long]())
      m_mzDbPeakelIdsByPeptideAndChargeByRunId += (lcMsRun.id -> new HashMap[(Peptide, Int), ArrayBuffer[Int]]())
    }
    val metricsByRunId = m_metricsByRunId.toMap
    val peakelIdByMzDbPeakelIdByRunId = m_peakelIdByMzDbPeakelIdByRunId.toMap
    val mzDbPeakelIdsByPeptideAndChargeByRunId = m_mzDbPeakelIdsByPeptideAndChargeByRunId.toMap
    
    // Create some mutable maps => they need to be synchronized
    private val peakelFileByRunId_lock = new Object()
    private val peakelFileByRunId = new LongMap[File]()
    
    def getPeakelFile(runId: Long): Option[File] = peakelFileByRunId.get(runId)
    def addPeakelFile(runId: Long, peakelFile: File) {
      peakelFileByRunId_lock.synchronized {
        peakelFileByRunId += runId -> peakelFile
      }
    }
    
    private val rTreeByRunId_lock = new Object()
    private val rTreeByRunId = new LongMap[PeakelRTree](runsCount)

    def getRTree(lcMsRunId: Long): Option[PeakelRTree] = rTreeByRunId.get(lcMsRunId)
    def addRTree(runId: Long, rTree: PeakelRTree) {
      rTreeByRunId_lock.synchronized {
        rTreeByRunId += runId -> rTree
      }
    }
    
    private val featureTuples_lock = new Object()
    val featureTuples = new ArrayBuffer[(Feature, Peptide, LcMsRun)]()
    
    def addFeatureTuple(featureTuple: (Feature, Peptide, LcMsRun)) {
      featureTuples_lock.synchronized {
        featureTuples += featureTuple
      }
    }
    
    private val conflictingPeptidesMap_lock = new Object()
    val conflictingPeptidesMap = new HashMap[(Peptide, Int), ArrayBuffer[Peptide]]()

    def getConflictedPeptides(peptideAndCharge: (Peptide, Int)): Option[ArrayBuffer[Peptide]] = {
      conflictingPeptidesMap.get(peptideAndCharge)
    }
    def addConflictedPeptides(peptideAndCharge: (Peptide, Int), peptides: Seq[Peptide]) {
      conflictingPeptidesMap_lock.synchronized {
        conflictingPeptidesMap.getOrElseUpdate(peptideAndCharge, ArrayBuffer[Peptide]()) ++= peptides
      }
    }
  }
    
   case class PeakelMatch(
    peakel: MzDbPeakel,
    peptide: Peptide,
    spectrumHeader: SpectrumHeader,
    charge: Int
  )

}

