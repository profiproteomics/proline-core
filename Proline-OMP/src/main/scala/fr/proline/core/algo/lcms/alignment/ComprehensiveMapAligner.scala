package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.AlignmentParams

class ComprehensiveMapAligner extends ILcmsMapAligner {

  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
  import fr.profi.util.math.combinations
  import fr.proline.core.om.model.lcms._
  
  def computeMapAlignments( lcmsMaps: Seq[ProcessedMap], alnParams: AlignmentParams ): AlignmentResult = {
    
    val nbMaps = lcmsMaps.length
    
    // Compute map pairs
    val mapById = lcmsMaps.map { lcmsMap => (lcmsMap.id -> lcmsMap) } toMap
    val mapIds = mapById.keys.toList
    val nrMapIdPairs = combinations( 2, mapIds )
    
    // Remove map pair redundancy
    /*val mapIdPairHash
    for( mapIdPair <- mapIdPairs ) {
      val sortedMapIdPair = sort { a <= b } mapIdPair
      val key = join('-', sortedMapIdPair )
      mapIdPairHash(key) = sortedMapIdPair
    }
    val nrMapIdPairs = values(mapIdPairHash)*/
    
    val mapAlnSets = new ArrayBuffer[MapAlignmentSet](nrMapIdPairs.length)
    for( mapIdPair <- nrMapIdPairs ) {

      val( map1, map2 ) = ( mapById(mapIdPair(0)), mapById(mapIdPair(1)) )
      
      val mapAlnSetOpt = this.computePairwiseAlnSet( map1, map2, alnParams )
      for ( mapAlnSet <- mapAlnSetOpt ) mapAlnSets += mapAlnSet
    }
    
    val refMap = this.determineAlnReferenceMap( lcmsMaps, mapAlnSets )
    
    AlignmentResult( refMap.id, mapAlnSets.toArray )
  }
  
  def determineAlnReferenceMap( lcmsMaps: Seq[ProcessedMap], 
                                mapAlnSets: Seq[MapAlignmentSet],
                                currentRefMap: ProcessedMap = null ): ProcessedMap = {
    
    if( lcmsMaps.length <= 2 ) return lcmsMaps(0)
    
    val mapAlnSetsByMapId = new HashMap[Long,ArrayBuffer[MapAlignmentSet]]
    for( mapAlnSet <- mapAlnSets ) {
      mapAlnSetsByMapId.getOrElseUpdate( mapAlnSet.refMapId, new ArrayBuffer[MapAlignmentSet](0) ) += mapAlnSet
      mapAlnSetsByMapId.getOrElseUpdate( mapAlnSet.targetMapId, new ArrayBuffer[MapAlignmentSet](0) ) += mapAlnSet
    }
    
    var refMap: ProcessedMap = null
    var refMapDistance = Double.NaN
    
    for( tmpRefMap <- lcmsMaps ) {
      val mapAlnSetsOpt = mapAlnSetsByMapId.get(tmpRefMap.id)
      
      var distance = 0.0 // mean of absolute delta times
      var nbLandmarks = 0
      for( mapAlnSets <- mapAlnSetsOpt ; mapAlnSet <- mapAlnSets ) {
        for( mapAln <- mapAlnSet.mapAlignments ) {
          val deltaTimeList = mapAln.deltaTimeList.toList
          distance += deltaTimeList.reduceLeft( (a:Float, b:Float) => math.abs(a) + math.abs(b) )
          nbLandmarks += deltaTimeList.length
        }
      }
      if( distance > 0 ) distance /= nbLandmarks 
      
      if( refMapDistance.isNaN || distance < refMapDistance ) {
        refMap = tmpRefMap
        refMapDistance = distance
      }
        
      //print tmpRefMap.id .": " .distance."\n"
    }
    //print "ref map_id: ".refMap.id."\n"
    
    refMap
  }

  
}