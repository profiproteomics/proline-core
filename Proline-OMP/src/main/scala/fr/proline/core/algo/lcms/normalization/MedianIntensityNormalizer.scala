package fr.proline.core.algo.lcms.normalization

class MedianIntensityMapNormalizer extends IMapSetNormalizer {

  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._
  import fr.proline.util.math.median
  
  protected def computeNormalizationFactorByMapId( mapSet: MapSet ): Map[Long,Float] = {
    
    val medianIntensityByMapIdBuilder = scala.collection.immutable.Map.newBuilder[Long,Double]
    
    // Compute intensity median for each map
    for( lcmsMap <- mapSet.childMaps ) {
      val mapIntensities = lcmsMap.features.map(_.intensity)

      val mapMedianIntensity = median(mapIntensities)
      medianIntensityByMapIdBuilder += (lcmsMap.id -> mapMedianIntensity)
    }
    
    this.calcNormalizationFactorByMapId( mapSet.getChildMapIds.toList, medianIntensityByMapIdBuilder.result() )
    
  }

}