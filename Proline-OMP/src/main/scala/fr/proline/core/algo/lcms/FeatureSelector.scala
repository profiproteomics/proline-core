package fr.proline.core.algo.lcms

object FeatureFilterType extends Enumeration {
  val INTENSITY = Value("INTENSITY")
  val RELATIVE_INTENSITY = Value("RELATIVE_INTENSITY")
}

object FeatureSelector {

  import filtering._
    
  def apply( filterType: String ): IFeatureSelector = { 
    
    val ftFilterType = try {
      FeatureFilterType.withName( filterType.toUpperCase() )
    } catch {
      case _: Throwable => throw new Exception("can't find an appropriate feature selector")
    }
  
    ftFilterType match {
      case FeatureFilterType.INTENSITY => new IntensityBasedSelector()
      case FeatureFilterType.RELATIVE_INTENSITY => new RelativeIntensityBasedSelector()
      //case "attributes" => new AttributesBasedSelector()
    }
    
  }
}