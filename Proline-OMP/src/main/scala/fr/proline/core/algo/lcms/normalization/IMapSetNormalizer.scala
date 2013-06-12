package fr.proline.core.algo.lcms.normalization

trait IMapSetNormalizer {

  import fr.proline.core.om.model.lcms._
  
  def normalizeFeaturesIntensity( mapSet: MapSet ): Unit = {
    
    val nfByMapId = this.computeNormalizationFactorByMapId( mapSet )
    
    for( childMap <- mapSet.childMaps ) {
      val nf = nfByMapId(childMap.id)
      childMap.normalizationFactor = nf
      
      for( ft <- childMap.features ) ft.normalizedIntensity = Some( ft.intensity * nf )
    }
    
  }
  
  /** Required method for classes consuming the trait */
  protected def computeNormalizationFactorByMapId( mapSet: MapSet ): Map[Long,Float]
  
  protected def determineReferenceMapId( mapIds: List[Long], intensityByMapId: Map[Long,Double] ): Long = {
    
    // Compute ref map as the map with the median of intensity sum or intensity median
    val mapIdsSortedByIntensity = mapIds.sort { (a,b) => intensityByMapId(a) < intensityByMapId(b) } 
    
    // Choose ref map as the map with the median intensity sum or intensity median
    val refIndex = (mapIds.length/2).toInt
    mapIdsSortedByIntensity(refIndex)
  
  }
  
  protected def calcNormalizationFactorByMapId( mapIds: List[Long], intensityByMapId: Map[Long,Double] ): Map[Long,Float] = {
    
    val refMapId = this.determineReferenceMapId( mapIds, intensityByMapId )
    val refMapIntensity = intensityByMapId(refMapId)
    
    // Compute normalization factor for each map of the map_set compared to the reference map
    // Normalized factor for reference map = 1
    mapIds.map { mapId => (mapId -> (refMapIntensity/intensityByMapId(mapId)).toFloat ) } toMap
    
  }
 


}