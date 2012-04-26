package fr.proline.core.algo.lcms

object FeatureSelector {

  import filtering._
    
  def apply( filterType: String ): IFeatureSelector = { filterType match {
    case "intensity" => new IntensityBasedSelector()
    case "relative_intensity" => new RelativeIntensityBasedSelector()
    //case "attributes" => new AttributesBasedSelector()
    case _ => throw new Exception("can't find an appropriate feature selector")
    }
  }
}