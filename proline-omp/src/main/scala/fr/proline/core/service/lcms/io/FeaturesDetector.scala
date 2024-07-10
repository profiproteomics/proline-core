package fr.proline.core.service.lcms.io

import com.typesafe.scalalogging.LazyLogging
import fr.profi.ms.algo.IsotopePatternInterpolator
import fr.profi.mzdb._
import fr.profi.mzdb.algo.feature.extraction.FeatureExtractorConfig
import fr.profi.mzdb.io.reader.provider.RunSliceDataProvider
import fr.profi.mzdb.model.{PutativeFeature, Feature => MzDbFeature}
import fr.profi.util.collection._
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentResult
import fr.proline.core.algo.msq.config._
import fr.proline.core.om.model.lcms.{Feature => LcMsFeature, _}
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.service.lcms.CreateMapSet
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import java.io.File
import scala.collection.mutable.{ArrayBuffer, HashMap, LongMap}


class FeaturesDetector (
  lcmsDbCtx: LcMsDbConnectionContext,
  mapSetName: String,
  lcMsRuns: Seq[LcMsRun],
  quantConfig: ILcMsQuantConfig,
  val peptideByRunIdAndScanNumber: Option[LongMap[LongMap[Peptide]]]
) extends AbstractMapSetDetector(lcmsDbCtx, mapSetName, lcMsRuns, quantConfig) with LazyLogging {
  
  // Do some requirements
  require(quantConfig.extractionParams.mozTolUnit matches "(?i)PPM")
  
  require(
    lcMsRuns.map(_.number).filter(_ > 0).distinct.length == lcMsRuns.length,
    "Invalid LC-MS run numbers: numbers should distinct and strictly positive"
  )

  protected val pps = new PeakPickingSoftware(
    id = -1,
    name = "Proline",
    version = new fr.proline.core.service.Version().getVersion.split("-").head,
    algorithm = "ExtractMapSet"
  )

  def extractMapSet(
    mzDbFileByLcMsRunId: LongMap[File],
    mapSetId: Long
  ): (MapSet, AlignmentResult) = {
    
    val sortedLcMsRuns = this.sortedLcMsRuns

    var mapIdx = 0
    val mapsCount = sortedLcMsRuns.length

    // --- Extract raw maps and convert them to processed maps ---
    val processedMaps = new Array[ProcessedMap](mapsCount)
    val lcmsRunByProcMapId = new LongMap[LcMsRun](mapsCount)
    val mzDbFileByProcMapId = new LongMap[File](mapsCount)
    val scanSeqByRunId = new LongMap[LcMsScanSequence](mapsCount)

    for (lcmsRun <- sortedLcMsRuns) {
      val scanSeq = lcmsRun.scanSequence.get
      scanSeqByRunId += lcmsRun.id -> scanSeq

      val mzDbFile = mzDbFileByLcMsRunId(lcmsRun.id)

      // Extract LC-MS map from the mzDB file
      val processedMap = this._extractProcessedMap(lcmsRun, mzDbFile, mapIdx + 1, mapSetId)

      // Update some mappings
      lcmsRunByProcMapId += processedMap.id -> lcmsRun
      mzDbFileByProcMapId += processedMap.id -> mzDbFile

      // Convert to processed map
      //val processedMap = rawMap.toProcessedMap( number = mapIdx + 1, mapSetId = tmpMapSetId )

      // Perform the feature clustering
      // TODO: use the clean maps service ???
      val scans = lcmsRun.scanSequence.get.scans
      val clusterizedMap = ClusterizeFeatures(processedMap, scans, clusteringParams)
      //clusterizedMap.toTsvFile("D:/proline/data/test/quanti/debug/clusterized_map_"+ (-clusterizedMap.id) +".tsv")

      // Set clusterized map id as the id of the provided map
      clusterizedMap.id = processedMap.id

      processedMaps(mapIdx) = clusterizedMap

      //processedMapByRawMapId += rawMap.id -> processedMap

      mapIdx += 1
    }

    // --- Perform the LC-MS maps alignment ---
    // TODO: do we need to remove the clusters for the alignment ???      
    val alnResult = if (alignmentConfig.isDefined)  {
      mapAligner.get.computeMapAlignments(processedMaps.filter(_ != null), alignmentConfig.get)
    } else {
      AlignmentResult(processedMaps(0).id, Array.empty[MapAlignmentSet])
    }

    // --- Create an in-memory map set ---
    var tmpMapSet = new MapSet(
      id = mapSetId,
      name = mapSetName,
      creationTimestamp = new java.util.Date,
      childMaps = processedMaps,
      alnReferenceMapId = alnResult.alnRefMapId,
      mapAlnSets = alnResult.mapAlnSets
    )

    // --- Build a temporary Master Map ---
    tmpMapSet.masterMap = BuildMasterMap(
      tmpMapSet,
      sortedLcMsRuns.map(_.scanSequence.get),
      quantConfig.crossAssignmentConfig.flatMap(_.ftFilter),
      quantConfig.detectionParams.get.ftMappingParams.get,
      clusteringParams
    )
    //tmpMapSet.toTsvFile("C:/Local/data/test/quanti/debug/master_map_"+ (-tmpMapSet.masterMap.id) +".tsv")

    // --- Re-build master map if peptide sequences have been provided ---
    for (pepMap <- peptideByRunIdAndScanNumber) {

      // Map peptides by scan id and scan sequence by run id
      val peptideByScanId = new LongMap[Peptide]()

      for (lcmsRun <- sortedLcMsRuns) {
        val scanSeq = lcmsRun.scanSequence.get

        for (lcmsScan <- scanSeq.scans) {
          for (peptide <- pepMap(lcmsRun.id).get(lcmsScan.initialId)) {
            peptideByScanId += lcmsScan.id -> peptide
          }
        }
      }

      // Instantiate a feature clusterer for each child map
      // TODO: provide this mapping to the master map builder ???
      val ftClustererByMapId = tmpMapSet.childMaps.toLongMapWith { childMap =>
        val scanSeq = scanSeqByRunId(childMap.runId.get)
        childMap.id -> new FeatureClusterer(childMap, scanSeq.scans, clusteringParams)
      }

      this._rebuildMasterMapUsingPeptides(tmpMapSet, peptideByScanId.result, ftClustererByMapId)
      //tmpMapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_with_peps_"+ (-tmpMapSet.masterMap.id) +".tsv")
    }

    // Re-build child maps in order to be sure they contain master feature children (including clusters)
    tmpMapSet = tmpMapSet.rebuildChildMaps()

    // --- Extract LC-MS missing features in all raw files ---
    val x2RawMaps = new ArrayBuffer[RawMap](sortedLcMsRuns.length)
    val x2ProcessedMaps = new ArrayBuffer[ProcessedMap](sortedLcMsRuns.length)
    val procMapTmpIdByRawMapId =  new LongMap[Long](sortedLcMsRuns.length)
    
    for (processedMap <- tmpMapSet.childMaps) {
      val rawMap = processedMap.getRawMaps().head.get
      val mzDbFile = mzDbFileByProcMapId(processedMap.id)
      val lcmsRun = lcmsRunByProcMapId(processedMap.id)

      // Extract missing features
      val peakelIdByMzDbPeakelId = new LongMap[Long]()
      val newLcmsFeatures = this._extractMissingFeatures(mzDbFile, lcmsRun, processedMap, tmpMapSet, peakelIdByMzDbPeakelId)

      // Create a new raw map by including the extracted missing features
      val x2RawMap = rawMap.copy(
        features = rawMap.features ++ newLcmsFeatures
      )
      
      val ftPeakels = newLcmsFeatures.flatMap(_.relations.peakelItems.flatMap(_.getPeakel()))
      val rawMapPeakels = new ArrayBuffer[Peakel](rawMap.peakels.get.length + ftPeakels.length)
      rawMapPeakels ++= rawMap.peakels.get
      rawMapPeakels ++= ftPeakels.groupByLong(_.id).values.map(_.head)
      
      // Append missing peakels
      x2RawMap.peakels = Some(rawMapPeakels.toArray)

      // Store the raw map
      logger.info("Storing the raw map...")
      rawMapStorer.storeRawMap(x2RawMap, storeFeatures = true, storePeakels = true)
      
      procMapTmpIdByRawMapId.put( x2RawMap.id, processedMap.id )
      
      // Detach peakels from the raw map
      x2RawMap.peakels = None

      // Detach peakels from features
      for (ft <- x2RawMap.features; peakelItem <- ft.relations.peakelItems) {
        peakelItem.peakelReference = PeakelIdentifier(peakelItem.peakelReference.id)
      }

      // Append raw map to the array buffer
      x2RawMaps += x2RawMap

      // Create a processed map for this rawMap
      x2ProcessedMaps += processedMap.copy(
        features = processedMap.features ++ newLcmsFeatures,
        rawMapReferences = Array(x2RawMap)
      )
    }
    //mapSet.toTsvFile("D:/proline/data/test/quanti/debug/master_map_no_missing_"+ (-mapSet.masterMap.id) +".tsv")

    // --- Create the corresponding map set ---
    val x2MapSet = CreateMapSet(lcmsDbCtx, mapSetName, x2ProcessedMaps)
    
    val procMapIdByTmpId = new LongMap[Long](sortedLcMsRuns.length)
    for (processedMap <- x2ProcessedMaps) {
      val rawMapId = processedMap.getRawMapIds.head
      val oldProcessedMapId = procMapTmpIdByRawMapId.get(rawMapId).get
      procMapIdByTmpId.put(oldProcessedMapId, processedMap.id )
    }

    // Attach the computed master map to the newly created map set
    x2MapSet.masterMap = tmpMapSet.masterMap
    x2MapSet.masterMap.mapSetId = x2MapSet.id
    x2MapSet.masterMap.rawMapReferences = x2RawMaps
    
    // update the map ids in the alnResult
    val finalMapAlnSets = new ArrayBuffer[MapAlignmentSet]
    for (alnSet <-alnResult.mapAlnSets) {
      val finalRefMapId = procMapIdByTmpId.get(alnSet.refMapId).get
      val finalTargetMapId = procMapIdByTmpId.get(alnSet.targetMapId).get
      val finalMapAlignments = new ArrayBuffer[MapAlignment]
      for (aln <- alnSet.mapAlignments){
        val finalMapAln = new MapAlignment(
          procMapIdByTmpId.get(aln.refMapId).get,
          procMapIdByTmpId.get(aln.targetMapId).get,
          aln.massRange,
          aln.timeList,
          aln.deltaTimeList,
          aln.properties
        )
        finalMapAlignments += finalMapAln
      }
      val finalMapAlnmSet = new MapAlignmentSet(
        finalRefMapId,
        finalTargetMapId,
        finalMapAlignments.toArray
      )
      finalMapAlnSets += finalMapAlnmSet
    }
    
    val finalAlnResult = new AlignmentResult(
      procMapIdByTmpId.get(alnResult.alnRefMapId).get,
      finalMapAlnSets.toArray
    )

    (x2MapSet, finalAlnResult)
  }
    
  private def _extractProcessedMap(lcmsRun: LcMsRun, mzDbFile: File, mapNumber: Int, mapSetId: Long): ProcessedMap = {

    // TODO: add max charge in config
    val maxCharge = 4

    val mzDbFts = if (quantConfig.detectionMethodName == DetectionMethod.DETECT_FEATURES) {
      this._detectFeatures(mzDbFile).filter(ft => ft.charge > 1 && ft.charge <= maxCharge)
    } else {
      this._extractFeaturesUsingMs2Events(mzDbFile, lcmsRun)
    }

    val rmsds = mzDbFts.par.map { mzdbFt =>
      val theoAbundances = IsotopePatternInterpolator.getTheoreticalPattern(mzdbFt.mz, mzdbFt.charge).abundances
      val peakelApexIntensities = mzdbFt.getPeakels.map(_.getApexIntensity)
      IsotopePatternInterpolator.calcAbundancesRmsd(theoAbundances, peakelApexIntensities)
    }.toArray

    val percentileComputer = new Percentile()
    val ipDevQ1 = percentileComputer.evaluate(rmsds, 25)
    val ipDevQ3 = percentileComputer.evaluate(rmsds, 75)
    val ipDevIQR = ipDevQ3 - ipDevQ1
    val ipDevUB = ipDevQ3 + 1.5 * ipDevIQR

    val rawMapId = RawMap.generateNewId()

    // Create a new raw Map
    val tmpRawMap = new RawMap(
      id = rawMapId,
      name = lcmsRun.rawFile.name,
      isProcessed = false,
      creationTimestamp = new java.util.Date,
      features = Array(),
      runId = lcmsRun.id,
      peakPickingSoftware = pps,
      properties = Some(
        LcMsMapProperties(
          ipDeviationUpperBound = Some(ipDevUB.toFloat)
        )
      )
    )

    // Convert features
    val peakelIdByMzDbPeakelId = new LongMap[Long]()
    val lcmsFeaturesWithoutClusters = mzDbFts.map { mzDbFt =>
      this._mzDbFeatureToLcMsFeature(mzDbFt, None, rawMapId, lcmsRun.scanSequence.get, peakelIdByMzDbPeakelId)
    }
    
    val ftPeakels = lcmsFeaturesWithoutClusters.flatMap( _.relations.peakelItems.map(_.peakelReference.asInstanceOf[Peakel] ) )

    val rawMap = tmpRawMap.copy(
      features = lcmsFeaturesWithoutClusters,
      peakels = Some(ftPeakels.groupByLong(_.id).values.map(_.head).toArray)
    )

    rawMap.toProcessedMap(mapNumber, mapSetId)
  }

  private def _extractFeaturesUsingMs2Events(mzDbFile: File, lcmsRun: LcMsRun): Array[MzDbFeature] = {

    logger.info("Start extracting features from MS2 events from " + mzDbFile.getName)

    val restrictToIdentifiedPeptides = quantConfig.detectionParams.get.startFromValidatedPeptides.get

    val peptideByScanNumber = peptideByRunIdAndScanNumber.map(_(lcmsRun.id)).getOrElse(LongMap.empty[Peptide])
    val mzDb = new MzDbReader(mzDbFile, true)

    val mzDbFts = try {

      val ftXtractConfig = FeatureExtractorConfig(
        mzTolPPM = this.extractionMozTolPPM
      )

      val mzdbFtX = new MzDbFeatureExtractor(mzDb, 5, 5, ftXtractConfig)

      this.logger.info("Retrieving scan headers...")
      val scanHeaders = mzDb.getSpectrumHeaders()
      val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2)
      val pfs = new ArrayBuffer[PutativeFeature](ms2ScanHeaders.length)

      this.logger.debug("Building putative features list from MS2 scan events...")

      for (scanH <- ms2ScanHeaders) {

        if (!restrictToIdentifiedPeptides || peptideByScanNumber.contains(scanH.getInitialId())) {
          pfs += new PutativeFeature(
            id = PutativeFeature.generateNewId,
            mz = scanH.getPrecursorMz,
            charge = scanH.getPrecursorCharge,
            spectrumId = scanH.getId,
            evidenceMsLevel = 2
          )
        }
      }

      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider(mzDb.getLcMsRunSliceIterator())

      // Extract features
      mzdbFtX.extractFeatures(rsdProv, pfs, extractionMozTolPPM)

    } finally {
      mzDb.close()
    }

    logger.info("Feature extraction done for file " + mzDbFile.getName)
    mzDbFts.toArray
  }

  private def _detectFeatures(mzDbFile: File): Array[MzDbFeature] = {

    val mzDb = new MzDbReader(mzDbFile, true)

    val mzDbFts = try {

      this.logger.info("Start detecting features in raw MS survey from " + mzDbFile.getName)

      val mzdbFtDetector = new MzDbFeatureDetector(
        mzDb,
        FeatureDetectorConfig(
          msLevel = 1,
          mzTolPPM = extractionMozTolPPM,
          minNbOverlappingIPs = 5
        )
      )

      // Extract features
      mzdbFtDetector.detectFeatures()

    } finally {
      mzDb.close()
    }

    mzDbFts
  }

  private def _extractMissingFeatures(
    mzDbFile: File,
    lcmsRun: LcMsRun,
    processedMap: ProcessedMap,
    mapSet: MapSet,
    peakelIdByMzDbPeakelId: LongMap[Long]
  ): Seq[LcMsFeature] = {

    val procMapId = processedMap.id
    val rawMapId = processedMap.getRawMapIds.head
    val masterMap = mapSet.masterMap
    val nbMaps = mapSet.childMaps.length

    val mzDb = new MzDbReader(mzDbFile, true)
    var mzDbFts = Seq.empty[MzDbFeature]
    val mftsWithMissingChild = new ArrayBuffer[LcMsFeature]
    val missingFtIdByMftId = new LongMap[Int]()
    val pfs = new ArrayBuffer[PutativeFeature]()

    try {

      val ftXtractConfig = FeatureExtractorConfig(
        mzTolPPM = this.extractionMozTolPPM,
        maxIPDeviation = processedMap.properties.flatMap(_.ipDeviationUpperBound)
      )

      val mzdbFtX = new MzDbFeatureExtractor(mzDb, 5, 5, ftXtractConfig)

      //val scanHeaders = mzDb.getScanHeaders()
      //val ms2ScanHeaders = scanHeaders.filter(_.getMsLevel() == 2 )
      
      this.logger.info("Start extracting missing Features from " + mzDbFile.getName)
      this.logger.info("Building putative features list using master features...")

      for (mft <- masterMap.features) {
        require(mft.children.length <= nbMaps, "master feature contains more child features than maps")

        // Check for master features having a missing child for this processed map
        val childFtOpt = mft.children.find(_.relations.processedMapId == processedMap.id)
        if (childFtOpt.isEmpty) {
          mftsWithMissingChild += mft

          val bestChildProcMapId = mft.relations.bestChildProcessedMapId
          val bestChild = mft.children.find(_.relations.processedMapId == bestChildProcMapId).get

          //val childMapAlnSet = revRefMapAlnSetByMapId(bestChildMapId)
          //val predictedTime = childMapAlnSet.calcReferenceElutionTime(mft.elutionTime, mft.mass)
          val predictedTimeOpt = mapSet.convertElutionTime(bestChild.elutionTime, bestChildProcMapId, procMapId)
          //println( "ftTime="+ mft.elutionTime +" and predicted time (in "+mzDbMapId+")="+predictedTime)    
          
          if (predictedTimeOpt.isDefined) {
            // Fix negative predicted times and remove decimals
            val predictedTime = if (predictedTimeOpt.get._1 <= 0) 1f
            else math.round(predictedTimeOpt.get._1).toFloat
            
            // Note: we can have multiple missing features for a given MFT
            // However we assume there a single missing feature for a given map
            val missingFtId = PutativeFeature.generateNewId
            missingFtIdByMftId += (mft.id -> missingFtId)
  
            val pf = new PutativeFeature(
              id = missingFtId,
              mz = mft.moz,
              charge = mft.charge,
              elutionTime = predictedTime,
              evidenceMsLevel = 2,
              isPredicted = true
            )
  
            // TODO: check the usage of these values
            pf.durations = mft.children.map(_.duration)
            pf.areas = mft.children.map(_.intensity)
            pf.mozs = mft.children.map(_.moz)
  
            pfs += pf
          }
        }
      }

      // Instantiates a Run Slice Data provider
      val rsdProv = new RunSliceDataProvider(mzDb.getLcMsRunSliceIterator())
      this.logger.info("Extracting " + missingFtIdByMftId.size + " missing features from " + mzDbFile.getName)
      // Extract features
      // TODO: add minNbCycles param
      mzDbFts = mzdbFtX.extractFeatures(rsdProv, pfs, extractionMozTolPPM)

    } finally {
      mzDb.close()
    }

    val pfById = pfs.mapByLong(_.id)
    val mzDbFtById = mzDbFts.mapByLong(_.id)

    // Convert mzDB features into LC-MS DB features
    val newLcmsFeatures = new ArrayBuffer[LcMsFeature](missingFtIdByMftId.size)
    for (
      mftWithMissingChild <- mftsWithMissingChild;
      mzDbFt <- mzDbFtById.get(missingFtIdByMftId(mftWithMissingChild.id)) if mzDbFt.area > 0 && mzDbFt.getMs1Count >= 5
    ) {
      
      // Retrieve putative feature
      val pf = pfById(mzDbFt.id)

      // FIXME: why do we extract features with 0 duration ???

      // Convert the extracted feature into a LC-MS feature
      val newLcmsFt = this._mzDbFeatureToLcMsFeature(mzDbFt, Some(pf), rawMapId, lcmsRun.scanSequence.get, peakelIdByMzDbPeakelId)

      // TODO: decide if we set or not this value (it may help for distinction with other features)
      newLcmsFt.correctedElutionTime = Some(mftWithMissingChild.elutionTime)

      // Update the processed map id of the new feature
      newLcmsFt.relations.processedMapId = processedMap.id

      // Add missing child feature to the master feature
      mftWithMissingChild.children ++= Array(newLcmsFt)

      // Add new LC-MS feature
      newLcmsFeatures += newLcmsFt
    }

    newLcmsFeatures
  }

  // TODO: apply this before and after filling missing values ???
  // Note: in this method we will break master features if they are matching multiple peptides
  // However we do not group existing master features
  private def _rebuildMasterMapUsingPeptides(
    mapSet: MapSet,
    peptideByScanId: LongMap[Peptide],
    clustererByMapId: LongMap[FeatureClusterer]
  ): Unit = {
    this.logger.info("Re-building master map using peptide identities...")

    val alnRefMapId = mapSet.getAlnReferenceMapId
    val masterFeatures = mapSet.masterMap.features
    val newMasterFeatures = new ArrayBuffer[LcMsFeature](masterFeatures.length)

    // Iterate over all map set master features
    for (mft <- masterFeatures) {

      // --- Find peptides matching child sub features ---
      val featuresByPepId = new LongMap[ArrayBuffer[LcMsFeature]]
      val pepIdsByFeature = new HashMap[LcMsFeature, ArrayBuffer[Long]]
      val unidentifiedFtSet = new collection.mutable.HashSet[LcMsFeature]

      // Decompose clusters if they exist and map them by peptide identification
      mft.eachChildSubFeature { subFt =>
        val ftRelations = subFt.relations
        val peptideIds = ftRelations.ms2EventIds.map(peptideByScanId.get(_)).withFilter(_.isDefined).map(_.get.id).distinct

        if (peptideIds.isEmpty) unidentifiedFtSet += subFt
        else {
          for (pepId <- peptideIds) {
            featuresByPepId.getOrElseUpdate(pepId, new ArrayBuffer[LcMsFeature]) += subFt
            pepIdsByFeature.getOrElseUpdate(subFt, new ArrayBuffer[Long]) += pepId
          }
        }
      }

      // --- Solve identification conflicts ---

      // If no identified feature in this master
      if (featuresByPepId.isEmpty) {
        // We keep the existing master feature as is
        newMasterFeatures += mft
      } // If all child features match the same peptide
      else if (featuresByPepId.size == 1) {
        // We tag this master feature with the peptide ID
        mft.relations.peptideId = featuresByPepId.head._1
        // And we keep the existing master feature
        newMasterFeatures += mft
      } else {
        // Else we create a master feature for each matching peptide
        for ((pepId, features) <- featuresByPepId) {

          val newMftFeatures = features ++ unidentifiedFtSet
          val ftsByMapId = newMftFeatures.groupBy(_.relations.processedMapId)
          val clusterizedFeatures = new ArrayBuffer[LcMsFeature]

          // Check if these features are assigned to a single peptide
          newMftFeatures.foreach { ft =>
            if (pepIdsByFeature.get(ft).map(_.length).getOrElse(0) > 1) {
              // Flag this feature as a conflicting one
              ft.selectionLevel = 0
            }
          }

          // Iterate over features grouped by maps
          for ((mapId, fts) <- ftsByMapId) {
            // If we have a single feature for this map => we keep it as is
            if (fts.length == 1) clusterizedFeatures += fts.head
            // Else we clusterize the multiple detected features
            else {

              // Partition identified and unidentified features
              val (identifiedFts, unidentifiedFts) = fts.partition(unidentifiedFtSet.contains(_) == false)

              // If we don't have at least one identified feature
              val ftCluster = if (identifiedFts.isEmpty) {
                // Clusterize unidentified features
                clustererByMapId(mapId).buildFeatureCluster(unidentifiedFts)
              } else {
                // Else clusterize identified features
                val tmpFtCluster = clustererByMapId(mapId).buildFeatureCluster(identifiedFts)

                if (unidentifiedFts.isEmpty == false) {
                  // Append unidentified features to the cluster
                  tmpFtCluster.subFeatures ++= unidentifiedFts
                  // Flag this cluster as a conflicting feature
                  tmpFtCluster.selectionLevel = 0
                }

                tmpFtCluster
              }

              // Append cluster to the master features list
              clusterizedFeatures += ftCluster
            }
          }

          val refFtOpt = clusterizedFeatures.find(_.relations.processedMapId == alnRefMapId)
          val refFt = refFtOpt.getOrElse(clusterizedFeatures.head)
          val newMft = refFt.toMasterFeature(children = clusterizedFeatures.toArray)

          // We tag this new master feature with the peptide ID
          newMft.relations.peptideId = pepId

          newMasterFeatures += newMft
        }
      }
    }

    // TODO: find where duplicated mft are introduced
    /*val mftByPep = newMasterFeatures.groupBy(_.relations.peptideId)
    for( (pid,mftbs) <- mftByPep) {
      println("pid="+pid)
      println("mftbs="+mftbs.length)
    }
    error("")*/

    mapSet.masterMap = mapSet.masterMap.copy(features = newMasterFeatures.toArray)

    ()
  }

}

