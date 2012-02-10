package fr.proline.core.om.lcms

package FeatureClasses {
  
  import scala.collection.mutable.HashMap
  
  // TODO: move somewhere else
  trait InMemoryIdGen {
    private var inMemoryIdCount = 0
    def generateNewId(): Int = { inMemoryIdCount -= 1; inMemoryIdCount }
  }
  
  class Peak (
      
          // Required fields
          val moz: Double,
          val intensity: Double,
          val leftHwhm: Double,
          val rightHwhm: Double
          
          ) {
    
  }
  
  class IsotopicPattern (
      
          // Required fields
          var id: Int,
          val moz: Double,
          val intensity: Double,
          val charge: Int,
          val fitScore: Double,
          val peaks: Array[Peak],
          val scanInitialId: Int,
          val overlappingIPs: Array[IsotopicPattern],
          
          // Mutable optional fields
          var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
          
          ) {
    
    
  }
  
  object Feature extends InMemoryIdGen
  
  class Feature (
          
          // Required fields
          var id: Int,
          val moz: Double,
          val intensity: Double,
          val charge: Int,
          val elutionTime: Float,
          val qualityScore: Double,
          val ms1Count: Int,
          val ms2Count: Int,
          val isOverlapping: Boolean,
          val firstScanInitialId: Int,
          val lastScanInitialId: Int,
          val apexScanInitialId: Int,
          val ms2EventIds: Array[Int],
          val isotopicPatterns: Option[Array[IsotopicPattern]],
          val overlappingFeatures: Array[Feature],
          
          // Mutable optional fields
          var children: Array[Feature] = null,
          var subFeatures: Array[Feature] = null,
          var calibratedMoz: Double = Double.NaN,
          var normalizedIntensity: Double = Double.NaN,
          var correctedElutionTime: Float = Float.NaN,
          var isClusterized: Boolean = false,
          var selectionLevel: Int = 2,
          
          var firstScanId: Int = 0,
          var lastScanId: Int = 0,
          var apexScanId: Int = 0,
          var bestChildId: Int = 0,
          var theoreticalFeatureId: Int = 0,
          var compoundId: Int = 0,
          var mapLayerId: Int = 0,
          var mapId: Int = 0,
          
          var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
          
          ) {
    
    // Requirements

    import fr.proline.core.om.helper.MsUtils
    
    lazy val mass = MsUtils.mozToMass( moz, charge )
    lazy val isCluster = if( subFeatures == null ) false else subFeatures.length > 0
    
    def getCorrectedElutionTime = if( correctedElutionTime.isNaN ) elutionTime else correctedElutionTime
  
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



}