package fr.proline.core.om.model.lcms

import scala.collection.mutable.HashMap
import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.util.misc.InMemoryIdGen

class Peak (
    
  // Required fields
  val moz: Double,
  val intensity: Float,
  val leftHwhm: Double,
  val rightHwhm: Double
  
)

//object IsotopicPattern extends InMemoryIdGen
class IsotopicPattern (
    
  // Required fields
  //var id: Int,
  val moz: Double,
  val intensity: Float,
  val charge: Int,
  val fitScore: Float,
  val peaks: Array[Peak],
  val scanInitialId: Int,
  val overlappingIPs: Array[IsotopicPattern],
  
  // Mutable optional fields
  var properties: Option[IsotopicPatternProperties] = None
  
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class IsotopicPatternProperties


object Feature extends InMemoryIdGen

case class FeatureRelations(
  val ms2EventIds: Array[Int],
  val firstScanInitialId: Int,
  val lastScanInitialId: Int,
  val apexScanInitialId: Int,        
  var firstScanId: Int = 0,
  var lastScanId: Int = 0,
  var apexScanId: Int = 0,
  var bestChildId: Int = 0,
  var bestChildMapId: Int = 0,
  var theoreticalFeatureId: Int = 0,
  var compoundId: Int = 0,
  var mapLayerId: Int = 0,
  var mapId: Int = 0
)

case class Feature (
        
  // Required fields
  var id: Int,
  val moz: Double,
  var intensity: Float,
  val charge: Int,
  val elutionTime: Float,
  val qualityScore: Double,
  var ms1Count: Int,
  var ms2Count: Int,
  val isOverlapping: Boolean,
  
  val isotopicPatterns: Option[Array[IsotopicPattern]],
  var overlappingFeatures: Array[Feature],
  
  val relations: FeatureRelations,
  
  // Mutable optional fields
  var children: Array[Feature] = null,
  var subFeatures: Array[Feature] = null,
  var calibratedMoz: Double = Double.NaN,
  var normalizedIntensity: Double = Double.NaN,
  var correctedElutionTime: Float = Float.NaN,
  var isClusterized: Boolean = false,
  var selectionLevel: Int = 2,
  
  var properties: Option[FeatureProperties] = None
  
) {
  
  // Requirements

  import fr.proline.util.ms.mozToMass
  
  lazy val mass = mozToMass( moz, charge )
  lazy val isCluster = if( subFeatures == null ) false else subFeatures.length > 0
  
  def getCalibratedMoz = if( calibratedMoz.isNaN ) moz else calibratedMoz
  def getNormalizedIntensity = if( normalizedIntensity.isNaN ) intensity else normalizedIntensity
  def getCorrectedElutionTime = if( correctedElutionTime.isNaN ) elutionTime else correctedElutionTime
  
  def toMasterFeature(): Feature = {
    val ftRelations = this.relations
    
    new Feature (
      id = Feature.generateNewId(),
      moz = this.moz,
      intensity = this.intensity,
      charge = this.charge,
      elutionTime = this.correctedElutionTime,
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
        bestChildId = ftRelations.bestChildId,
        bestChildMapId = ftRelations.mapId,
        ms2EventIds = null
        ),
      isotopicPatterns = null,
      overlappingFeatures = null,
      children = Array(this)
    )
  }
  
  /*
  def isOverlapping(f: Feature, ppm : Double, lcmsRun:LcmsRun): Boolean = {
    
    /**
     * function to test if one feature is overlapping
     * 
     */
    //doing nothing if matching occurs
    this match {
      case f => return false
    }
    
    val mozTolerance =  math.max(moz, f.moz) * ppm / 1e6
    
    if (math.abs(moz - f.moz) > mozTolerance) {
      return false
    }
    
    var minTime = lcmsRun.scanById(this.relations.firstScanId).time 
    var maxTime = lcmsRun.scanById(relations.lastScanId).time
    var fminTime = lcmsRun.scanById(f.relations.firstScanId).time
    var fmaxTime = lcmsRun.scanById(f.relations.lastScanId).time
    
    if (maxTime > fminTime && minTime < fmaxTime)  {
      // intersection add stuff in overlapping feature isotopicPattern or new feature ?
      if (f.moz > moz) {
    	 if (!overlappingFeatures.contains(f)) {
    	  overlappingFeatures :+ f
    	  f.isClusterized = true
    	 }
      }else  {
        if (! f.overlappingFeatures.contains(this)) {
        	f.overlappingFeatures :+ this
        	this.isClusterized = true
        }
      }
      return true
    }
    false
  }*/
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class FeatureProperties (
  @BeanProperty protected var peakelsCount: Option[Int] = None,
  @BeanProperty protected var peakelsRatios: Option[Array[Float]] = None,
  @BeanProperty protected var overlapCorrelation: Option[Float] = None,
  @BeanProperty protected var overlapFactor: Option[Float] = None
)


case class TheoreticalFeature (
    
  // Required fields
  var id: Int,
  val moz: Double,
  val charge: Int,
  val elutionTime: Float,
  val origin: String,
  
  // Mutable optional fields
  var mapLayerId: Int = 0,
  var mapId: Int = 0,
  
  var properties: Option[TheoreticalFeatureProperties] = None
  
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class TheoreticalFeatureProperties
