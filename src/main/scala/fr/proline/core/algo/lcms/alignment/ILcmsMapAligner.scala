package fr.proline.core.algo.lcms.alignment

import fr.proline.core.algo.lcms.FeatureMappingParams
import fr.proline.core.om.lcms.MapClasses._
  
case class AlignmentParams( massInterval: Int,
                            smoothingMethodName: String,
                            smoothingParams: AlnSmoothingParams,
                            ftMappingParams: FeatureMappingParams,
                            maxIterations: Int = 3
                            )
                            
case class AlignmentResult( alnRefMapId: Int, mapAlnSets: Array[MapAlignmentSet] )

trait ILcmsMapAligner {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.algo.lcms.AlnSmoother
  import fr.proline.core.algo.lcms.FeatureMapper  
  import fr.proline.core.om.lcms.FeatureClasses._

  def computeMapAlignments( lcmsMaps: Seq[ProcessedMap], alnParams: AlignmentParams ): AlignmentResult
  def determineAlnReferenceMap(lcmsMaps: Seq[ProcessedMap], mapAlnSets: Seq[MapAlignmentSet], currentRefMap: ProcessedMap): ProcessedMap
  
  def computePairwiseAlnSet( map1: ProcessedMap, map2: ProcessedMap, alnParams: AlignmentParams ): MapAlignmentSet = {
    
    val massInterval = alnParams.massInterval
    //val timeInterval = alnParams.timeInterval
    
    val( map1Features, map2Features ) = ( map1.features, map2.features )
    val ftMapping = new FeatureMapper().computePairwiseFtMapping( map1Features, map2Features, alnParams.ftMappingParams )
    
    val map1FtById = map1Features.map { ft => (ft.id -> ft) } toMap
    
    ftMapping
    
    // two possibilities: keep nearest mass match or exclude matching conflicts (more than one match)
    val landmarksByMassRangeIndex = new java.util.HashMap[Int,ArrayBuffer[Landmark]]
    
    for( (map1FtId, matchingFeatures) <- ftMapping ) {
      // method 2: exclude conflicts
      if( matchingFeatures.length == 1 ) {
        val map1Ft = map1FtById(map1FtId)
        val deltaTime = matchingFeatures(0).elutionTime - map1Ft.elutionTime      
        val massRangePos = ( map1Ft.mass / massInterval ).toInt
        
        if( ! landmarksByMassRangeIndex.containsKey(massRangePos) ) {
          landmarksByMassRangeIndex.put( massRangePos, new ArrayBuffer[Landmark](1) )
        }
        
        landmarksByMassRangeIndex.get(massRangePos) += Landmark( map1Ft.elutionTime, deltaTime)
      }
    }
    
    // Create an alignment smoother
    val smoothingMethodName = alnParams.smoothingMethodName
    val alnSmoother = AlnSmoother( methodName = smoothingMethodName )
    
    // Compute feature alignments
    val ftAlignments = new ArrayBuffer[MapAlignment](0)

    val landmarksIter = landmarksByMassRangeIndex.entrySet().iterator()    
    while( landmarksIter.hasNext() ) {
      val landmarksEntry = landmarksIter.next
      val massRangeIndex = landmarksEntry.getKey
      
      val landmarksSortedByTime = landmarksEntry.getValue.toList.sort { (a,b) => a.time < b.time }
      val timeList = landmarksSortedByTime.map { _.time }
      val deltaTimeList = landmarksSortedByTime.map { _.deltaTime }
      
      val mapAlignment = new MapAlignment(
                              fromMapId = map1.id,
                              toMapId = map2.id,
                              massRange = (massRangeIndex*massInterval,(massRangeIndex+1)*massInterval),
                              timeList = timeList.toArray,
                              deltaTimeList = deltaTimeList.toArray
                            )
      
      ftAlignments += alnSmoother.smoothMapAlignment( mapAlignment, alnParams.smoothingParams )
      
    }
    
     new MapAlignmentSet( fromMapId = map1.id,
                          toMapId = map2.id,
                          mapAlignments = ftAlignments.toArray
                        )
    
  }

}