package fr.proline.core.om.model.lcms

import scala.beans.BeanProperty
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.profi.mzdb.model.IsotopicPatternLike
import fr.profi.mzdb.model.Peak
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.mzdb.model.PeakelDataMatrixCursor
import fr.profi.util.misc.InMemoryIdGen

// TODO: remove and use peakel model instead
case class IsotopicPattern(
  val mz: Double,
  var intensity: Float,
  val charge: Int,
  val scanInitialId: Int,
  val peaks: Array[Peak] = Array(),
  val overlappingIPs: Option[Array[IsotopicPatternLike]] = None,
  
  var fittingScore: Option[Float] = None,
  var properties: Option[IsotopicPatternProperties] = None
  
) extends IsotopicPatternLike

case class IsotopicPatternProperties()

object Peakel extends InMemoryIdGen

case class Peakel(
  var id: Long,
  val moz: Double,
  val elutionTime: Float,
  val area: Float,
  val duration: Float, // TODO: re-add duration at half maximum ???

  val isOverlapping: Boolean,
  var featuresCount: Int,
  
  //val peaks: Array[LcMsPeak],
  val dataMatrix: PeakelDataMatrix,
  
  val leftHwhmMean: Option[Float] = None,
  val leftHwhmCv: Option[Float] = None,
  val rightHwhmMean: Option[Float] = None,
  val rightHwhmCv: Option[Float] = None,
  
  //var firstScanId: Long = 0L,
  //var lastScanId: Long = 0L,
  //var apexScanId: Long = 0L,
  var rawMapId: Long = 0L,
  
  var properties: Option[PeakelProperties] = None
  
) extends fr.profi.mzdb.model.IPeakelDataContainer with IEntityReference[Peakel] {
  
  def getSpectrumIds(): Array[Long] = dataMatrix.spectrumIds
  def getElutionTimes(): Array[Float] = dataMatrix.elutionTimes
  def getMzValues(): Array[Double] = dataMatrix.mzValues
  def getIntensityValues(): Array[Float] = dataMatrix.intensityValues
  def getPeaksCount() = dataMatrix.peaksCount
  override def getNewCursor(): PeakelDataMatrixCursor = new PeakelDataMatrixCursor(dataMatrix)
  
  // Make some requirements
  require( dataMatrix.spectrumIds != null && dataMatrix.spectrumIds.length > 0, "some scanIds must be provided" )
  require( dataMatrix.mzValues != null && dataMatrix.mzValues.length > 0, "some mzValues must be provided" )
  require( dataMatrix.spectrumIds.length == dataMatrix.mzValues.length, "scanIds and mzValues must have the same size" )
  require( dataMatrix.mzValues.length == dataMatrix.intensityValues.length, "mzList and intensityList must have the same size" )
  
  // Make some requirements
  //require( peaks != null && peaks.isEmpty == false, "some peaks must be provided" )
  //require( peaks.count( _ == null ) == 0, "all peaks must be defined" )
  
  // TODO: create a trait to share this method with MzDbPeakel ?
  val apexIndex: Int = {
    
    val peaksCount = dataMatrix.peaksCount
    val intensityValues = dataMatrix.intensityValues
    var maxIntensity = 0f
    var apexIdx = 0
    
    var idx = 0
    while (idx < peaksCount) {
      val intensity = intensityValues(idx)
      if (intensity > maxIntensity) {
        maxIntensity = intensity
        apexIdx = idx
      }
      idx += 1
    }
    
    apexIdx
  }
  
  def apexIntensity: Float = dataMatrix.intensityValues(apexIndex)
  
  def firstScanId = dataMatrix.spectrumIds(0)
  def lastScanId = dataMatrix.spectrumIds( dataMatrix.peaksCount - 1 )
  def apexScanId = dataMatrix.spectrumIds(apexIndex)
  
  def getElutionStartTime(): Float = dataMatrix.elutionTimes(0)  
  def getElutionStopTime(): Float = dataMatrix.elutionTimes( dataMatrix.peaksCount - 1 )
  def getElutionTimeIntensityPairs() = dataMatrix.getElutionTimeIntensityPairs()
  
  /*def getElutionStartTime( scanSequence: LcMsScanSequence ): Float = {
    scanSequence.scanById(this.firstScanId).time
  }
  
  def getElutionStopTime( scanSequence: LcMsScanSequence ): Float = {
    scanSequence.scanById(this.lastScanId).time
  }*/
  
  def copyDataMatrix(): Peakel = {
    this.copy(
      dataMatrix = dataMatrix.copy(
        spectrumIds = dataMatrix.spectrumIds.toArray,
        elutionTimes = dataMatrix.elutionTimes.toArray,
        mzValues = dataMatrix.mzValues.toArray,
        intensityValues = dataMatrix.intensityValues.toArray
      )
    )
  }
  
}

case class PeakelProperties()

case class PeakelIdentifier( var id: Long ) extends IEntityReference[Peakel]

case class FeaturePeakelItem(
  var featureReference: IEntityReference[Feature],
  var peakelReference: IEntityReference[Peakel],
  val isotopeIndex: Int,
  val isBasePeakel: Boolean,
  var properties: Option[FeaturePeakelItemProperties] = None
) {
  
  def getPeakel(): Option[Peakel] = {
    peakelReference match {
      case peakel: Peakel => Some(peakel)
      case _ => None
    }
  }
  
}

case class FeaturePeakelItemProperties()

/*class LcMsDataPoint (
  // Required fields
  val moz: Double,
  val time: Float,
  val scanId: Long
)

// The peakel shape should store 5 LcMsDataPoints (at q0,q1,q2,q3,q4),
// the quartiles being estimated approximatively (we want experimental data points)
class PeakelShape (
  // Required fields
  val area: Float,  
  val dataPoints: Array[LcMsDataPoint],
  val overlappingPeakel: Option[Array[PeakelShape]] = None,
  
  // Mutable optional fields
  var fitScore: Option[Float] = None,
  var properties: Option[PeakelShapeProperties] = None
) {
  
  def q0(): LcMsDataPoint = dataPoints(0)
  def q1(): LcMsDataPoint = dataPoints(1)
  def q2(): LcMsDataPoint = dataPoints(2)
  def q3(): LcMsDataPoint = dataPoints(3)
  def q4(): LcMsDataPoint = dataPoints(4)
  
  def apex(): LcMsDataPoint = dataPoints(2)
  def firstScanId: Long = dataPoints(0).scanId
  def lastScanId: Long = dataPoints(4).scanId  
}

case class PeakelShapeProperties()
*/

object Feature extends InMemoryIdGen {

  /*def buildPeakels(ips: Seq[IsotopicPatternLike]): Array[Peakel] = {

    // Determine the maximum number of peaks
    val maxNbPeaks = ips.map(_.peaks.length).max

    val peakels = new ArrayBuffer[Peakel]()

    breakable {
      for (peakelIdx <- 0 until maxNbPeaks) {
        val peakelOpt = this._buildPeakel(ips, peakelIdx)

        if (peakelOpt.isDefined)
          peakels += peakelOpt.get
        else
          break
      }
    }

    peakels.toArray
  }*/

  /*protected def _buildPeakel(ips: Seq[IsotopicPatternLike], peakelIdx: Int): Option[Peakel] = {

    val lcMsPeaks = new ArrayBuffer[LcMsPeak]()

    for (ip <- ips) {
      if (peakelIdx < ip.peaks.length) {

        val peak = ip.peaks(peakelIdx)
        lcMsPeaks += LcMsPeak(
          moz = peak.getMz,
          elutionTime = peak.getLcContext.getElutionTime,
          intensity = peak.getIntensity
        )
      }
    }

    if (lcMsPeaks.isEmpty) Option.empty[Peakel]
    else Some(new Peakel(peaks.toArray))
  }*/
  
}

case class FeatureRelations(
  @transient var peakelItems: Array[FeaturePeakelItem] = null,
  var peakelsCount: Int = 0,
  @transient var compound: Option[Compound] = None,
  val ms2EventIds: Array[Long],
  val firstScanInitialId: Int,
  val lastScanInitialId: Int,
  val apexScanInitialId: Int,
  var firstScanId: Long = 0L,
  var lastScanId: Long = 0L,
  var apexScanId: Long = 0L,
  var bestChildId: Long = 0L,
  var bestChildProcessedMapId: Long = 0L,
  var theoreticalFeatureId: Long = 0L,
  var compoundId: Long = 0L,
  var mapLayerId: Long = 0L,
  var rawMapId: Long = 0L,
  var processedMapId: Long = 0L,
  @transient var peptideId: Long = 0L
)

case class Feature (
  
  // Required fields
  var id: Long,
  val moz: Double,
  val charge: Int,
  val elutionTime: Float,
  var apexIntensity: Float = 0f, // TODO: remove default value  
  var intensity: Float,
  val duration: Float,
  val qualityScore: Float,
  var ms1Count: Int,
  var ms2Count: Int,
  val isOverlapping: Boolean,
  
  // TODO: remove this field (produce peakels instead in LC-MS MAP parsers)
  @transient val isotopicPatterns: Option[Array[IsotopicPatternLike]] = None,
  val relations: FeatureRelations,
  // TODO: create a masterRelations to avoid the loss of value with the use of rebuildMasterFeaturesUsingBestChild ?
  
  // Mutable optional fields
  var children: Array[Feature] = null,
  var subFeatures: Array[Feature] = null,
  var overlappingFeatures: Array[Feature] = null,
  var calibratedMoz: Option[Double] = None,
  var normalizedIntensity: Option[Float] = None,
  var correctedElutionTime: Option[Float] = None,
  var isClusterized: Boolean = false,
  var selectionLevel: Int = 2,
  
  var properties: Option[FeatureProperties] = None
  
) extends IEntityReference[Feature] {
  
  // Requirements
  require( elutionTime.isNaN == false, "elution time must be a valid float value" )

  import fr.profi.util.ms.mozToMass
  
  lazy val mass = mozToMass( moz, charge )
  def isCluster = if( subFeatures == null ) false else subFeatures.length > 0
  def isMaster = if( children == null ) false else children.length > 0
  
  def getCorrectedElutionTimeOrElutionTime = correctedElutionTime.getOrElse(elutionTime)
  def getCalibratedMozOrMoz = calibratedMoz.getOrElse(moz)
  def getNormalizedIntensityOrIntensity = normalizedIntensity.getOrElse(intensity)
  
  def getBasePeakel(): Option[Peakel] = {
    Option(relations.peakelItems)
      .flatMap( _.find(_.isBasePeakel) )
      .flatMap( _.getPeakel() )
  }
  
  def getElutionStartTime( scanSequence: LcMsScanSequence ): Float = {
    scanSequence.scanById(this.relations.firstScanId).time
  }
  
  def getElutionStopTime( scanSequence: LcMsScanSequence ): Float = {
    scanSequence.scanById(this.relations.lastScanId).time
  }
  
  def getSourceMapId: Long = {
    if( this.isCluster || this.isMaster ) this.relations.processedMapId else this.relations.rawMapId
  }
  
  def getRawMapIds(): Array[Long] = {
    if( this.isMaster ) children.flatMap( _.getRawMapIds ).distinct
    else if ( this.isCluster ) Array(this.subFeatures(0).relations.rawMapId)
    else Array(this.relations.rawMapId)
  }
  
  def eachSubFeatureOrThisFeature( onEachSubFt: (Feature) => Unit ) {
    if( this.isCluster ) {
      for( subFt <- this.subFeatures ) onEachSubFt( subFt )
    }
    else onEachSubFt( this )
  }
  
  def eachChildSubFeature( onEachSubFt: (Feature) => Unit ) {
    require( this.isMaster, "can't iterate over children of a non maser feature" )
    
    for( childFt <- this.children ) {
      childFt.eachSubFeatureOrThisFeature( onEachSubFt )
    }
  }
  
  def toRawMapFeature(): Feature = {
    require( isCluster == false, "can't convert a cluster feature into a run map feature" )
    require( isMaster == false, "can't convert a master feature into a run map feature" )
    
    this.copy(
      calibratedMoz = None,
      normalizedIntensity = None,
      correctedElutionTime = None,
      isClusterized = false
    )
  }
  
  def toMasterFeature( id: Long = Feature.generateNewId(), children: Array[Feature] = Array(this) ): Feature = {
    val ftRelations = this.relations
    
    new Feature (
      id = id,
      moz = this.moz,
      intensity = this.intensity,
      charge = this.charge,
      elutionTime = this.getCorrectedElutionTimeOrElutionTime, // master time scale must be corrected or be the ref
      duration = this.duration,
      calibratedMoz = this.calibratedMoz,
      normalizedIntensity = this.normalizedIntensity,
      correctedElutionTime = this.correctedElutionTime,
      qualityScore = this.qualityScore,
      ms1Count = this.ms1Count,
      ms2Count = this.ms2Count,
      isOverlapping = false,
      selectionLevel = this.selectionLevel,
      relations = new FeatureRelations(
        firstScanInitialId = ftRelations.firstScanInitialId,
        lastScanInitialId = ftRelations.lastScanInitialId,
        apexScanInitialId = ftRelations.apexScanInitialId,
        firstScanId = ftRelations.firstScanId,
        lastScanId = ftRelations.lastScanId,
        apexScanId = ftRelations.apexScanId,
        bestChildId = this.id,
        bestChildProcessedMapId = ftRelations.processedMapId, //TODO => this is certainly wrong ...  
        ms2EventIds = null
      ),
      isotopicPatterns = None,
      overlappingFeatures = null,
      children = children
    )
  }
  
}

case class FeatureIdentifier( var id: Long ) extends IEntityReference[Feature]

case class FeatureProperties(
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var predictedElutionTime: Option[Float] = None,

  // TODO: fix the serialization issue (remove Option ???)
  //@JsonDeserialize(contentAs = classOf[Array[java.lang.Float]] )
  //@BeanProperty var peakelsRatios: Option[Array[Float]] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean] )
  // TODO: add to feature table ?
  @BeanProperty var isReliable: Option[Boolean] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  // TODO: rename to interferenceCorrelation
  @BeanProperty var overlapCorrelation: Option[Float] = None,
  
  // TODO: rename to interferanceFactor
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var overlapFactor: Option[Float] = None
)

object Compound extends InMemoryIdGen

case class Compound(
  var id: Long,
  var identifier: String // maybe a peptide sequence and its ptm_string or a chemical formula
)

class MasterFeatureBuilder(
  var id: Long = Feature.generateNewId,
  var bestFeature: Feature,
  val children: ArrayBuffer[Feature],
  val peptideId: Long = 0L
) {
  require(children.length > 0,"children array must not be empty")
  
  def this( bestFeature: Feature, nbMaps: Int ) {
    this( Feature.generateNewId, bestFeature, ArrayBuffer.fill(1)(bestFeature) )
  }
  
  def toMasterFeature( id: Long = this.id ): Feature = {
    val mft = bestFeature.toMasterFeature( id = id, children = children.toArray )
    mft.relations.peptideId = peptideId
    mft
  }
  
  def eachSubFeature( onEachSubFt: (Feature) => Unit ) {
    for( childFt <- this.children ) {
      if( childFt.isCluster ) {
        for( subFt <- childFt.subFeatures ) onEachSubFt( subFt )
      } else onEachSubFt( childFt )
    }
  }
  
}

case class TheoreticalFeature (
    
  // Required fields
  var id: Long,
  val moz: Double,
  val charge: Int,
  val elutionTime: Float,
  val origin: String,
  
  // Mutable optional fields
  var mapLayerId: Long = 0L,
  var mapId: Long = 0L,
  
  var properties: Option[TheoreticalFeatureProperties] = None
  
)

case class TheoreticalFeatureProperties()
