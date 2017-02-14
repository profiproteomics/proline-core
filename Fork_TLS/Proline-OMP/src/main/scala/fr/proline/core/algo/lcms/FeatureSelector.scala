package fr.proline.core.algo.lcms

import fr.profi.util.lang.EnhancedEnum

object FeatureFilterType extends EnhancedEnum {
  val INTENSITY = Value
  val RELATIVE_INTENSITY = Value
}

object FeatureSelector {

  import filtering._
    
  def apply( filterType: String ): IFeatureSelector = { 
    
    val ftFilterType = FeatureFilterType.maybeNamed( filterType.toUpperCase() ) match {
      case Some(f) => f
      case None => throw new Exception("can't find an appropriate feature selector")
    }
  
    ftFilterType match {
      case FeatureFilterType.INTENSITY => new IntensityBasedSelector()
      case FeatureFilterType.RELATIVE_INTENSITY => new RelativeIntensityBasedSelector()
      //case "attributes" => new AttributesBasedSelector()
    }
    
  }
}