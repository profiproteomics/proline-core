package fr.proline.core.om.model.lcms

import scala.collection.mutable.HashMap
import fr.proline.core.utils.misc.InMemoryIdGen

class Peak (
    
        // Required fields
        val moz: Double,
        val intensity: Float,
        val leftHwhm: Double,
        val rightHwhm: Double
        
        ) {
  
}

class IsotopicPattern (
    
        // Required fields
        var id: Int,
        val moz: Double,
        val intensity: Float,
        val charge: Int,
        val fitScore: Float,
        val peaks: Array[Peak],
        val scanInitialId: Int,
        val overlappingIPs: Array[IsotopicPattern],
        
        // Mutable optional fields
        var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
        
        ) {
  
  
}

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
        val overlappingFeatures: Array[Feature],
        
        val relations: FeatureRelations,
        
        // Mutable optional fields
        var children: Array[Feature] = null,
        var subFeatures: Array[Feature] = null,
        var calibratedMoz: Double = Double.NaN,
        var normalizedIntensity: Double = Double.NaN,
        var correctedElutionTime: Float = Float.NaN,
        var isClusterized: Boolean = false,
        var selectionLevel: Int = 2,
        
        var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
        
        ) {
  
  // Requirements

  import fr.proline.core.utils.ms.mozToMass
  
  lazy val mass = mozToMass( moz, charge )
  lazy val isCluster = if( subFeatures == null ) false else subFeatures.length > 0
  
  def getCalibratedMoz = if( calibratedMoz.isNaN ) moz else calibratedMoz
  def getNormalizedIntensity = if( normalizedIntensity.isNaN ) intensity else normalizedIntensity
  def getCorrectedElutionTime = if( correctedElutionTime.isNaN ) elutionTime else correctedElutionTime
  
  def toMasterFeature(): Feature = {
    val ftRelations = this.relations
    new Feature ( id = Feature.generateNewId(),
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
}

class TheoreticalFeature (
    
        // Required fields
        var id: Int,
        val moz: Double,
        val charge: Int,
        val elutionTime: Float,
        val origin: String,
        
        // Mutable optional fields
        var mapLayerId: Int = 0,
        var mapId: Int = 0,
        
        var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
        
        ) {



}
