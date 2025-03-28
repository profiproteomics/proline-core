package fr.proline.core.algo.lcms

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms._
import fr.profi.util.ms._
import fr.profi.util.math.getMedianObject


object ClusterIntensityComputation extends Enumeration {
  val MOST_INTENSE = Value("MOST_INTENSE")
  val SUM = Value("SUM")
}

object ClusterTimeComputation extends Enumeration {
  val MOST_INTENSE = Value("MOST_INTENSE")
  val MEDIAN = Value("MEDIAN")
}

object ClusterizeFeatures extends LazyLogging {

  val EPSILON = 1e-3

  val ftMozSortingFunc = new Function2[Feature, Feature, Boolean] {
    def apply(a: Feature, b: Feature): Boolean = if (a.moz < b.moz) true else false
  }
  
  val ftTimeSortingFunc = new Function2[Feature, Feature, Boolean] {
    def apply(a: Feature, b: Feature): Boolean = if (a.elutionTime < b.elutionTime) true else false
  }
  
  def apply(lcmsMap: ILcMsMap, scans: Seq[LcMsScan], params: ClusteringParams): ProcessedMap = {    
    val ftClusterer = new FeatureClusterer(lcmsMap,scans,params)
    ftClusterer.clusterizeFeatures()
  }
  
  def buildFeatureCluster(
    ftGroup: Seq[Feature],
    rawMapId: Long,
    procMapId: Long,
    intensityComputationMethod: ClusterIntensityComputation.Value,
    timeComputationMethod: ClusterTimeComputation.Value,
    scanById: Map[Long,LcMsScan]
  ): Feature = {
    
    // Flatten clusters into features if needed
    val unclusterizedFeatures = new ArrayBuffer[Feature](ftGroup.length)
    
    for( ft <- ftGroup ) {
      if( ft.isCluster ) {
        unclusterizedFeatures ++= ft.subFeatures
      } else {
        unclusterizedFeatures += ft
      }
    }
    
    // Set all features of the group to a clusterized status
    unclusterizedFeatures.foreach { _.isClusterized = true }

    // Compute some vars for the feature cluster
    val unclusterizedFtsAsList = unclusterizedFeatures.toList
    
    val intensity = this._computeClusterIntensity(unclusterizedFtsAsList, intensityComputationMethod)
    val elutionTime = this._computeClusterTime(unclusterizedFtsAsList, timeComputationMethod)

    val medianFtOpt = unclusterizedFtsAsList.find( ft => math.abs(ft.intensity - intensity) < EPSILON )
    val medianFt = medianFtOpt.getOrElse(getMedianObject(unclusterizedFtsAsList, this.ftMozSortingFunc))
    val moz = medianFt.moz

    // Set some vars
    val ms2EventIdSetBuilder = scala.collection.immutable.Set.newBuilder[Long]
    for (ft <- unclusterizedFeatures) {
      val ms2EventIds = ft.relations.ms2EventIds
      if (ms2EventIds != null) {
        for (ms2EventId <- ms2EventIds) ms2EventIdSetBuilder += ms2EventId
      }
    }
    val ms2EventIds = ms2EventIdSetBuilder.result().toArray[Long]
    val ms2Count = ms2EventIds.length
    val mostIntenseFt = this._findMostIntenseFeature(unclusterizedFtsAsList)
    val charge = mostIntenseFt.charge
    val qualityScore = mostIntenseFt.qualityScore
    //val apexIp = mostIntenseFt.apex
    val apexScanId = mostIntenseFt.relations.apexScanId
    val apexScanInitialId = mostIntenseFt.relations.apexScanInitialId
    val nbSubFts = unclusterizedFeatures.length

    // Determine first ft scan and last ft scan
    val subftsSortedByAscTime = unclusterizedFeatures.sortWith { (a, b) => a.elutionTime < b.elutionTime }
    val (firstSubft, lastSubft) = (subftsSortedByAscTime.head, subftsSortedByAscTime.last)
    val (firstScanId, firstScanInitialId) = (firstSubft.relations.firstScanId, firstSubft.relations.firstScanInitialId)
    val (lastScanId, lastScanInitialId) = (lastSubft.relations.lastScanId, lastSubft.relations.lastScanInitialId)
    val( firstScan, lastScan ) = (scanById(firstScanId),scanById(lastScanId))
    val ms1Count = 1 + lastScan.cycle - firstScan.cycle
    val duration = lastScan.time - firstScan.time

    val ftCluster = new Feature(
      id = Feature.generateNewId,
      moz = moz,
      charge = charge,
      intensity = intensity,
      elutionTime = elutionTime,
      duration = duration,
      ms1Count = ms1Count,
      ms2Count = ms2Count,
      subFeatures = subftsSortedByAscTime.toArray,
      isotopicPatterns = None,
      isOverlapping = false,
      overlappingFeatures = null,
      qualityScore = qualityScore,
      relations = new FeatureRelations(
        firstScanId = firstScanId,
        lastScanId = lastScanId,
        apexScanId = apexScanId,
        firstScanInitialId = firstScanInitialId,
        lastScanInitialId = lastScanInitialId,
        apexScanInitialId = apexScanInitialId,
        ms2EventIds = ms2EventIds,
        rawMapId = rawMapId,
        processedMapId = procMapId
      )

    )
    //ftCluster.apex(apexIp) if defined apexIp
    
    ftCluster
  }
  
  private def _computeClusterTime(features: List[Feature], method: ClusterTimeComputation.Value ): Float = {    
    method match {
      case ClusterTimeComputation.MEDIAN => getMedianObject(features, ftTimeSortingFunc).elutionTime
      case ClusterTimeComputation.MOST_INTENSE => this._findMostIntenseFeature(features).elutionTime
    }
  }

  private def _computeClusterIntensity(features: List[Feature], method: ClusterIntensityComputation.Value ): Float = {
    method match {
      case ClusterIntensityComputation.SUM => features.foldLeft(0f) { (r,c) => r + c.intensity }
      case ClusterIntensityComputation.MOST_INTENSE => this._findMostIntenseFeature(features).intensity
    }
    
  }
  
  private def _findMostIntenseFeature(features: List[Feature]): Feature = {
    
    features.maxBy(_.intensity) //VDS Warning maxBy may return wrong value if intensity contains NaN
    // Group features by intensity
    /*val ftByIntensity = features map { ft => (ft.intensity -> ft) }
    //push(@{ ftByIntensity( _.intensity ) }, _ ) for features
    val ftIntensities = keys(ftByIntensity)
    ftIntensities = sort { a <=> b } ftIntensities
    
    val ftMostIntense = ftByIntensity(ftIntensities(-1)).(0)
    return ftMostIntense*/

  }

}

class FeatureClusterer(
  lcmsMap: ILcMsMap,
  scans: Seq[LcMsScan],
  params: ClusteringParams
) extends LazyLogging {
  
  private val( rawMapId, procMapId ) = lcmsMap match {
    case procMap: ProcessedMap => (0L,procMap.id)
    case rawMapId: RawMap => (rawMapId.id,0L)
  }
  
  // Retrieve parameters
  private val mozTol = params.mozTol
  private val mozTolUnit = params.mozTolUnit
  private val timeTol = params.timeTol
  
  private val intensityComputationMethod = try {
    ClusterIntensityComputation.withName( params.intensityComputation.toUpperCase() )
  } catch {
    case _: Throwable => throw new Exception("the cluster intensity computation method '" + params.intensityComputation + "' is not implemented")
  }
    
  private val timeComputationMethod = try {
    ClusterTimeComputation.withName( params.timeComputation.toUpperCase() )
  } catch {
    case _: Throwable => throw new Exception("the cluster time computation method '" + params.timeComputation + "' is not implemented")
  }

  // Retrieve some vars
  private val scanById = scans.map { scan => (scan.id -> scan) }.toMap
  
  def buildFeatureCluster( ftGroup: Seq[Feature] ): Feature = {
    ClusterizeFeatures.buildFeatureCluster(ftGroup, rawMapId, procMapId, intensityComputationMethod, timeComputationMethod, scanById)
  }

  def clusterizeFeatures(): ProcessedMap = {

    val features = lcmsMap.features

    val featuresByCharge = features.groupBy(_.charge)
    val chargeStates = featuresByCharge.keys.toList.sorted

    // Iterate over charge states
    val singleFeatures = new ArrayBuffer[Feature]
    val totalFtGroups = new ArrayBuffer[ArrayBuffer[Feature]]
    // ArrayBuffer.fill[Feature](1,2)(null)
    //Array.ofDim[Feature]()

    for (charge <- chargeStates) {
      logger.info("charge = " + charge)

      val sameChargeFeatures = featuresByCharge.get(charge).get

      // Sort features by m/z
      var ftsSortedByMoz = sameChargeFeatures.sortBy( _.moz )

      // Set some vars
      var prevFt = ftsSortedByMoz.head
      val ftsGroupedByMoz = new ArrayBuffer[ArrayBuffer[Feature]](1)
      ftsGroupedByMoz += ArrayBuffer(prevFt)
      var ftGroupIdx = 0

      // Cluster feature by moz
      for (ftIdx <- 1 until ftsSortedByMoz.length) {

        val ft = ftsSortedByMoz(ftIdx)
        val ftMoz = ft.moz

        // Compute m/z tolerance in Dalton
        val mozTolInDalton = calcMozTolInDalton(ftMoz, mozTol, mozTolUnit)
        if (math.abs(ftMoz - prevFt.moz) > mozTolInDalton) {
          // Append new group of features because current m/z is too far from the previous one
          ftGroupIdx += 1
          ftsGroupedByMoz += new ArrayBuffer[Feature](1)
        }

        // Append feature to its corresponding group
        ftsGroupedByMoz(ftGroupIdx) += ft

        // Replace previous feature by the current one
        prevFt = ft
      }

      // Clusterize features by time
      val sameChargeFtGroups = new ArrayBuffer[ArrayBuffer[Feature]]
      for (ftGroup <- ftsGroupedByMoz) {
        if (ftGroup.length == 1) {
          singleFeatures += ftGroup.head
        } else {
          val tmpFtsGroupedByTime = this._splitFtGroupByTime(ftGroup)

          // Iterate over features grouped by time dimension
          for (tmpFtGroup <- tmpFtsGroupedByTime) {
            if (tmpFtGroup.length == 1) {
              // Put aside unclusterized features
              singleFeatures ++= tmpFtGroup
            } else { sameChargeFtGroups += tmpFtGroup }
          }
        }
      }

      // Filtering same charge ft groups to remove m/z conflicts
      for (ftGroup <- sameChargeFtGroups) {
        val mozTolInDalton = calcMozTolInDalton(ftGroup.head.moz, mozTol, mozTolUnit)
        val mozSplitFtGroups = this._splitFtGroupByMoz(ftGroup, mozTolInDalton)

        for (mozSplitFtGroup <- mozSplitFtGroups) {

          // If we retrieve more than one feature in the group
          if (mozSplitFtGroup.length >= 2) {

            // Split again by time
            val timeSplitFtGroups = this._splitFtGroupByTime(mozSplitFtGroup)

            for (tmpFtGroup <- timeSplitFtGroups) {
              // If we retrieve only one feature in the group
              if (tmpFtGroup.length == 1) { singleFeatures += tmpFtGroup.head }
              // Else => more than one feature in the group
              else { totalFtGroups += tmpFtGroup }
            }

            // Else put aside alone feature
          } else { singleFeatures += mozSplitFtGroup.head }
        }
      }

      this.logger.info("number of detected feature clusters: " + totalFtGroups.length)
    }

    // Check if single features don't contain duplicated objects
    val tmpIdHash = new java.util.HashMap[Long, Long]
    for (ft <- singleFeatures) {
      if (tmpIdHash containsKey ft.id) throw new Exception("duplicated feature detected")
      tmpIdHash.put(ft.id, 1)
    }

    // Convert total ft groups into feature clusters
    val ftClusters = for (totalFtGroup <- totalFtGroups)
      yield ClusterizeFeatures.buildFeatureCluster(
        totalFtGroup,
        rawMapId,
        procMapId,
        intensityComputationMethod,
        timeComputationMethod,
        scanById
      )

    // Add feature clusters to clusterized_features
    val clusterizedFeatures = (singleFeatures ++ ftClusters).toArray

    val newLcmsMap = if (lcmsMap.isProcessed) {

      // Create new processed map from existing processed map
      lcmsMap.asInstanceOf[ProcessedMap].copy(
        id = ProcessedMap.generateNewId(),
        creationTimestamp = new java.util.Date(),
        modificationTimestamp = new java.util.Date(),
        features = clusterizedFeatures,
        isLocked = false
      )

    } else {
      // Create new processed map from existing run map
      lcmsMap.asInstanceOf[RawMap].toProcessedMap(
        number = 0,
        mapSetId = 0,
        features = clusterizedFeatures
      )
    }

    newLcmsMap
  }

  private def _splitFtGroupByTime(ftGroup: Seq[Feature]): ArrayBuffer[ArrayBuffer[Feature]] = {

    // Sort features by first scan time
    val nbFts = ftGroup.length
    val ftsSortedByTime = ftGroup.sortBy( ft => scanById(ft.relations.firstScanId).time )
    
    // Set some vars
    val ftsGroupedByTime = new ArrayBuffer[ArrayBuffer[Feature]](1)
    val ftGroupIdxByAssignedFtId = new collection.mutable.HashMap[Long, Long]
    var( curFtIdx, ftGroupIdx ) = (0,0)

    // Clusterize features by time
    for (putativeFirstCft <- ftsSortedByTime) {
      curFtIdx += 1

      // Skip feature if it has been already assigned to a group
      if ( ftGroupIdxByAssignedFtId.contains(putativeFirstCft.id) == false ) {
        ftsGroupedByTime += ArrayBuffer(putativeFirstCft)
        ftGroupIdxByAssignedFtId += (putativeFirstCft.id -> ftGroupIdx)
        
        //val clusterFirstTime = scanById(putativeFirstCft.relations.firstScanId).time
        val clusterLastTime = scanById(putativeFirstCft.relations.lastScanId).time
        //var (minTime, maxTime) = (clusterFirstTime - timeTol, clusterLastTime + timeTol)
        var maxTime = clusterLastTime + timeTol
        
        // Search for other cluster features
        var reachedFarthestFeature = false
        for (tmpFtIdx <- curFtIdx until nbFts) {
          // TODO: use break instead of if statement
          if (reachedFarthestFeature == false) {
            val tmpFt = ftsSortedByTime(tmpFtIdx)

            // Skip feature if it has been already assigned to a group
            if ( ftGroupIdxByAssignedFtId.contains(tmpFt.id) == false ) {

              val tmpFtFirstTime = scanById(tmpFt.relations.firstScanId).time
              if (tmpFtFirstTime <= maxTime) {
                ftsGroupedByTime(ftGroupIdx) += tmpFt
                maxTime = scanById(tmpFt.relations.lastScanId).time + timeTol
                ftGroupIdxByAssignedFtId += (tmpFt.id -> ftGroupIdx)
              } else {
                reachedFarthestFeature = true
              }
            }
          }
        }

        // Increment group index for next putative first cluster feature
        ftGroupIdx += 1
      }
    }

    ftsGroupedByTime

  }

  private def _splitFtGroupByMoz(ftGroup: Seq[Feature], mozTolInDalton: Double): ArrayBuffer[Seq[Feature]] = {

    val nbFeatures = ftGroup.length
    if (nbFeatures == 0) return ArrayBuffer.empty[Seq[Feature]]

    // Sort features by m/z
    val sortedFts = ftGroup.sortBy( _.moz )

    // Check if first ft moz is too far from last ft moz
    val firstFt = sortedFts.head
    val lastFt = sortedFts.last
    var ftsGroupedByMz = new ArrayBuffer[Seq[Feature]]
    
    // If there is only one feature in the group or if the features are close in m/z
    if (nbFeatures == 1 || math.abs(firstFt.moz - lastFt.moz) < mozTolInDalton) {
      // Keep the input feature group as is
      ftsGroupedByMz = ArrayBuffer(ftGroup)
      // Else split feature group into new groups
    } else {

      // Retrieve the median object which is taken as a reference
      val refFt = getMedianObject(sortedFts, ClusterizeFeatures.ftMozSortingFunc)
      val refFtId = refFt.id
      val refFtMoz = refFt.moz

      // Aggregate features which are close in m/z to the reference
      val tmpFtGroup = ArrayBuffer(refFt)
      val tmpOtherFts = new ArrayBuffer[Feature]
      for (ft <- sortedFts) {
        if (ft.id != refFtId) {
          if (math.abs(ft.moz - refFtMoz) < mozTolInDalton) { tmpFtGroup += ft }
          else { tmpOtherFts += ft }
        }
      }

      // Append the new group to the list of groups
      ftsGroupedByMz += tmpFtGroup

      // Check if we need to split the group again (recursive method call)
      val nbOtherFts = tmpOtherFts.length
      if (nbOtherFts > 1) {
        val otherFtGroups = this._splitFtGroupByMoz(tmpOtherFts, mozTolInDalton)
        ftsGroupedByMz ++= otherFtGroups
      } else if (nbOtherFts == 1) { ftsGroupedByMz += tmpOtherFts }
    }

    return ftsGroupedByMz
  }

}
