package fr.proline.core.algo.lcms

object MapSetNormalizer {
  
  import normalization._
  
  def apply( methodName: String ): IMapSetNormalizer = { methodName match {
    case "intensity_sum" => new IntensitySumMapNormalizer()
    case "median_intensity" => new MedianIntensityMapNormalizer()
    case "median_ratio" => new MedianRatioNormalizer()
    case _ => throw new Exception("can't find an appropriate map set normalizer")
    }
  }
  
}