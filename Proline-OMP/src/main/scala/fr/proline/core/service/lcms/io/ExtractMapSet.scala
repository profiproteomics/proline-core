package fr.proline.core.service.lcms.io

import java.io.File
import java.util.Arrays
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap
import scala.collection.parallel._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.Breaks._

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import com.github.davidmoten.rtree._
import com.typesafe.scalalogging.LazyLogging

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import fr.profi.chemistry.model.MolecularConstants
import fr.profi.jdbc.easy._
import fr.profi.ms.algo.IsotopePatternEstimator
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.ms.model.TheoreticalIsotopePattern
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.provider.RunSliceDataProvider
import fr.profi.mzdb.model.{ Feature => MzDbFeature, Peakel => MzDbPeakel, SpectrumHeader }
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
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature }
import fr.proline.core.om.model.msi.{ Peptide, PeptideMatch }
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms._
import fr.proline.core.om.storer.lcms.impl.PgPeakelWriter
import fr.proline.core.om.storer.lcms.impl.SQLScanSequenceStorer
import fr.proline.core.service.lcms.AlignMapSet
import fr.proline.core.service.lcms.CreateMapSet
import fr.proline.core.service.lcms.ILcMsService

import rx.lang.scala.Observable
import rx.lang.scala.JavaConversions.toJavaObservable
import rx.lang.scala.JavaConversions.toScalaObservable
import rx.lang.scala.Scheduler
import rx.lang.scala.schedulers
import rx.lang.scala.Subject
import rx.lang.scala.subjects.PublishSubject
import fr.profi.mzdb.io.peakels.PeakelDbWriter
import fr.profi.mzdb.io.peakels.PeakelDbReader
import fr.profi.mzdb.algo.PeakelCorrelationScorer
import fr.proline.core.algo.msq.SignalBasedMapSetExtractor
import fr.proline.core.algo.msq.HybridMapSetExtractor

/**
 * @author David Bouyssie
 *
 */


class ExtractMapSet(
  val lcmsDbCtx: LcMsDbConnectionContext,
  val mapSetName: String,
  val lcMsRuns: Seq[LcMsRun],
  val quantConfig: ILcMsQuantConfig,
  val peptideByRunIdAndScanNumber: Option[LongMap[LongMap[Peptide]]] = None, // sequence data may or may not be provided
  val peptideMatchByRunIdAndScanNumber: Option[LongMap[LongMap[ArrayBuffer[PeptideMatch]]]] = None
) extends ILcMsService with LazyLogging {

  // Do some requirements
  require(quantConfig.extractionParams.mozTolUnit matches "(?i)PPM")

  protected val normalizationMethod = quantConfig.normalizationMethod
  
  protected val lcmsDbHelper = new LcmsDbHelper(lcmsDbCtx)
  protected val scanSeqProvider = new SQLScanSequenceProvider(lcmsDbCtx)


  // FIXME: generate new id and store the pps
  protected val pps = new PeakPickingSoftware(
    id = 1,
    name = "Proline",
    version = "1.1.0", // TODO: retrieve the version of OMP dynamically
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

    val mzDbFileByLcMsRunId = new LongMap[File](lcMsRuns.length)
    for (lcmsRun <- lcMsRuns) {

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
    val (finalMapSet, alnResult) = if (quantConfig.detectPeakels) {
      val extractor = new HybridMapSetExtractor(lcmsDbCtx, mapSetName, lcMsRuns, quantConfig, peptideMatchByRunIdAndScanNumber.get)
      extractor.extractMapSet(lcMsRuns, mzDbFileByLcMsRunId, tmpMapSetId)
    } else {
      val extractor = new SignalBasedMapSetExtractor(lcmsDbCtx, mapSetName, lcMsRuns, quantConfig, peptideByRunIdAndScanNumber.get)
      extractor.extractMapSet(lcMsRuns, mzDbFileByLcMsRunId, tmpMapSetId)
    }

    // End of finalMapSet assignment block
    
    // Update peakel.feature_count column using an hand-made SQL query
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      val rawMapIds = finalMapSet.childMaps.flatMap(_.getRawMapIds())
      val whereClause = rawMapIds.map(id => s"map_id=$id").mkString(" OR ")
      val subQuery = "SELECT peakel_id, count(feature_id) AS ft_count " +
      s"FROM feature_peakel_item WHERE ($whereClause) GROUP BY peakel_id";
      
      // Note: update only the feature_count when it is greater than one (default value)
      // TODO: evaluate if it is faster to load the values and then to update the peakels using their ids
      val sqlQuery = "UPDATE peakel SET feature_count = peakel_ft_counts.ft_count "+
      s"FROM ($subQuery) AS peakel_ft_counts WHERE peakel.id = peakel_ft_counts.peakel_id AND peakel_ft_counts.ft_count > 1"

      logger.debug("Update peakel.feature_count column using SQL query:\n"+sqlQuery)
      ezDBC.execute(sqlQuery)
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
    //AlignMapSet(lcmsDbCtx, finalMapSet, alnMethodName, alnParams)
   
    // CBY: commented = could not find usage of this value .... ??
    //val finalAlnResult = mapAligner.computeMapAlignments(finalMapSet.childMaps, alnParams)

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
    //val scanSeqId = lcmsDbHelper.getScanSequenceIdForRawFileName(rawFileName)
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
        instrument = lcmsRun.rawFile.instrument,
        scans = scans
      )

      val scanSeqStorer = new SQLScanSequenceStorer(lcmsDbCtx)
      scanSeqStorer.storeScanSequence(scanSeq)

      // Close the mzDB file
      mzDb.close()

      scanSeq
    }

  }



}

