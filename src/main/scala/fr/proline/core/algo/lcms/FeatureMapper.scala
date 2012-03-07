package fr.proline.core.algo.lcms

case class FeatureMappingParams( mozTol: Double, mozTolUnit: String, timeTol: Float )

object FeatureMapper {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._
  import fr.proline.core.utils.ms.calcMozTolInDalton
  
  def computePairwiseFtMapping ( map1Features: Array[Feature],
                                 map2Features: Array[Feature],
                                 methodParams: FeatureMappingParams,
                                 isChargeTolerant: Boolean = false ): Map[Int,Array[Feature]]=  {
    
    val mozTol = methodParams.mozTol
    val mozTolUnit = methodParams.mozTolUnit
    val timeTol = methodParams.timeTol
    
    // Group features by charge
    var map1FtsByCharge: Map[Int,Array[Feature]] = null // TODO map1FtsByCharge
    var map2FtsByCharge: Map[Int,Array[Feature]] = null
    var chargeStates: List[Int] = null
    
    if( ! isChargeTolerant ) {
      map1FtsByCharge = map1Features.groupBy( _.charge )
      map2FtsByCharge = map2Features.groupBy( _.charge )
      chargeStates = (map1FtsByCharge.keys.toArray ++ map2FtsByCharge.keys).toList.distinct
    } else {
      // Use a zero charge to marge all charge states
      map1FtsByCharge = Map( 0 -> map1Features )
      map2FtsByCharge = Map( 0 -> map2Features ) 
      chargeStates = List( 0 )
    }
    
    val ftMapping = new java.util.HashMap[Int,ArrayBuffer[Feature]] // ft1Id => Array(ft2) 
    for( chargeState <- chargeStates ) {

      if( map1FtsByCharge.contains(chargeState) && map2FtsByCharge.contains(chargeState) ) {
        
        val map1SameChargeFts = map1FtsByCharge(chargeState)
        val map2SameChargeFts = map2FtsByCharge(chargeState)
        
        //print "charge: chargeState \n"
        //print "nb m1 ft: ". scalar(map1_ftGroup) ."\n"
        //print "nb m2 ft: ". scalar(map2_ftGroup) ."\n"
      
        // Group map2 features by m/z integer (truncated, not rounded)
        val map2FtsGroupedByMoz = map2SameChargeFts.groupBy( _.moz.toInt )
        
        for( map1Ft <- map1SameChargeFts ) {
          
          // Define some vars
          val map1FtId = map1Ft.id
          val map1FtMoz = map1Ft.moz
          var map1FtTime = map1Ft.getCorrectedElutionTime
          
          // Be more tolerant for feature clusters
          var localTimeTol = timeTol
          if( map1Ft.isCluster ) {
            val subFts = map1Ft.subFeatures
            val subFtsSortedByTime = subFts.toList.sort { (a,b) => a.elutionTime <= b.elutionTime } toArray
            val clusterElutionDuration = subFtsSortedByTime( subFts.length - 1 ).getCorrectedElutionTime - 
                                         subFtsSortedByTime(0).getCorrectedElutionTime
            localTimeTol += clusterElutionDuration
          }
          
          // Retrieve putative features of map2 which match the current map1 feature (same m/z range)
          val moz1AsInt = map1FtMoz.toInt
          val sameMozRangeMap2Fts = new ArrayBuffer[Feature](0)
          for( val mozIndex <- moz1AsInt-1 to moz1AsInt+1 ) {
            if( map2FtsGroupedByMoz contains mozIndex ) {
              sameMozRangeMap2Fts ++= map2FtsGroupedByMoz(mozIndex) 
            }
          }
          
          // Compute m/z tolerance in daltons
          val mozTolInDalton = calcMozTolInDalton( map1FtMoz, mozTol, mozTolUnit )
          
          for( map2Ft <- sameMozRangeMap2Fts ) {
            
            val deltaMoz = math.abs(map1FtMoz - map2Ft.moz)
            val deltaTime = math.abs(map1FtTime - map2Ft.getCorrectedElutionTime)
            
            if( deltaMoz < mozTolInDalton && deltaTime < localTimeTol ) {
              if( !ftMapping.containsKey(map1FtId) ) {
                ftMapping.put( map1FtId, new ArrayBuffer[Feature](1) )
              }
              ftMapping.get(map1FtId) += map2Ft
            }
          }
        }
      }
    }
    
    // Convert the java HashMap into a scala immutable Map
    val immutableFtMappingBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Feature]]
    
    val ftMappingIter = ftMapping.entrySet().iterator()    
    while( ftMappingIter.hasNext() ) {
      val entry = ftMappingIter.next
      immutableFtMappingBuilder += ( entry.getKey -> entry.getValue.toArray )
    }    
    
    immutableFtMappingBuilder.result()
  }

}