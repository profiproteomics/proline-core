package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer
  
object Fragmentation {
  
  lazy val defaultIonTypes: Array[FragmentIonType] = {
    
    // Create a map of theoretical fragments
    val ionTypesAsStr = "a a-NH3 a-H2O b b-NH3 b-H2O c d v w x y y-NH3 y-H2O z z+1 z+2 ya yb immonium".split(" ")
    
    val ionTypes = new ArrayBuffer[FragmentIonType](ionTypesAsStr.length)   
    for( val ionTypeAsStr <- ionTypesAsStr ) {
      
      var( ionSeries, nl ) = ("", Option.empty[NeutralLoss.Value] )
      if( ionTypeAsStr matches ".*-.*"  ) {
        val ionTypeAttrs = ionTypeAsStr.split("-")
        ionSeries = ionTypeAttrs(0)
        nl = Some(NeutralLoss.withName(ionTypeAttrs(1)))
      }
      else { ionSeries = ionTypeAsStr }
      
      // Create new fragment ion type
      ionTypes += new FragmentIonType(
                                      ionSeries = FragmentIonSeries.withName(ionSeries),
                                      neutralLoss = nl
                                    )
    }
    
    ionTypes.toArray
  } 

}

object FragmentIonSeries extends Enumeration {
  //type FragmentIonSeries = Value
  val a = Value("a")
  val b = Value("b")
  val c = Value("c")
  val d = Value("d")
  val v = Value("v")
  val w = Value("w")
  val x = Value("x")
  val y = Value("y")
  val ya = Value("ya")
  val yb = Value("yb")
  val z = Value("z")
  val z1 = Value("z+1")
  val z2 = Value("z+2")
  val immonium = Value("immonium")
}

object NeutralLoss extends Enumeration {
  val H2O = Value("H2O")
  val NH3 = Value("NH3")
}

case class FragmentIonType(   
  // Required fields
  val ionSeries: FragmentIonSeries.Value,
 
  // Immutable optional fields
  val neutralLoss: Option[NeutralLoss.Value] = None
) {
  
  override def toString():String = {
    if( neutralLoss != None ) ionSeries + "-" + neutralLoss.get   
    else ionSeries.toString
  }
}
  
trait FragmentationRule{
  // Required fields
  val description: String
  
  require( description != null )
}
  
case class ChargeConstraint(
  
  // Required fields
  val description: String,
  val fragmentCharge: Int,
  val precursorMinCharge: Option[Int] = None
  
) extends FragmentationRule

trait FragmentationSeriesRequirement {
  
  val requiredSeries: FragmentIonSeries.Value
  val requiredSeriesQualityLevel: String
  
  // Requirements
  if( requiredSeriesQualityLevel != null ) {
    require( requiredSeriesQualityLevel == "significant" || 
             requiredSeriesQualityLevel == "highest_scoring" )
  }
}

case class RequiredSeries (
  
  // Required fields
  val description: String,
  val requiredSeries: FragmentIonSeries.Value,
  val requiredSeriesQualityLevel: String
  
) extends FragmentationRule with FragmentationSeriesRequirement {
  
  require( requiredSeries != null )
  require( requiredSeriesQualityLevel != null )

}

case class TheoreticalFragmentIon(
    
  // Required fields
  val description: String,
  val ionType: FragmentIonType,
  val requiredSeries: FragmentIonSeries.Value = null,
  val requiredSeriesQualityLevel: String = null,
 
  // Immutable optional fields
  val fragmentMaxMoz: Double = 0.0,
  val residueConstraint: Option[String] = None  
  
) extends FragmentationRule with FragmentationSeriesRequirement

