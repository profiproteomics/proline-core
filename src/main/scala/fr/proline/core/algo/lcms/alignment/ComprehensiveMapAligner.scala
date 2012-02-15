package fr.proline.core.algo.lcms.alignment

class ComprehensiveMapAligner extends ILcmsMapAligner {

  import scala.collection.mutable.ArrayBuffer  
  import fr.proline.core.om.helper.MiscUtils.combinations
  import fr.proline.core.om.lcms._
  
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
      
      val mapAlnSet = this.computePairwiseAlnSet( map1, map2, alnParams )
      mapAlnSets += mapAlnSet
      
    }
    
    val refMap = this.determineAlnReferenceMap( lcmsMaps, mapAlnSets )
    
    AlignmentResult( refMap.id, mapAlnSets.toArray )
  }
  
  def determineAlnReferenceMap( lcmsMaps: Seq[ProcessedMap], 
                                mapAlnSets: Seq[MapAlignmentSet],
                                currentRefMap: ProcessedMap = null ): ProcessedMap = {
    
    if( lcmsMaps.length <= 2 ) return lcmsMaps(0)
    
    val mapAlnSetsByMapId = new java.util.HashMap[Int,ArrayBuffer[MapAlignmentSet]]
    for( mapAlnSet <- mapAlnSets ) {
      for( mapId <- Array( mapAlnSet.fromMapId, mapAlnSet.toMapId) ) {
        if( !mapAlnSetsByMapId.containsKey(mapId) ) {
          mapAlnSetsByMapId.put(mapId, new ArrayBuffer[MapAlignmentSet](0))
        }
      }
      mapAlnSetsByMapId.get( mapAlnSet.fromMapId ) += mapAlnSet
      mapAlnSetsByMapId.get( mapAlnSet.toMapId ) += mapAlnSet
    }
    
    var refMap: ProcessedMap = null
    var refMapDistance = Double.NaN
    
    for( tmpRefMap <- lcmsMaps ) {
      val mapAlnSets = mapAlnSetsByMapId.get(tmpRefMap.id)
      
      var distance = 0.0 // mean of absolute delta times
      var nbLandmarks = 0
      for( mapAlnSet <- mapAlnSets ) {
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