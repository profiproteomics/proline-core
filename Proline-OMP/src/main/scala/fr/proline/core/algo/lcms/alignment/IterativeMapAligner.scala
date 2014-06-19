package fr.proline.core.algo.lcms.alignment

import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.algo.lcms.AlignmentParams

class IterativeMapAligner extends ILcmsMapAligner with Logging {

  import fr.proline.core.om.model.lcms._
  import fr.profi.util.math.getMedianObject

  def computeMapAlignments(lcmsMaps: Seq[ProcessedMap], alnParams: AlignmentParams): AlignmentResult = {

    // Select randomly a reference map
    val refMapRandomIndex = (math.random * lcmsMaps.length).toInt
    val randomRefMap = lcmsMaps(refMapRandomIndex)

    this.findBestMapAlignments(lcmsMaps, randomRefMap, alnParams, 1)

  }

  private def findBestMapAlignments(lcmsMaps: Seq[ProcessedMap],
                                    alnRefMap: ProcessedMap,
                                    alnParams: AlignmentParams,
                                    iterationNum: Int): AlignmentResult = {

    // Retrieve some vars
    val maxIterNum = alnParams.maxIterations
    val alnRefMapId = alnRefMap.id

    // Iterate over maps to compute alignments with the random or new reference map
    //val mapAlnSets = lcmsMaps filter { _.id != alnRefMap.id } map { this.computePairwiseAlnSet( alnRefMap, _, alnParams) }
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

  private def computeMapAlnSets(lcmsMaps: Seq[ProcessedMap],
                                alnRefMap: ProcessedMap,
                                alnParams: AlignmentParams): Seq[MapAlignmentSet] = {
    //print "computing feature alignments...\n"

    // Iterate over maps to compute alignments with the random or new reference map
    lcmsMaps
      .withFilter { _.id != alnRefMap.id }
      .map { this.computePairwiseAlnSet(alnRefMap, _, alnParams) }
      .withFilter { _.isDefined }
      .map { _.get }
  }

  def determineAlnReferenceMap(lcmsMaps: Seq[ProcessedMap],
                               mapAlnSets: Seq[MapAlignmentSet],
                               currentRefMap: ProcessedMap): ProcessedMap = {

    if (lcmsMaps.length <= 2) return lcmsMaps(0)

    val mapAlnSetByMapId = mapAlnSets.map { alnSet => (alnSet.targetMapId -> alnSet) } toMap
    val mapDistanceByIdBuilder = scala.collection.immutable.Map.newBuilder[Long, Float]
    mapDistanceByIdBuilder += (currentRefMap.id -> 0)

    for (map <- lcmsMaps) {
      if (map.id != currentRefMap.id) { // skip current reference map

        val mapAlnSet = mapAlnSetByMapId(map.id)

        var distance: Float = 0 // mean of delta times
        var nbLandmarks = 0

        for (mapAln <- mapAlnSet.mapAlignments) {
          val deltaTimeList = mapAln.deltaTimeList.toList
          distance += deltaTimeList.sum
          nbLandmarks += deltaTimeList.length
        }

        if (distance > 0) distance /= nbLandmarks

        mapDistanceByIdBuilder += (map.id -> distance)

        //print map.id .": " .distance."\n"
      }
    }

    //print "map distance by map id:\n"
    //print Dumper( mapDistanceById )
    val mapDistanceById = mapDistanceByIdBuilder.result()

    val mapDistanceSortFunc = new Function2[Long, Long, Boolean] {
      def apply(a: Long, b: Long): Boolean = if (mapDistanceById(a) < mapDistanceById(a)) true else false
    }

    val medianMapId = getMedianObject(mapDistanceById.keys.toList, mapDistanceSortFunc)
    //print "ref map_id: ".medianMapId."\n"

    lcmsMaps.find { _.id == medianMapId } get

  }

}