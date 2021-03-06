package fr.proline.core.algo.lcms

import fr.profi.util.collection._
import fr.profi.util.ms.calcMozTolInDalton
import fr.proline.core.om.model.lcms._

import scala.collection.mutable.{ArrayBuffer, LongMap}

object FeatureMapper {
  

  def computePairwiseFtMapping(
    map1Features: Seq[Feature],
    map2Features: Seq[Feature],
    methodParams: FeatureMappingParams,
    isChargeTolerant: Boolean = false
  ): LongMap[ArrayBuffer[Feature]] = {

    require(methodParams.mozTol.isDefined, "FeatureMapper requires FeatureMappingParams.mozTol parameter")
    require(methodParams.mozTolUnit.isDefined, "FeatureMapper requires FeatureMappingParams.mozTolUnit parameter")

    val mozTol = methodParams.mozTol.get
    val mozTolUnit = methodParams.mozTolUnit.get
    val timeTol = methodParams.timeTol
    
    // Group features by charge
    var map1FtsByCharge = LongMap.empty[Seq[Feature]]
    var map2FtsByCharge = LongMap.empty[Seq[Feature]]
    var chargeStates = Array.empty[Long]
    
    if (isChargeTolerant == false) {
      map1FtsByCharge = map1Features.groupByLong( _.charge )
      map2FtsByCharge = map2Features.groupByLong( _.charge )
      chargeStates = (map1FtsByCharge.keys.toArray ++ map2FtsByCharge.keys).distinct
    } else {
      // Use a zero charge to marge all charge states
      map1FtsByCharge = LongMap( 0L -> map1Features )
      map2FtsByCharge = LongMap( 0L -> map2Features ) 
      chargeStates = Array( 0L )
    }
    
    val ftMapping = new collection.mutable.LongMap[ArrayBuffer[Feature]] // ft1Id => Array(ft2) 

    for (chargeState <- chargeStates) {

      if (map1FtsByCharge.contains(chargeState) && map2FtsByCharge.contains(chargeState)) {
        
        val map1SameChargeFts = map1FtsByCharge(chargeState)
        val map2SameChargeFts = map2FtsByCharge(chargeState)
        
        //print "charge: chargeState \n"
        //print "nb m1 ft: ". scalar(map1_ftGroup) ."\n"
        //print "nb m2 ft: ". scalar(map2_ftGroup) ."\n"
      
        // Group map2 features by m/z integer (truncated, not rounded)
        val map2FtsGroupedByMoz = map2SameChargeFts.groupByLong( _.moz.toInt )
        
        for (map1Ft <- map1SameChargeFts) {
          
          // Define some vars
          val map1FtId = map1Ft.id
          val map1FtMoz = map1Ft.moz
          var map1FtTime = map1Ft.getCorrectedElutionTimeOrElutionTime
          val map1FtDuration = map1Ft.duration // duration is updated for clusters
          
          // Be more tolerant for feature clusters
          /*val localTimeTol = map1Ft.time 
          if( map1Ft.isCluster ) {
            val subFts = map1Ft.subFeatures
            val subFtsSortedByTime = subFts.sortBy( _.elutionTime )
            val clusterElutionDuration = subFtsSortedByTime( subFts.length - 1 ).getCorrectedElutionTimeOrElutionTime - 
                                         subFtsSortedByTime(0).getCorrectedElutionTimeOrElutionTime
            map1FtDuration += clusterElutionDuration
          }*/
          
          // Retrieve putative features of map2 which match the current map1 feature (same m/z range)
          val moz1AsInt = map1FtMoz.toInt
          val sameMozRangeMap2Fts = new ArrayBuffer[Feature](0)
          for( mozIndex <- moz1AsInt-1 to moz1AsInt+1 ) {
            if( map2FtsGroupedByMoz contains mozIndex ) {
              sameMozRangeMap2Fts ++= map2FtsGroupedByMoz(mozIndex) 
            }
          }
          
          // Compute m/z tolerance in daltons
          val mozTolInDalton = calcMozTolInDalton( map1FtMoz, mozTol, mozTolUnit )
          
          for (map2Ft <- sameMozRangeMap2Fts) {
            
            val deltaMoz = math.abs(map1FtMoz - map2Ft.moz)
            val deltaTime = math.abs(map1FtTime - map2Ft.getCorrectedElutionTimeOrElutionTime)
            val map2FtDuration = map2Ft.duration
            // Compute the shortest duration which will be used to adjust the time tolerance window
            val shortestDuration = if (map1FtDuration < map2FtDuration) map1FtDuration else map2FtDuration
            
            // If the m/z falls in the m/z and time tol windows
            if( deltaMoz < mozTolInDalton && deltaTime < (shortestDuration + timeTol) ) {
              ftMapping.getOrElseUpdate(map1FtId,new ArrayBuffer[Feature](1)) += map2Ft
            }
          }
        }
      }
    }
    
    ftMapping
  }

}