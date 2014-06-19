package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer

//import com.fasterxml.jackson.annotation.JsonInclude
//import com.fasterxml.jackson.annotation.JsonInclude.Include

import fr.profi.util.misc.InMemoryIdGen
  
object Fragmentation {
  
  val ionSeriesNames = "a a-NH3 a-H2O b b-NH3 b-H2O c d v w x y y-NH3 y-H2O z z+1 z+2 ya yb immonium".split(" ")
  val mascotIonSeriesNames = "a a* a0 b b* b0 c d v w x y y* y0 z z+1 z+2 ya yb immonium".split(" ")
  
  val defaultIonTypes: Array[FragmentIonType] = {
    
    // Create a map of theoretical fragments
    val ionTypes = new ArrayBuffer[FragmentIonType](ionSeriesNames.length)   
    for(ionSeriesName <- ionSeriesNames ) {
      // Create new fragment ion type
      ionTypes += new FragmentIonType( ionSeriesName )
    }
    
    ionTypes.toArray
  }
  
  lazy val defaultIonTypeByMascotSeriesName = mascotIonSeriesNames.zip(defaultIonTypes).toMap
  
  def isReverseSeries( seriesName: String ): Boolean = {
    seriesName.startsWith("x") || seriesName.startsWith("y") || seriesName.startsWith("z")
  }

}

object FragmentIonSeries extends Enumeration {
  val a = Value("a")
  val a_NH3 = Value("a-NH3")
  val a_H2O = Value("a-H2O")
  val b = Value("b")
  val b_NH3 = Value("b-NH3")
  val b_H2O = Value("b-H2O")
  val c = Value("c")
  val d = Value("d")
  val v = Value("v")
  val w = Value("w")
  val x = Value("x")
  val y = Value("y")
  val y_NH3 = Value("y-NH3")
  val y_H2O = Value("y-H2O")
  val ya = Value("ya")
  val yb = Value("yb")
  val z = Value("z")
  val z1 = Value("z+1")
  val z2 = Value("z+2")
  val immonium = Value("immonium")
}

object NeutralLoss extends Enumeration {
  val H2O = Value("H2O")
  val H3PO4 = Value("H3PO4")
  val NH3 = Value("NH3")
}

object FragmentIonType extends InMemoryIdGen {
  
  def parseNeutralLoss(ionSeriesName: String): Option[NeutralLoss.Value] = {
    if( ionSeriesName matches ".*-.*"  ) Some(NeutralLoss.withName( ionSeriesName.split("-")(1) ))
    else None
  }
  
}
case class FragmentIonType private (
  
  var id: Long = FragmentIonType.generateNewId(),
  
  // Required fields
  val ionSeries: FragmentIonSeries.Value,
  
  // Immutable optional fields
  val neutralLoss: Option[NeutralLoss.Value] = None,
  
  var properties: Option[FragmentIonTypeProperties] = None
  
) {
  
  def this(ionSeriesName: String, properties: Option[FragmentIonTypeProperties] = None ) = {
    this( FragmentIonType.generateNewId(),
          FragmentIonSeries.withName(ionSeriesName),
          FragmentIonType.parseNeutralLoss(ionSeriesName),
          properties )
  }  
  
  val isReverseSeries = Fragmentation.isReverseSeries(ionSeries.toString)
  
  override def toString():String = {
    this.ionSeries.toString
    //if( neutralLoss.isDefined ) ionSeries + "-" + neutralLoss.get   
    //else ionSeries.toString
  }
}

//@JsonInclude( Include.NON_NULL )
case class FragmentIonTypeProperties()
  
trait FragmentationRule {
  // Required fields
  val description: String
  var properties: Option[FragmentationRuleProperties]
  
  require( description != null )
}

//@JsonInclude( Include.NON_NULL )
case class FragmentationRuleProperties()
  
case class ChargeConstraint(
  
  // Required fields
  val description: String,
  val fragmentCharge: Int,
  val precursorMinCharge: Option[Int] = None,
  
  var properties: Option[FragmentationRuleProperties] = None
  
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
  val requiredSeriesQualityLevel: String,
  
  var properties: Option[FragmentationRuleProperties] = None
  
) extends FragmentationRule with FragmentationSeriesRequirement {
  
  require( requiredSeries != null )
  require( requiredSeriesQualityLevel != null )

}

case class FragmentIonRequirement(
    
  // Required fields
  val description: String,
  val ionType: FragmentIonType,
  val requiredSeries: FragmentIonSeries.Value = null,
  val requiredSeriesQualityLevel: String = null,
 
  // Immutable optional fields
  val fragmentMaxMoz: Option[Float] = None,
  val residueConstraint: Option[String] = None,
  
  var properties: Option[FragmentationRuleProperties] = None
  
) extends FragmentationRule with FragmentationSeriesRequirement

