package fr.proline.core.service.lcms.io

import java.io.File

import com.typesafe.scalalogging.LazyLogging
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.provider.RunSliceDataProvider
import fr.profi.mzdb.model.{PeakelDataMatrix, PutativeFeature, Feature => MzDbFeature, Peakel => MzDbPeakel}
import fr.profi.util.collection._
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config._
import fr.proline.core.om.model.lcms.{Feature => LcMsFeature, _}
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.storer.lcms._
import fr.proline.core.service.lcms.CreateMapSet
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import scala.collection.mutable.{ArrayBuffer, HashMap, LongMap}


abstract class AbstractMapSetDetector (
  val lcmsDbCtx: LcMsDbConnectionContext,
  val mapSetName: String,
  val lcMsRuns: Seq[LcMsRun],
  val quantConfig: ILcMsQuantConfig
) extends LazyLogging {

  protected val sortedLcMsRuns = lcMsRuns.sortBy(_.number)
  protected val mozTolPPM = quantConfig.extractionParams.mozTol.toFloat
  protected val clusteringParams = quantConfig.clusteringParams
  protected val alignmentConfig = quantConfig.alignmentConfig
  protected val crossAssignmentConfig = quantConfig.crossAssignmentConfig

  // Instantiate a raw map storer and a map aligner
  protected val rawMapStorer = RawMapStorer(lcmsDbCtx)
  protected val mapAligner = if (alignmentConfig.isDefined) Some(LcmsMapAligner(alignmentConfig.get.methodName)) else None

  protected def _mzDbPeakelToLcMsPeakel(
    mzDbPeakel: MzDbPeakel,
    lcMsPeakelIdOpt: Option[Long],
    rawMapId: Long,
    scanSeq: LcMsScanSequence
  ): Peakel = {
    
    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    
    // Create the peakel data matrix
    val peakelDataMatrix = new PeakelDataMatrix(
      // Convert mzDB scan IDs into LCMSdb scan ids (same warning as above)
      spectrumIds = mzDbPeakel.spectrumIds.map( sid => lcmsScanIdByInitialId(sid.toInt) ),
      elutionTimes = mzDbPeakel.elutionTimes,
      mzValues = mzDbPeakel.mzValues,
      intensityValues = mzDbPeakel.intensityValues
    )

    val lcmsPeakel = Peakel(
      id = lcMsPeakelIdOpt.getOrElse(Peakel.generateNewId),
      moz = mzDbPeakel.getMz,
      elutionTime = mzDbPeakel.getElutionTime(),
      //apexIntensity = mzDbPeakel.getApexIntensity(), now lazilly computed
      area = mzDbPeakel.area,
      duration = mzDbPeakel.calcDuration,
      //fwhm = Some( mzDbPeakel.fwhm ),
      isOverlapping = false, // FIXME: determine this value
      featuresCount = 1,
      dataMatrix = peakelDataMatrix,
      // FIXME: scanId and scanInitialId may be different in future mzDB configurations
      //firstScanId = lcmsScanIdByInitialId(peakelScanInitialIds.head),
      //lastScanId = lcmsScanIdByInitialId(peakelScanInitialIds.last),
      //apexScanId = lcmsScanIdByInitialId(mzDbPeakel.getApexScanInitialId),
      rawMapId = rawMapId
    )

    lcmsPeakel
  }
  
  protected def _mzDbFeatureToLcMsFeature(
    mzDbFt: MzDbFeature,
    putativeFeature: Option[PutativeFeature],
    rawMapId: Long,
    scanSeq: LcMsScanSequence,
    peakelIdByMzDbPeakelId: LongMap[Long]
  ): LcMsFeature = {
    
    val ftId = LcMsFeature.generateNewId

    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId
    val scanInitialIds = mzDbFt.getSpectrumIds
    
    // WARNING: we assume here that these methods returns the initial ID but it may change in the future
    val apexScanInitialId = mzDbFt.getApexSpectrumId.toInt
    val (firstScanInitialId, lastScanInitialId) = (scanInitialIds.head.toInt, scanInitialIds.last.toInt)
    val firstLcMsScanId = lcmsScanIdByInitialId(firstScanInitialId)
    val lastLcMsScanId = lcmsScanIdByInitialId(lastScanInitialId)
    val apexLcMsScanId = lcmsScanIdByInitialId(apexScanInitialId)
    val ms2EventIds = mzDbFt.getMs2SpectrumIds.map(sid => lcmsScanIdByInitialId(sid.toInt))
    // END OF WARNING

    val approxMass = mzDbFt.mz * mzDbFt.charge
    // FIXME: DBO => this is not very accurate (what about big peptides ?)
    val theoBasePeakelIndex = if (approxMass < 2000) 0
    else if (approxMass < 3500) 1
    else 2
    
    val indexedPeakels = mzDbFt.indexedPeakels
    
    var basePeakelIndex = 0
    for ( (peakel,isotopeIdx) <- indexedPeakels ) {
      if (isotopeIdx <= theoBasePeakelIndex) {
        basePeakelIndex = isotopeIdx
      }
    }
    val basePeakel = indexedPeakels(basePeakelIndex)._1

    val lcmsFtPeakelItems = for ( (mzDbPeakel, peakelIdx) <- indexedPeakels) yield {
      
      // Retrieve perisited LC-MS peakel id if it exists
      val lcmsPeakelIdOpt = peakelIdByMzDbPeakelId.get(mzDbPeakel.id)
      
      val lcmsPeakel = this._mzDbPeakelToLcMsPeakel(mzDbPeakel,lcmsPeakelIdOpt,rawMapId,scanSeq)
      
      // Cache new LC-MS peakel ID
      peakelIdByMzDbPeakelId(mzDbPeakel.id) = lcmsPeakel.id
      
      FeaturePeakelItem(
        featureReference = FeatureIdentifier(ftId),
        peakelReference = lcmsPeakel,
        isotopeIndex = peakelIdx,
        isBasePeakel = (peakelIdx == basePeakelIndex)
      )
    }
    
    val ftProps = FeatureProperties()
    if (mzDbFt.isPredicted) {
      require(
        putativeFeature.isDefined,
        "the putativeFeature must be defined if the feature has been predicted"
      )
      
      // Add predicted time property
      val predictedTime = putativeFeature.get.elutionTime
      ftProps.setPredictedElutionTime(Some(predictedTime))
    }
    
    new LcMsFeature(
      id = ftId,
      moz = mzDbFt.mz,
      apexIntensity = basePeakel.getApexIntensity(),
      intensity = basePeakel.getApexIntensity(),
      charge = mzDbFt.charge,
      elutionTime = mzDbFt.getElutionTime,
      duration = mzDbFt.calcDuration(),
      qualityScore = Option(mzDbFt.qualityProperties).map(_.qualityScore).getOrElse(0f),
      ms1Count = mzDbFt.getMs1Count,
      ms2Count = mzDbFt.getMs2Count,
      isOverlapping = false,
      isotopicPatterns = None,
      selectionLevel = 2,
      properties = Some(ftProps),
      relations = new FeatureRelations(
        ms2EventIds = ms2EventIds,
        peakelItems = lcmsFtPeakelItems,
        peakelsCount = lcmsFtPeakelItems.length,
        firstScanInitialId = firstScanInitialId,
        lastScanInitialId = lastScanInitialId,
        apexScanInitialId = apexScanInitialId,
        firstScanId = firstLcMsScanId,
        lastScanId = lastLcMsScanId,
        apexScanId = apexLcMsScanId,
        rawMapId = rawMapId
      )
    )
  }

}

