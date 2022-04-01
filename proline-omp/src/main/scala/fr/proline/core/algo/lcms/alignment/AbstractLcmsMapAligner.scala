package fr.proline.core.algo.lcms.alignment

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.core.algo.lcms.{AlignmentConfig, AlnSmoother, FeatureMapper}
import fr.proline.core.algo.msq.profilizer.CommonsStatHelper
import fr.proline.core.om.model.lcms._

import scala.annotation.tailrec
import scala.collection.mutable.{ArrayBuffer, LongMap}

case class AlignmentResult( alnRefMapId: Long, mapAlnSets: Array[MapAlignmentSet] )

abstract class AbstractLcmsMapAligner extends LazyLogging {

  def computeMapAlignmentsUsingCustomFtMapper(
    lcmsMaps: Seq[ProcessedMap],
    alnConfig: AlignmentConfig
  )(ftMapper: (Seq[Feature],Seq[Feature]) => LongMap[_ <: Seq[Feature]]): AlignmentResult

  def determineAlnReferenceMap(
    lcmsMaps: Seq[ProcessedMap],
    mapAlnSets: Seq[MapAlignmentSet],
    currentRefMap: ProcessedMap
  ): ProcessedMap

  def computeMapAlignments(
    lcmsMaps: Seq[ProcessedMap],
    alnConfig: AlignmentConfig
  ): AlignmentResult = {
    this.computeMapAlignmentsUsingCustomFtMapper(lcmsMaps,alnConfig) { (map1Features, map2Features) =>
      FeatureMapper.computePairwiseFtMapping( map1Features, map2Features, alnConfig.ftMappingMethodParams)
    }
  }

  protected def computePairwiseAlnSet(
    map1: ProcessedMap,
    map2: ProcessedMap,
    ftMapper: (Seq[Feature],Seq[Feature]) => LongMap[_ <: Seq[Feature]],
    alnConfig: AlignmentConfig
  ): Option[MapAlignmentSet] = {

    try {

      logger.info("Aligning map {} to map {} ", map1.name, map2.name)

      val massInterval = if (alnConfig.methodParams.isDefined) alnConfig.methodParams.get.massInterval.getOrElse(20000) else 20000
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

          landmarksByMassIdx.getOrElseUpdate(massRangePos, new ArrayBuffer[Landmark]) += Landmark(map1Ft.elutionTime, deltaTime)
        }
      }

      // Create an alignment smoother
      val smoothingMethod = alnConfig.smoothingMethodName
      val alnSmoother = AlnSmoother( method = smoothingMethod )

      // Compute feature alignments
      val ftAlignments = new ArrayBuffer[MapAlignment](0)

      for ((massRangeIdx,landmarks) <- landmarksByMassIdx) {

        if (!landmarks.isEmpty) {
          val landmarksSortedByTime = landmarks.sortBy(l => (l.x,l.dx))

          //Landmark._toCSVFile(List("RT", map1.name, map2.name, massRangeIdx).mkString("_") + ".csv", landmarksSortedByTime)

          var smoothedLandmarks = alnSmoother.smoothLandmarks(landmarksSortedByTime, alnConfig.smoothingMethodParams)
          // FIXME: this should not be empty
          if (smoothedLandmarks.isEmpty) {
            logger.warn("Empty array of smoothed Landmarks, use the original landmarks instead")
            smoothedLandmarks = landmarksSortedByTime
          }

          val (timeList, deltaTimeList, toleranceTimeList) = (new ArrayBuffer[Float](smoothedLandmarks.length), new ArrayBuffer[Float](smoothedLandmarks.length), new ArrayBuffer[Float](smoothedLandmarks.length))
          var prevTimePlusDelta = smoothedLandmarks(0).x.toFloat + smoothedLandmarks(0).dx.toFloat - 1
          var prevTime = -1f
          smoothedLandmarks.sortBy(_.x).foreach { lm =>

            val timePlusDelta = lm.x.toFloat + lm.dx.toFloat

            // Filter time+delta values which are not greater than the previous one
            if (lm.x > prevTime && timePlusDelta > prevTimePlusDelta) {
              timeList += lm.x.toFloat
              deltaTimeList += lm.dx.toFloat
              toleranceTimeList += lm.tx.toFloat
              prevTime = lm.x.toFloat
              prevTimePlusDelta = timePlusDelta
            }
          }

          val mapAlignment = new MapAlignment(
            refMapId = map1.id,
            targetMapId = map2.id,
            massRange = (massRangeIdx * massInterval, (massRangeIdx + 1) * massInterval),
            timeList = timeList.toArray,
            deltaTimeList = deltaTimeList.toArray,
            properties = Some(MapAlignmentProperties(toleranceTimeList = toleranceTimeList.toArray))
          )

          ftAlignments += mapAlignment //alnSmoother.smoothMapAlignment( landmarksSortedByTime, alnParams.smoothingParams )
        } else {
          logger.warn("No landmarks found for massRange {} from map #{} and map #{}", massRangeIdx, map1.id, map2.id)
        }
      }

      if( ftAlignments.isEmpty ) {
        throw new Exception("No feature alignments have been found")
      } else Some(
        new MapAlignmentSet(
          refMapId = map1.id,
          targetMapId = map2.id,
          mapAlignments = ftAlignments.toArray
        )
      )

    } catch {
      case e: Throwable => {
        val errorMsg = s"Can't compute map alignment set between map #${map1.id} and map #${map2.id} because: ${e.getMessage}"
        this.logger.warn("error during alignement:", e)
        if (alnConfig.ignoreErrors.getOrElse(false)) {
          None
        } else {
          throw new Exception(e)
        }
      }
    }

  }

  protected def removeAlignmentOutliers(alnResult: AlignmentResult): AlignmentResult = {

    val filteredMapAlnSets = alnResult.mapAlnSets.map { mapAlnSet =>
      val filteredMapAlns = mapAlnSet.mapAlignments.map { mapAln =>
        this._removeMapAlnOutliers(mapAln, remainingIterations = 3)
      }
      mapAlnSet.copy( mapAlignments = filteredMapAlns)
    }

    alnResult.copy(mapAlnSets = filteredMapAlnSets)
  }

  // Remove outliers recursively
  @tailrec
  private def _removeMapAlnOutliers(mapAln: MapAlignment, remainingIterations: Int): MapAlignment = {
    if (remainingIterations == 0) return mapAln


    val timeList = mapAln.timeList
    val dataPointsCount = timeList.length
    val deltaTimeList = mapAln.deltaTimeList
    val properties = mapAln.properties.getOrElse(MapAlignmentProperties(Array.fill[Float](dataPointsCount)(0.0f)))

    val deltaTimeDiffs = deltaTimeList.sliding(2).map { deltaTimePair =>
      (deltaTimePair(1) - deltaTimePair(0)).toDouble
    } toArray

    // Compute delta time difference statistics and determine outlier threshold
    val deltaTimeStatSummary = CommonsStatHelper.calcExtendedStatSummary(deltaTimeDiffs)
    val lowerInnerFence = deltaTimeStatSummary.getLowerOuterFence()
    val upperInnerFence = deltaTimeStatSummary.getUpperOuterFence()
    val maxAbsDiff = math.max( math.abs(lowerInnerFence), math.abs(upperInnerFence) )

    // Create new lists
    val newTimeList = new ArrayBuffer[Float](dataPointsCount)
    val newDeltaTimeList = new ArrayBuffer[Float](dataPointsCount)
    val newToleranceTimeList = new ArrayBuffer[Float](dataPointsCount)

    // Add first data point to the new list
    newTimeList += timeList(0)
    newDeltaTimeList += deltaTimeList(0)

    // Search for outliers
    for ( (deltaTimeDiff,i) <- deltaTimeDiffs.zipWithIndex) {

      val isOutlier = math.abs(deltaTimeDiff) > maxAbsDiff

      if (!isOutlier) {
        val dataPointIdx = i + 1
        newTimeList += timeList(dataPointIdx)
        newDeltaTimeList += deltaTimeList(dataPointIdx)
        newToleranceTimeList += properties.toleranceTimeList(dataPointIdx)
      }
    }

    val map1 = mapAln.refMapId
    val map2 = mapAln.targetMapId

    // Check if we found outliers
    val outliersCount = dataPointsCount - newTimeList.length
    if (outliersCount == 0) {
      logger.debug(
        s"No more outlier in LC-MS alignment between map with ID=$map1 and map with ID=$map2"
      )
      return mapAln
    }

    logger.debug(
      s"Removed $outliersCount outlier(s) in LC-MS alignment between map with ID=$map1 and map with ID=$map2"
    )

    // Create a copy of map alignment if outliers were found
    val filteredMapAln = mapAln.copy(
      timeList = newTimeList.toArray,
      deltaTimeList = newDeltaTimeList.toArray,
      properties = Some(properties.copy(toleranceTimeList = newToleranceTimeList.toArray))
    )

    this._removeMapAlnOutliers(filteredMapAln, remainingIterations - 1)
  }

}