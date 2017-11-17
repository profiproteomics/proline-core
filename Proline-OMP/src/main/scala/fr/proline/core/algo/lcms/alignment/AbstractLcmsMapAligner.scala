package fr.proline.core.algo.lcms.alignment

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.collection._
import fr.proline.core.algo.lcms.AlignmentParams
import fr.proline.core.algo.lcms.AlnSmoother
import fr.proline.core.algo.lcms.FeatureMapper
import fr.proline.core.algo.lcms.FeatureMappingParams
import fr.proline.core.algo.msq.profilizer.CommonsStatHelper
import fr.proline.core.om.model.lcms._

case class AlignmentResult( alnRefMapId: Long, mapAlnSets: Array[MapAlignmentSet] )

abstract class AbstractLcmsMapAligner extends LazyLogging {

  def computeMapAlignmentsUsingCustomFtMapper(
    lcmsMaps: Seq[ProcessedMap],
    alnParams: AlignmentParams
  )(ftMapper: (Seq[Feature],Seq[Feature]) => LongMap[_ <: Seq[Feature]]): AlignmentResult
  
  def determineAlnReferenceMap(
    lcmsMaps: Seq[ProcessedMap],
    mapAlnSets: Seq[MapAlignmentSet],
    currentRefMap: ProcessedMap
  ): ProcessedMap
  
  def computeMapAlignments(
    lcmsMaps: Seq[ProcessedMap],
    alnParams: AlignmentParams
  ): AlignmentResult = {
    this.computeMapAlignmentsUsingCustomFtMapper(lcmsMaps,alnParams) { (map1Features, map2Features) =>
      FeatureMapper.computePairwiseFtMapping( map1Features, map2Features, alnParams.ftMappingParams )
    }
  }
  
  protected def computePairwiseAlnSet(
    map1: ProcessedMap,
    map2: ProcessedMap,
    ftMapper: (Seq[Feature],Seq[Feature]) => LongMap[_ <: Seq[Feature]],
    alnParams: AlignmentParams
  ): Option[MapAlignmentSet] = {
    
    val massInterval = alnParams.massInterval
    //val timeInterval = alnParams.timeInterval
    
    val( map1Features, map2Features ) = ( map1.features, map2.features )
    val ftMapping = ftMapper( map1Features, map2Features)
    
    val map1FtById = map1Features.mapByLong(_.id)
    
    /*
    println("*****")
    for ((map1FtId,matchingFts) <- ftMapping; val map1Ft = map1FtById(map1FtId); matchingFt <- matchingFts) {
     println(s"${map1Ft.elutionTime}\t${matchingFt.elutionTime}")
    }
    println("#####")
    */
        
    // two possibilities: keep nearest mass match or exclude matching conflicts (more than one match)
    val landmarksByMassIdx = new LongMap[ArrayBuffer[Landmark]]
    
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
   
    for ((massRangeIdx,landmarks) <- landmarksByMassIdx if landmarks.isEmpty == false) {
      
      val landmarksSortedByTime = landmarks.sortBy( _.time )
      var smoothedLandmarks = alnSmoother.smoothLandmarks( landmarksSortedByTime, alnParams.smoothingParams )
      // FIXME: this should not be empty
      if( smoothedLandmarks.isEmpty ) smoothedLandmarks = landmarksSortedByTime
      
      /*val timeList = landmarksSortedByTime.map { _.time }
      val deltaTimeList = landmarksSortedByTime.map { _.deltaTime }*/
      
      val( timeList, deltaTimeList ) = ( new ArrayBuffer[Float](smoothedLandmarks.length), new ArrayBuffer[Float](smoothedLandmarks.length) )
      var prevTimePlusDelta = smoothedLandmarks(0).time + smoothedLandmarks(0).deltaTime - 1
      var prevTime = -1f
      smoothedLandmarks.sortBy( _.time ).foreach { lm =>
        
        val timePlusDelta = lm.time + lm.deltaTime
        
        // Filter time+delta values which are not greater than the previous one
        if( lm.time > prevTime && timePlusDelta > prevTimePlusDelta ) {
          timeList += lm.time
          deltaTimeList += lm.deltaTime
          prevTime = lm.time
          prevTimePlusDelta = timePlusDelta
        }
      }
      
      val mapAlignment = new MapAlignment(
        refMapId = map1.id,
        targetMapId = map2.id,
        massRange = (massRangeIdx*massInterval,(massRangeIdx+1)*massInterval),
        timeList = timeList.toArray,
        deltaTimeList = deltaTimeList.toArray
      )
      
      ftAlignments += mapAlignment //alnSmoother.smoothMapAlignment( landmarksSortedByTime, alnParams.smoothingParams )
    }
    
    if( ftAlignments.isEmpty ) {
      this.logger.warn(s"can't compute map alignment set between map #${map1.id} and map #${map2.id}")
      None
    }
    else Some(
      new MapAlignmentSet(
        refMapId = map1.id,
        targetMapId = map2.id,
        mapAlignments = ftAlignments.toArray
      )
    )
    
  }
  
  protected def removeAlignmentOutliers(alnResult: AlignmentResult): AlignmentResult = {
    
    val filteredMapAlnSets = alnResult.mapAlnSets.map { mapAlnSet =>
      val filteredMapAlns = mapAlnSet.mapAlignments.map { mapAln =>
        
        val timeList = mapAln.timeList
        val deltaTimeList = mapAln.deltaTimeList
        
        val deltaTimeDiffs = deltaTimeList.sliding(2).map { deltaTimePair =>
          (deltaTimePair(1) - deltaTimePair(0)).toDouble
        } toArray
        
        // Compute delta time difference statistics and determine outlier threshold
        val deltaTimeStatSummary = CommonsStatHelper.calcExtendedStatSummary(deltaTimeDiffs)
        val lowerInnerFence = deltaTimeStatSummary.getLowerInnerFence()
        val upperInnerFence = deltaTimeStatSummary.getUpperInnerFence()
        val maxAbsDiff = math.max( math.abs(lowerInnerFence), math.abs(upperInnerFence) )
        
        // Create new lists
        val dataPointsCount = timeList.length
        val newTimeList = new ArrayBuffer[Float](dataPointsCount)
        val newDeltaTimeList = new ArrayBuffer[Float](dataPointsCount)
        
        // Add first data point to the new list
        newTimeList += timeList(0)
        newDeltaTimeList += deltaTimeList(0)
        
        // Check for outliers
        for ( (deltaTimeDiff,i) <- deltaTimeDiffs.zipWithIndex) {
          
          val isOutlier = math.abs(deltaTimeDiff) > maxAbsDiff
          
          if (!isOutlier) {
            val dataPointIdx = i + 1
            newTimeList += timeList(dataPointIdx)
            newDeltaTimeList += deltaTimeList(dataPointIdx)
          }
        }
        
        // Create a copy of map alignment only if outliers were found
        if (newTimeList.length == dataPointsCount) mapAln
        else {
          val map1 = mapAln.refMapId
          val map2 = mapAln.targetMapId
          
          val outliersCount = dataPointsCount - newTimeList.length
          logger.debug(
            s"Removed $outliersCount outlier(s) in LC-MS alignment between map with ID=$map1 and map with ID=$map2"
          )
          
          mapAln.copy(
            timeList = newTimeList.toArray,
            deltaTimeList = newDeltaTimeList.toArray
          )
        }
      }
      
      mapAlnSet.copy( mapAlignments = filteredMapAlns)
    }
    
    alnResult.copy(mapAlnSets = filteredMapAlnSets)
  }

}