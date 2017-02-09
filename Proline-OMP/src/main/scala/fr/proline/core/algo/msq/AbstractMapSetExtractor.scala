package fr.proline.core.algo.msq

import java.io.File

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

import fr.profi.mzdb.model.{ Feature => MzDbFeature }
import fr.profi.mzdb.model.{ Peakel => MzDbPeakel }
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.mzdb.model.PutativeFeature
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config.ILcMsQuantConfig
import fr.proline.core.om.model.lcms.{ Feature => LcMsFeature }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatch
import fr.profi.chemistry.model.MolecularConstants
import fr.proline.core.om.storer.lcms.RawMapStorer
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.algo.lcms.LcmsMapAligner
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider

abstract class AbstractMapSetExtractor(
  val mapSetName: String,
  val lcMsRuns: Seq[LcMsRun],
  val quantConfig: ILcMsQuantConfig
  ) {

    // Define some vars
  protected val mozTolPPM = quantConfig.extractionParams.mozTol.toFloat
  protected val clusteringParams = quantConfig.clusteringParams
  protected val alnMethodName = quantConfig.alnMethodName
  protected val alnParams = quantConfig.alnParams
  protected val masterFtFilter = quantConfig.ftFilter
  protected val ftMappingParams = quantConfig.ftMappingParams
  protected val normalizationMethod = quantConfig.normalizationMethod
  protected val avgIsotopeMassDiff = MolecularConstants.AVERAGE_PEPTIDE_ISOTOPE_MASS_DIFF

  protected val mapAligner = LcmsMapAligner(methodName = alnMethodName)

  // FIXME: generate new id and store the pps
  protected val pps = new PeakPickingSoftware(
    id = 1,
    name = "Proline",
    version = "1.1.0", // TODO: retrieve the version of OMP dynamically
    algorithm = "ExtractMapSet"
  )

  
  def extractMapSet(
    lcMsRuns: Seq[LcMsRun],
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long): (MapSet, AlignmentResult)

  protected def _mzDbFeatureToLcMsFeature(
    mzDbFt: MzDbFeature,
    putativeFeature: Option[PutativeFeature],
    rawMapId: Long,
    scanSeq: LcMsScanSequence,
    peakelIdByMzDbPeakelId: LongMap[Long]): LcMsFeature = {

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
    for ((peakel, isotopeIdx) <- indexedPeakels) {
      if (isotopeIdx <= theoBasePeakelIndex) {
        basePeakelIndex = isotopeIdx
      }
    }
    val basePeakel = indexedPeakels(basePeakelIndex)._1

    val lcmsFtPeakelItems = for ((mzDbPeakel, peakelIdx) <- indexedPeakels) yield {

      // Retrieve perisited LC-MS peakel id if it exists
      val lcmsPeakelIdOpt = peakelIdByMzDbPeakelId.get(mzDbPeakel.id)

      val lcmsPeakel = this._mzDbPeakelToLcMsPeakel(mzDbPeakel, lcmsPeakelIdOpt, rawMapId, scanSeq)

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

  private def _mzDbPeakelToLcMsPeakel(
    mzDbPeakel: MzDbPeakel,
    lcMsPeakelIdOpt: Option[Long],
    rawMapId: Long,
    scanSeq: LcMsScanSequence): Peakel = {

    // Retrieve some vars
    val lcmsScanIdByInitialId = scanSeq.scanIdByInitialId

    // Create the peakel data matrix
    val peakelDataMatrix = new PeakelDataMatrix(
      // Convert mzDB scan IDs into LCMSdb scan ids (same warning as above)
      spectrumIds = mzDbPeakel.spectrumIds.map(sid => lcmsScanIdByInitialId(sid.toInt)),
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

}