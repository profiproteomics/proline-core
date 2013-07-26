package fr.proline.core.util.generator.lcms

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms._

class LcMsMapSetFakeGenerator( nbMaps: Int, alnFakeGenerator: MapAlignmentFakeGenerator ) {
  require( nbMaps >= 2, "two maps at least must be generated")

  def generateMapSet( lcmsRun: LcMsRun, runMap: RunMap ): MapSet = {
    
    val mapSetId = MapSet.generateNewId
    
    val processedMaps = new ArrayBuffer[ProcessedMap](nbMaps)
    for( mapNumber <- 1 to nbMaps ) {
      val clonedRunMap = _cloneRunMap(runMap)
      processedMaps += clonedRunMap.toProcessedMap(mapNumber, mapSetId, clonedRunMap.features)
    }
    
    val refMapId = processedMaps(0).id
    var timeList = lcmsRun.scanSequence.get.scans.withFilter( _.msLevel == 1).map( _.time )
    
    val mapAlnSets = processedMaps.tail.map { processedMap =>
      val mapAln = alnFakeGenerator.generateMapAlignment(timeList, refMapId, processedMap.id, Pair(0,20000) )
      
      // Update the time list
      timeList = timeList.zip( mapAln.deltaTimeList ).map { dataPoint =>
        dataPoint._1 + dataPoint._2
      }
      
      new MapAlignmentSet(
        refMapId = refMapId,
        targetMapId = processedMap.id,
        mapAlignments = Array(mapAln)
      )
    }
    
    new MapSet(
      id = MapSet.generateNewId,
      name = "",
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps.toArray,
      alnReferenceMapId = refMapId,
      mapAlnSets = mapAlnSets.toArray
    )
  }
  
  private def _cloneRunMap( runMap: RunMap ): RunMap = {
    val newRunMapId = RunMap.generateNewId
    
    val features = runMap.features.map { ft => 
      val ftRelations = ft.relations.copy( mapId = newRunMapId )
      ft.copy( id = Feature.generateNewId, relations = ftRelations )
    }
    
    runMap.copy( id = newRunMapId, creationTimestamp = new java.util.Date, features = features)
  }
  
}