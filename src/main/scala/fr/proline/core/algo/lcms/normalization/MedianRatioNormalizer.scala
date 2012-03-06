package fr.proline.core.algo.lcms.normalization

class MedianRatioNormalizer extends IMapSetNormalizer {

  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._
  import fr.proline.core.om.helper.MiscUtils.median
  
  protected def computeNormalizationFactorByMapId( mapSet: MapSet ): Map[Int,Float] = {
    
    val masterMap = mapSet.masterMap
    if( masterMap == null ) { throw new Exception( "The map_set #"+ mapSet.id +" must have a master map\n") }
    
    // Retrieve some vars
    val masterMapFeatures = masterMap.features
    val maps = mapSet.childMaps
    val mapIdsAsList = mapSet.getChildMapIds.toList
    
    // Determine reference map
    val intSumByMapId = new IntensitySumMapNormalizer().getIntensitySumByMapId( mapSet )
    val refMapId = this.determineReferenceMapId( mapIdsAsList, intSumByMapId)
    val nonRefMapIds = mapIdsAsList filter { _ != refMapId }
    
    // Compute ratios of feature intensities between each map and the reference map
    val intensityRatiosByMapId = new java.util.HashMap[Int,ArrayBuffer[Float]]
    for( mapId <- mapIdsAsList ) intensityRatiosByMapId.put( mapId, new ArrayBuffer[Float](0) )
    
    for( masterFt <- masterMapFeatures ) {
      val childFtByMapId = masterFt.children.map { ft => ( ft.mapId -> ft ) } toMap
      
      if( childFtByMapId contains refMapId ) {
        val refChildFt = childFtByMapId( refMapId )
        
        for( mapId <- nonRefMapIds ) {
          
          val childFt = childFtByMapId.get( mapId )
          if( childFt != None && childFt.get.intensity > 0 ) {
            val intRatio = refChildFt.intensity / childFt.get.intensity
            intensityRatiosByMapId.get(mapId) += intRatio.toFloat
          }
        }
      }
    }
    
    // Compute normalization factors as median intensity ratio for each map
    val nfByByMapIdBuilder = scala.collection.immutable.Map.newBuilder[Int,Float]
    nfByByMapIdBuilder += ( refMapId -> 1 )
    
    for( mapId <- mapIdsAsList ) {
      val intensityRatios = intensityRatiosByMapId.get(mapId)
      val mapMedianRatio = median( intensityRatios.toArray[Float] )
      nfByByMapIdBuilder += (mapId -> mapMedianRatio)
    }
    
    nfByByMapIdBuilder.result()
    
  }
  
}