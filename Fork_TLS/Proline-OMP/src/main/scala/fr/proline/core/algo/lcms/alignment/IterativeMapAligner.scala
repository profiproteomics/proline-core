package fr.proline.core.algo.lcms.alignment

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.math.getMedianObject
import fr.proline.core.algo.lcms.AlignmentParams
import fr.proline.core.om.model.lcms._

class IterativeMapAligner extends AbstractLcmsMapAligner with LazyLogging {

  def computeMapAlignments(lcmsMaps: Seq[ProcessedMap], alnParams: AlignmentParams): AlignmentResult = {

    // Select randomly a reference map
    val refMapRandomIndex = (math.random * lcmsMaps.length).toInt
    val randomRefMap = lcmsMaps(refMapRandomIndex)

    this.findBestMapAlignments(lcmsMaps, randomRefMap, alnParams, 1)
  }

  private def findBestMapAlignments(
    lcmsMaps: Seq[ProcessedMap],
    alnRefMap: ProcessedMap,
    alnParams: AlignmentParams,
    iterationNum: Int
  ): AlignmentResult = {

    // Retrieve some vars
    val maxIterNum = alnParams.maxIterations
    val alnRefMapId = alnRefMap.id

    // Iterate over maps to compute alignments with the random or new reference map
    val mapAlnSets = this.computeMapAlnSets(lcmsMaps, alnRefMap, alnParams)

    // Determine the best alignment reference map using the previously computed alignements
    logger.info("determining best alignment reference map (#iteration=" + iterationNum + ")...")
    val newAlnRefMap = this.determineAlnReferenceMap(lcmsMaps, mapAlnSets, alnRefMap)

    // Return if the new reference map is identical to the previous one
    // or if the maximum number of iterations has been reached
    if (newAlnRefMap.id == alnRefMapId) {
      return AlignmentResult(newAlnRefMap.id, mapAlnSets.toArray)
    } else if (iterationNum >= maxIterNum) {
      // Compute the feature alignments again using the new reference map      
      val mapAlnSets = this.computeMapAlnSets(lcmsMaps, newAlnRefMap, alnParams)
      return AlignmentResult(newAlnRefMap.id, mapAlnSets.toArray)
    } else {

      // Compute the map alignments again using the new reference map
      return this.findBestMapAlignments(lcmsMaps, newAlnRefMap, alnParams, iterationNum + 1)
    }

  }

  private def computeMapAlnSets(
    lcmsMaps: Seq[ProcessedMap],
    alnRefMap: ProcessedMap,
    alnParams: AlignmentParams
  ): Seq[MapAlignmentSet] = {
    //print "computing feature alignments...\n"

    // Iterate over maps to compute alignments with the random or new reference map
    lcmsMaps
      .withFilter { _.id != alnRefMap.id }
      .map { this.computePairwiseAlnSet(alnRefMap, _, alnParams) }
      .withFilter { _.isDefined }
      .map { _.get }
  }

  def determineAlnReferenceMap(
    lcmsMaps: Seq[ProcessedMap],
    mapAlnSets: Seq[MapAlignmentSet],
    currentRefMap: ProcessedMap
  ): ProcessedMap = {
    require(lcmsMaps.nonEmpty, "lcmsMaps is empty")

    if (lcmsMaps.length <= 2) return lcmsMaps(0)

    val mapAlnSetByMapId = mapAlnSets.map { alnSet => (alnSet.targetMapId -> alnSet) } toMap
    val mapDistanceByIdBuilder = scala.collection.immutable.Map.newBuilder[Long, Float]
    mapDistanceByIdBuilder += (currentRefMap.id -> 0)

    for (map <- lcmsMaps) {
      if (map.id != currentRefMap.id) { // skip current reference map

        mapAlnSetByMapId.get(map.id).map { mapAlnSet =>

          var meanDistance = 0f // mean of delta times (without absolute value conversion)
          var nbLandmarks = 0

          for (mapAln <- mapAlnSet.mapAlignments) {
            val deltaTimeList = mapAln.deltaTimeList.toList
            meanDistance += deltaTimeList.sum
            nbLandmarks += deltaTimeList.length
          }

          if (meanDistance > 0) meanDistance /= nbLandmarks

          mapDistanceByIdBuilder += (map.id -> meanDistance)
        }
      }
    }

    val mapDistanceById = mapDistanceByIdBuilder.result()

    val mapDistanceSortFunc = (a: Long, b: Long) => if (mapDistanceById(a) < mapDistanceById(b)) true else false
    val medianMapId = getMedianObject(mapDistanceById.keys.toList,mapDistanceSortFunc)

    lcmsMaps.find( _.id == medianMapId ).get
  }

}