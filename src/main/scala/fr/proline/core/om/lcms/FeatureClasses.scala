package fr.proline.core.om.lcms

package FeatureClasses {
  
  import scala.collection.mutable.HashMap
  
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
          val fitScore: Option[Double],
          val peaks: Array[Peak],
          val scanInitialId: Int,
          val overlappingIPs: Option[Array[IsotopicPattern]],
          
          // Mutable optional fields
          var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
          
          ) {
    
    
  }
  
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
          val isCluster: Boolean,
          val isOverlapping: Boolean,
          val firstScanInitialId: Int,
          val lastScanInitialId: Int,
          val apexScanInitialId: Int,
          val ms2EventIds: Option[Array[Int]],
          val subFeatures: Option[Array[Feature]],
          val children: Option[Array[Feature]],
          val isotopicPatterns: Option[Array[IsotopicPattern]],
          val overlappingFeatures: Option[Array[Feature]],
          
          // Mutable optional fields
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

    import fr.proline.core.om.helper.MsUtils
    
    lazy val mass = MsUtils.mozToMass( moz, charge )
  
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