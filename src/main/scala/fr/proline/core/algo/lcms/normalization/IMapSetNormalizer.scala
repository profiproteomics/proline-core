package fr.proline.core.algo.lcms.normalization

trait IMapSetNormalizer {

  import fr.proline.core.om.lcms.MapClasses._
  
  def computeNormalizationFactors( mapSet: MapSet ): Map[Int,Float]

  def determineReferenceMapId( mapIds: List[Int], intensityByMapId: Map[Int,Double] ): Int = {
    
    // Compute ref map as the map with the median of intensity sum or intensity median
    val mapIdsSortedByIntensity = mapIds.sort { (a,b) => intensityByMapId(a) < intensityByMapId(b) } 
    
    // Choose ref map as the map with the median intensity sum or intensity median
    val refIndex = (mapIds.length/2).toInt
    mapIdsSortedByIntensity(refIndex)
  
  }
  
  protected def calcNormalizationFactors( mapIds: List[Int], intensityByMapId: Map[Int,Double] ): Map[Int,Float] = {
    
    val refMapId = this.determineReferenceMapId( mapIds, intensityByMapId )
    val refMapIntensity = intensityByMapId(refMapId)
    
    // Compute normalization factor for each map of the map_set compared to the reference map
    // Normalized factor for reference map = 1
    mapIds.map { mapId => (mapId -> (refMapIntensity/intensityByMapId(mapId)).toFloat ) } toMap
    
  }
 
  /** Computes the median value of a sequence of Doubles */
  /*def median(s: Seq[Double]) = {
    val (lower, upper) = s.sortWith(_<_).splitAt(s.size / 2)
    if (s.size % 2 == 0) (lower.last + upper.head) / 2.0 else upper.head
  }*/
  
  def median[T](s: Seq[T])(implicit n: Fractional[T]) = {
    import n._
    val (lower, upper) = s.sortWith(_<_).splitAt(s.size / 2)
    if (s.size % 2 == 0) (lower.last + upper.head) / fromInt(2) else upper.head
  }

}