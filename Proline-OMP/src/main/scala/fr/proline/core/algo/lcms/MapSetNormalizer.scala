package fr.proline.core.algo.lcms

object NormalizationMethod extends Enumeration {
  val INTENSITY_SUM = Value("INTENSITY_SUM")
  val MEDIAN_INTENSITY = Value("MEDIAN_INTENSITY")
  val MEDIAN_RATIO = Value("MEDIAN_RATIO")
}

object MapSetNormalizer {
  
  import normalization._
  
  def apply( methodName: String ): IMapSetNormalizer = {
    
    val normMethod = try {
      NormalizationMethod.withName( methodName.toUpperCase() )
    } catch {
      case _ : Throwable=> throw new Exception(s"can't find an appropriate map set normalizer for method named '$methodName'")
    }
    
    normMethod match {
      case NormalizationMethod.INTENSITY_SUM => new IntensitySumMapNormalizer()
      case NormalizationMethod.MEDIAN_INTENSITY => new MedianIntensityMapNormalizer()
      case NormalizationMethod.MEDIAN_RATIO => new MedianRatioNormalizer()
    }
  }
  
}