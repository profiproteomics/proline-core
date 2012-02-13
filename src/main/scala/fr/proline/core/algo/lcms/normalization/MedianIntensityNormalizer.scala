package fr.proline.core.algo.lcms.normalization

class MedianIntensityMapNormalizer extends IMapSetNormalizer {

  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.lcms.MapClasses._
  import fr.proline.core.om.helper.MiscUtils.median
  
  def computeNormalizationFactors( mapSet: MapSet ): Map[Int,Float] = {
    
    val medianIntensityByMapIdBuilder = scala.collection.immutable.Map.newBuilder[Int,Double]
    
    // Compute intensity median for each map
    for( lcmsMap <- mapSet.childMaps ) {
      val mapIntensities = lcmsMap.features.map(_.intensity)

      val mapMedianIntensity = median(mapIntensities)
      medianIntensityByMapIdBuilder += (lcmsMap.id -> mapMedianIntensity)
    }
    
    this.calcNormalizationFactors( mapSet.getChildMapIds.toList, medianIntensityByMapIdBuilder.result() )
    
  }

}