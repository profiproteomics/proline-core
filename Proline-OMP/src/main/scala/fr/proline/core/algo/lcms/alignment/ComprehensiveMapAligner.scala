package fr.proline.core.algo.lcms.alignment

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.profi.util.collection._
import fr.profi.util.math.combinations

import fr.proline.core.algo.lcms.AlignmentParams
import fr.proline.core.om.model.lcms._

class ComprehensiveMapAligner extends AbstractLcmsMapAligner {

  def computeMapAlignments( lcmsMaps: Seq[ProcessedMap], alnParams: AlignmentParams ): AlignmentResult = {
    
    val nbMaps = lcmsMaps.length
    
    // Compute map pairs
    val mapById = lcmsMaps.mapByLong(_.id)
    val mapIds = mapById.keys.toList
    val nrMapIdPairs = combinations( 2, mapIds )
    
    val mapAlnSets = new ArrayBuffer[MapAlignmentSet](nrMapIdPairs.length)
    for (mapIdPair <- nrMapIdPairs) {

      val map1 = mapById(mapIdPair(0))
      val map2 = mapById(mapIdPair(1))
      
      val mapAlnSetOpt = this.computePairwiseAlnSet( map1, map2, alnParams )
      if (mapAlnSetOpt.isDefined) mapAlnSets += mapAlnSetOpt.get
    }
    
    val refMap = this.determineAlnReferenceMap( lcmsMaps, mapAlnSets )
    
    AlignmentResult( refMap.id, mapAlnSets.toArray )
  }
  
  def determineAlnReferenceMap(
    lcmsMaps: Seq[ProcessedMap], 
    mapAlnSets: Seq[MapAlignmentSet],
    currentRefMap: ProcessedMap = null
  ): ProcessedMap = {
    require(lcmsMaps.nonEmpty, "lcmsMaps is empty")
    
    if (lcmsMaps.length <= 2) return lcmsMaps(0)
    
    val mapAlnSetsByMapId = new HashMap[Long,ArrayBuffer[MapAlignmentSet]]
    for( mapAlnSet <- mapAlnSets ) {
      mapAlnSetsByMapId.getOrElseUpdate( mapAlnSet.refMapId, new ArrayBuffer[MapAlignmentSet](0) ) += mapAlnSet
      mapAlnSetsByMapId.getOrElseUpdate( mapAlnSet.targetMapId, new ArrayBuffer[MapAlignmentSet](0) ) += mapAlnSet
    }
    
    var refMap: ProcessedMap = null
    var refMapDistance = Double.NaN
    
    for( tmpRefMap <- lcmsMaps ) {
      val mapAlnSetsOpt = mapAlnSetsByMapId.get(tmpRefMap.id)

      var absMeanDistance = 0f // mean of absolute delta times
      var nbLandmarks = 0
      for (mapAlnSets <- mapAlnSetsOpt; mapAlnSet <- mapAlnSets) {
        for (mapAln <- mapAlnSet.mapAlignments) {
          val deltaTimeList = mapAln.deltaTimeList.toList
          absMeanDistance += deltaTimeList.foldLeft(0f)( (sum, delta) => sum + math.abs(delta) )
          nbLandmarks += deltaTimeList.length
        }
      }
      if (absMeanDistance > 0) absMeanDistance /= nbLandmarks

      if (refMapDistance.isNaN || absMeanDistance < refMapDistance) {
        refMap = tmpRefMap
        refMapDistance = absMeanDistance
      }
    }
    
    refMap
  }

  
}