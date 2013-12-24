package fr.proline.core.util.generator.lcms

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms._
import fr.proline.util.ms._

class LcMsMapSetFakeGenerator(
  nbMaps: Int,
  alnFakeGenerator: MapAlignmentFakeGenerator,
  mozErrorPPM: Float = 0f,
  timeError: Float = 0f
  ) {
  require( nbMaps >= 2, "two maps at least must be generated")

  // Note: the same LcMsRun is used to build all child maps
  // This imply that all feature are related to the same LcMsRun
  // The scan time values have thus to be modified according to the MapAlignmentSet by a further algorithm
  def generateMapSet( lcmsRun: LcMsRun, rawMap: RawMap ): MapSet = {
    
    val mapSetId = MapSet.generateNewId
    
    val processedMaps = new ArrayBuffer[ProcessedMap](nbMaps)
    for( mapNumber <- 1 to nbMaps ) {
      val clonedRawMap = _cloneRawMap(rawMap)
      processedMaps += clonedRawMap.toProcessedMap(mapNumber, mapSetId, clonedRawMap.features)
    }
    
    val refMapId = processedMaps(0).id
    var timeList = lcmsRun.scanSequence.get.scans.withFilter( _.msLevel == 1).map( _.time )
    
    val fixedProcessedMaps = new ArrayBuffer[ProcessedMap](processedMaps.length) + processedMaps.head
    val mapAlnSets = processedMaps.tail.map { processedMap =>
      val mapAln = alnFakeGenerator.generateMapAlignment(timeList, refMapId, processedMap.id, Pair(0,20000) )
      
      // Update the time list
      timeList = timeList.zip( mapAln.deltaTimeList ).map { dataPoint =>
        dataPoint._1 + dataPoint._2
      }
      
      val mapAlnSet = new MapAlignmentSet(
        refMapId = refMapId,
        targetMapId = processedMap.id,
        mapAlignments = Array(mapAln)
      )
      val reversedAlnSet = mapAlnSet.getReversedAlnSet
      
      // Update the features attributes
      val updatedFeatures = processedMap.features.map { ft =>
        
        val mozErrorInDa = calcMozTolInDalton(ft.moz,mozErrorPPM,MassTolUnit.PPM)
        val newMoz = LcMsRandomator.fluctuateValue(ft.moz, mozErrorInDa, mozErrorInDa * 3)
        
        var newTime = mapAlnSet.calcTargetMapElutionTime(ft.elutionTime, Some(ft.mass) )
        newTime = LcMsRandomator.fluctuateValue(newTime, timeError, timeError * 3)
        
        val corrTime = reversedAlnSet.calcTargetMapElutionTime(newTime, Some(ft.mass))
        //println(ft.elutionTime+" "+corrTime)
        ft.copy( moz = newMoz, elutionTime = newTime, correctedElutionTime = Some(corrTime) )
      }
      
      fixedProcessedMaps += processedMap.copy( features = updatedFeatures )
      
      mapAlnSet
    }
    
    new MapSet(
      id = MapSet.generateNewId,
      name = "",
      creationTimestamp = new java.util.Date,
      childMaps = fixedProcessedMaps.toArray,
      alnReferenceMapId = refMapId,
      mapAlnSets = mapAlnSets.toArray
    )
  }
  
  private def _cloneRawMap( rawMap: RawMap ): RawMap = {
    val newRawMapId = RawMap.generateNewId
    
    val features = rawMap.features.map { ft => 
      val ftRelations = ft.relations.copy( rawMapId = newRawMapId )
      ft.copy( id = Feature.generateNewId, relations = ftRelations )
    }
    
    rawMap.copy( id = newRawMapId, creationTimestamp = new java.util.Date, features = features)
  }
  
}