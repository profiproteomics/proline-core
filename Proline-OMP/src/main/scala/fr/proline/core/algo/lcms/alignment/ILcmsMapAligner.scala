package fr.proline.core.algo.lcms.alignment

import scala.collection.mutable.HashMap
import fr.proline.core.algo.lcms.AlignmentParams
import fr.proline.core.algo.lcms.FeatureMappingParams
import fr.proline.core.om.model.lcms._

case class AlignmentResult( alnRefMapId: Long, mapAlnSets: Array[MapAlignmentSet] )

trait ILcmsMapAligner {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.algo.lcms.AlnSmoother
  import fr.proline.core.algo.lcms.FeatureMapper

  def computeMapAlignments( lcmsMaps: Seq[ProcessedMap], alnParams: AlignmentParams ): AlignmentResult
  def determineAlnReferenceMap(lcmsMaps: Seq[ProcessedMap], mapAlnSets: Seq[MapAlignmentSet], currentRefMap: ProcessedMap): ProcessedMap
  
  def computePairwiseAlnSet( map1: ProcessedMap, map2: ProcessedMap, alnParams: AlignmentParams ): MapAlignmentSet = {
    
    val massInterval = alnParams.massInterval
    //val timeInterval = alnParams.timeInterval
    
    val( map1Features, map2Features ) = ( map1.features, map2.features )
    val ftMapping = FeatureMapper.computePairwiseFtMapping( map1Features, map2Features, alnParams.ftMappingParams )
    
    val map1FtById = map1Features.map { ft => (ft.id -> ft) } toMap
    
    ftMapping
    
    // two possibilities: keep nearest mass match or exclude matching conflicts (more than one match)
    val landmarksByMassIdx = new HashMap[Long,ArrayBuffer[Landmark]]
    
    for( (map1FtId, matchingFeatures) <- ftMapping ) {
      // method 2: exclude conflicts
      if( matchingFeatures.length == 1 ) {
        val map1Ft = map1FtById(map1FtId)
        val deltaTime = matchingFeatures(0).elutionTime - map1Ft.elutionTime      
        val massRangePos = ( map1Ft.mass / massInterval ).toInt
        
        landmarksByMassIdx.getOrElseUpdate(massRangePos,new ArrayBuffer[Landmark]) += Landmark( map1Ft.elutionTime, deltaTime)
      }
    }
    
    // Create an alignment smoother
    val smoothingMethodName = alnParams.smoothingMethodName
    val alnSmoother = AlnSmoother( methodName = smoothingMethodName )
    
    // Compute feature alignments
    val ftAlignments = new ArrayBuffer[MapAlignment](0)
   
    for( (massRangeIdx,landmarks) <- landmarksByMassIdx ){
      
      val landmarksSortedByTime = landmarks.sortBy( _.time )
      val timeList = landmarksSortedByTime.map { _.time }
      val deltaTimeList = landmarksSortedByTime.map { _.deltaTime }
      
      val mapAlignment = new MapAlignment(
        refMapId = map1.id,
        targetMapId = map2.id,
        massRange = (massRangeIdx*massInterval,(massRangeIdx+1)*massInterval),
        timeList = timeList.toArray,
        deltaTimeList = deltaTimeList.toArray
      )
      
      ftAlignments += alnSmoother.smoothMapAlignment( mapAlignment, alnParams.smoothingParams )
      
    }
    
     new MapAlignmentSet(
       refMapId = map1.id,
       targetMapId = map2.id,
       mapAlignments = ftAlignments.toArray
     )
    
  }

}