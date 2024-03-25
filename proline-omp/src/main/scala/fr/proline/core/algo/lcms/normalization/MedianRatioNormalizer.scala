package fr.proline.core.algo.lcms.normalization

class MedianRatioNormalizer extends IMapSetNormalizer {

  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._
  import fr.profi.util.math.median
  
  protected def computeNormalizationFactorByMapId( mapSet: MapSet ): Map[Long,Float] = {
    
    val masterMap = mapSet.masterMap
    require( masterMap != null, "the map_set #"+ mapSet.id +" must have a master map")
    
    // Retrieve some vars
    val masterMapFeatures = masterMap.features
    val maps = mapSet.childMaps
    val mapIdsAsList = mapSet.getChildMapIds.toList
    
    // Determine reference map
    val intSumByMapId = new IntensitySumMapNormalizer().getIntensitySumByMapId( mapSet )
    val refMapId = this.determineReferenceMapId( mapIdsAsList, intSumByMapId)
    val nonRefMapIds = mapIdsAsList filter { _ != refMapId }
    
    // Compute ratios of feature intensities between each map and the reference map
    val intensityRatiosByMapId = new java.util.HashMap[Long,ArrayBuffer[Float]]
    for( mapId <- nonRefMapIds ) intensityRatiosByMapId.put( mapId, new ArrayBuffer[Float](0) )
    
    for( masterFt <- masterMapFeatures ) {
      val childFtByMapId = masterFt.children.map { ft => ( ft.relations.processedMapId -> ft ) }.toMap
      
      if( childFtByMapId contains refMapId ) {
        val refChildFt = childFtByMapId( refMapId )
        
        for( mapId <- nonRefMapIds ) {
          
          val childFt = childFtByMapId.get( mapId )
          if( childFt.isDefined && childFt.get.intensity > 0 ) {
            val intRatio = refChildFt.intensity / childFt.get.intensity
            intensityRatiosByMapId.get(mapId) += intRatio.toFloat
          }
        }
      }
    }
    
    // Compute normalization factors as median intensity ratio for each map
    val nfByByMapIdBuilder = scala.collection.immutable.Map.newBuilder[Long,Float]
    nfByByMapIdBuilder += ( refMapId -> 1 )
    
    for( mapId <- nonRefMapIds ) {
      val intensityRatios = intensityRatiosByMapId.get(mapId)
      require(intensityRatios.length > 0,"ratios list must not be empty")
      
      val mapMedianRatio = median( intensityRatios )
      nfByByMapIdBuilder += (mapId -> mapMedianRatio)
    }
    
    nfByByMapIdBuilder.result()
    
  }
  
}