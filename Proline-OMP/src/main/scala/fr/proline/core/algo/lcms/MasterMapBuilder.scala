package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import fr.proline.core.om.model.lcms._
import filtering._

object MasterMapBuilder {
  
  private var _processedMapIdByRunMapId = Map.empty[Long,Long]
  
  def buildMasterMap( mapSet: MapSet, masterFtFilter: Filter, ftMappingParams: FeatureMappingParams ): ProcessedMap = {
    
    // Check that map set maps have been aligned
    require( mapSet.getAlnReferenceMap.isDefined,"can't build a master map with unaligned maps")
    
    // TODO: check filter name (must be intensity or relative_intensity)
    
    // Retrieve some vars
    val childMaps = mapSet.childMaps
    val processedMapIdByRunMapId = mapSet.getProcessedMapIdByRunMapId
    val alnRefMap = mapSet.getAlnReferenceMap.get
    val alnRefMapId = alnRefMap.id
    val mapAlnSets = mapSet.mapAlnSets
    val normalizationFactors = mapSet.getNormalizationFactorByMapId
    
    val ftSelector = FeatureSelector( masterFtFilter.name )
    val filterTree = FilterNode(filter=masterFtFilter)    
    
    // Initialize master map using filtered features
    var masterFts = new ArrayBuffer[Feature]() ++ alnRefMap.features filter { _.selectionLevel >= 2 } map { _.toMasterFeature }
    
    // Check if we have multiple maps
    if( childMaps.length > 1 ) {
      
      //println( alnRefMapId )
  
      // Retrieve alignments of the reference map (TODO: put in a helper)      
      /*val revRefMapAlnSets = mapAlnSets filter { _.toMapId == alnRefMapId } 
      val refMapAlnSets = (mapAlnSets filter { _.fromMapId == alnRefMapId }) ++ (revRefMapAlnSets map {_.getReversedAlnSet})
      var refMapAlnSetByMapId = refMapAlnSets map { alnSet => alnSet.toMapId -> alnSet }  toMap*/
      
      // Feed master map with the best features (above intensity threshold)
      for( childMap <- childMaps ) {
        if( childMap.id != alnRefMapId ) {
          
          // Filter the child map features
          ftSelector.selectFeatures( childMap, filterTree )
          
          // Retrieve the features above the provided threshold
          val highIntensityFts = childMap.features filter { _.selectionLevel >= 2 }
          
          // Feed the master map with the filtered features
          this._feedMasterMapFeatures(masterFts, highIntensityFts, mapSet, processedMapIdByRunMapId, ftMappingParams, true )
        }
      }
      
      //print "feed master map with low quality features\n"
        
      // Feed master map with low quality features (under intensity threshold)
      for( childMap <- childMaps ) {
        
        // Retrieve the features under the provided threshold        
        val lowIntensityFts = childMap.features filter { _.selectionLevel < 2 }
        
        if( lowIntensityFts.length > 0 ) {
          // Assign these poor quality features to existing master features
          // but don't make them master features if they can't be assigned
          this._feedMasterMapFeatures(masterFts,lowIntensityFts,mapSet,processedMapIdByRunMapId,ftMappingParams, false )  
        }
              
      }
      
      // Try to map single features to existing master features with charge error tolerance
      // print "map alone features to existing master features with charge error tolerance\n"
      // Note this has been disabled in Proline because we expect the charge states to be correctly computed
      //masterFts = this._mergeMasterFeaturesWithChargeTol(mapSet, masterFts, ftMappingParams)
    }
    
    val curTime = new java.util.Date()
    
    // Create the master map with master features
    val masterMap = ProcessedMap(
      id = ProcessedMap.generateNewId(),
      number = 0,
      name = alnRefMap.name,
      features = this.rebuildMftsUsingBestChild( masterFts ),
      isMaster = true,
      isAlnReference = false,
      isProcessed = true,
      creationTimestamp = curTime,
      modificationTimestamp = curTime,
      mapSetId = mapSet.id,
      runMapIdentifiers = mapSet.getRunMapIds().map(Identifier(_))
    )
    
    masterMap
  }
  
  def rebuildMftsUsingBestChild( masterFeatures: Seq[Feature] ): Array[Feature] = {
    
    val newMasterFeatures = new ArrayBuffer[Feature](masterFeatures.length)
    for( val mft <- masterFeatures ) {
      
      val mftChildren = mft.children
      
      // Retrieve the highest feature child    
      val highestFtChild = mftChildren.reduceLeft { (a,b) => 
        if( a.getNormalizedIntensityOrIntensity > b.getNormalizedIntensityOrIntensity ) a else b
      }
      val newMasterFt = highestFtChild.toMasterFeature
      newMasterFt.id = mft.id
      newMasterFt.children = mft.children
      
      newMasterFeatures += newMasterFt
      // Update master feature attributes
      /*ft.ms1Count = highestFtChild.ms1Count
      ft.ms2Count = highestFtChild.ms2Count
      ft.intensity = highestFtChild.intensity
      ft.normalizedIntensity = highestFtChild.normalizedIntensity
      ft.bestChildId = highestFtChild.id
      ft.firstScanId = highestFtChild.firstScanId
      ft.lastScanId = highestFtChild.lastScanId
      ft.apexScanId = highestFtChild.apexScanId
      */
      
    }
    
    newMasterFeatures.toArray
    
  }
  
  private def _feedMasterMapFeatures(
     masterMapFeatures: ArrayBuffer[Feature],
     childMapFeatures: Array[Feature], 
     mapSet: MapSet,
     processedMapIdByRunMapId: Map[Long,Long],
     ftMappingParams: FeatureMappingParams,
     addNonMatchingFeatures: Boolean 
   ): Unit = {
    
    // Iterate over all child map features in order to correct their elution time
    for( val childFt <- childMapFeatures ) {
      
      val childMapId = childFt.relations.processedMapId
      require( childMapId != 0, "a processed map id must be defined for each child feature (m/z=" + childFt.moz +")")
      
      val childProcessedMapId = if( childFt.isCluster ) childMapId
      else processedMapIdByRunMapId(childMapId)
      
      if( childProcessedMapId != mapSet.getAlnReferenceMapId ) {
        // Calculate corrected elution time using the elution time alignment
        val correctedTime = mapSet.convertElutionTime(childFt.elutionTime, childProcessedMapId, mapSet.getAlnReferenceMapId)
        childFt.correctedElutionTime = Some(correctedTime)
      }

    }
    //print "compute pairwise ft mapping\n" if ! addNonMatchingFeatures
    
    // Align child map features with master map features
    val ftMapping = FeatureMapper.computePairwiseFtMapping(
      masterMapFeatures.toArray,
      childMapFeatures,
      ftMappingParams
    )
    val revFtMapping = FeatureMapper.computePairwiseFtMapping(
      childMapFeatures,
      masterMapFeatures.toArray,
      ftMappingParams
    )
   
    val masterMapFtById = masterMapFeatures.map { ft => ft.id -> ft } toMap
    
    // Retrieve nearest matching children (considering elution time) and their id
    val matchingFtIdSet = new HashSet[Long]()
    
    for( val(masterMapFtId, matchingChildFeatures) <- ftMapping ) {
      
      val masterMapFt = masterMapFtById(masterMapFtId)
      
      var newChildFeatures = Array.empty[Feature]
      if( matchingChildFeatures.length == 1 ) newChildFeatures = matchingChildFeatures
      else {        
        val mftTime = masterMapFt.elutionTime
        
        val unambiguousMatchingFts = matchingChildFeatures.filter( ft => revFtMapping(ft.id).length == 1 )
        if( unambiguousMatchingFts.length == 1 ) newChildFeatures = unambiguousMatchingFts
        else {
          
          val ambiguousMatchingFts = if( unambiguousMatchingFts.length == 0 ) matchingChildFeatures else unambiguousMatchingFts
          newChildFeatures = ambiguousMatchingFts
          
          //val exclusivelyMatchingChildFts = matchingChildFeatures.filter( ft => matchingFtIdSet.contains(ft.id) )
          /*nearestChildFt = ambiguousMatchingFts.reduceLeft { (a,b) =>
            if( math.abs(a.getCorrectedElutionTimeOrElutionTime - mftTime) < math.abs(b.getCorrectedElutionTimeOrElutionTime - mftTime) ) a else b
          }*/
        }
      }
      
      matchingFtIdSet ++= newChildFeatures.map(_.id)
      
      // Append matching children to the current list of master feature children
      masterMapFt.children ++= newChildFeatures
      
    }
    
    if( addNonMatchingFeatures ) _addNonMatchingFeatures(masterMapFeatures,childMapFeatures,matchingFtIdSet)
    
  }
  
  private def _addNonMatchingFeatures(
     masterMapFeatures: ArrayBuffer[Feature],
     childMapFeatures: Array[Feature],
     matchingFtIdSet: HashSet[Long]
  ): ArrayBuffer[Feature] = {
      
    // Retrieve child map features which weren't aligned with master map
    val nonMatchingChildFts = childMapFeatures filter { ft => ! matchingFtIdSet.contains( ft.id ) }
    
    // Set the corrected elution time using the elution time alignment
    /*nonMatchingChildFts.foreach { childFt =>
      val correctedTime = mapSet.convertElutionTime(childFt.elutionTime, childFt.relations.mapId, mapSet.alnReferenceMapId)
      childFt.correctedElutionTime = Some(correctedTime)
    }*/
    
    // Convert child features into master features
    val newMasterFeatures = nonMatchingChildFts.map { _.toMasterFeature() }
    
    //val newMasterFeatures = new ArrayBuffer[Feature](nonMatchingChildFts.length)
    /*for( nonMatchingChildFt <- nonMatchingChildFts ) {
      newMasterFeatures += new Feature (
                                id = Feature.generateNewId,
                                moz = nonMatchingChildFt.moz,
                                intensity = nonMatchingChildFt.intensity,
                                charge = nonMatchingChildFt.charge,
                                elutionTime = nonMatchingChildFt.correctedElutionTime,
                                qualityScore = nonMatchingChildFt.qualityScore,
                                ms1Count = nonMatchingChildFt.ms1Count,
                                ms2Count = nonMatchingChildFt.ms2Count,
                                isOverlapping = false,
                                firstScanId = nonMatchingChildFt.firstScanId,
                                lastScanId = nonMatchingChildFt.lastScanId,
                                apexScanId = nonMatchingChildFt.apexScanId,
                                firstScanInitialId = nonMatchingChildFt.firstScanInitialId,
                                lastScanInitialId = nonMatchingChildFt.lastScanInitialId,
                                apexScanInitialId = nonMatchingChildFt.apexScanInitialId,
                                ms2EventIds = null,
                                isotopicPatterns = null,
                                overlappingFeatures = null,
                                children = Array(nonMatchingChildFt)
                              )
      
    }*/
    
    // Add non-matching child features to the master map
    masterMapFeatures ++= newMasterFeatures
    
  }

  private def _mergeMasterFeaturesWithChargeTol(
    mapSet: MapSet,
    masterFeatures: ArrayBuffer[Feature], 
    ftMappingParams: FeatureMappingParams
  ): ArrayBuffer[Feature] = {
    
    // Retrieve master map and number of maps
    val runMapIds = mapSet.getRunMapIds
    val nbMaps = runMapIds.length
    
    // Build a hash map of unfulfilled master features and another one for single features
    val notFullMftsByMapId = new java.util.HashMap[Long,ArrayBuffer[Feature]]
    val singleFeaturesByMapId = new java.util.HashMap[Long,ArrayBuffer[Feature]]
    
    // Initialize the hash maps
    for( mapId <- runMapIds ) {
      notFullMftsByMapId.put( mapId, new ArrayBuffer[Feature](0) )
      singleFeaturesByMapId.put( mapId, new ArrayBuffer[Feature](0) )
    }
    
    for( masterFt <- masterFeatures ) {
      
      // Determine the number of matched maps
      val matchedMapIdSet = masterFt.getRunMapIds.toSet
      val nbMatchedMaps = matchedMapIdSet.size
      
      // If master feature contains a single feature
      if( masterFt.children.length == 1 ) {
        val runMapId = matchedMapIdSet.first
        //println("runmap id set="+matchedMapIdSet.mkString(","))
        //println("runmap id list ="+ runMapIds.mkString(","))
        singleFeaturesByMapId.get( runMapId ) += masterFt
      }
      // If master feature is not fulfilled
      else if( nbMatchedMaps < nbMaps ) {
        
        val unmatchedMapIds = runMapIds filter { ! matchedMapIdSet.contains(_) } 
        
        for( mapId <- unmatchedMapIds ) {
          notFullMftsByMapId.get(mapId) += masterFt
        }
      }
      
    }
    
    //print "compute pairwise ft mapping with charge tolerance\n"
    
    // Align child map features with master map features    
    // Iterate over single features of each map
    val toDeleteSingleFtIdSet = new scala.collection.mutable.HashSet[Long]()
    
    val entryIter = singleFeaturesByMapId.entrySet().iterator()
    while( entryIter.hasNext() ) {
      val entry = entryIter.next()
      val( mapId, singleFeatures ) = (entry.getKey, entry.getValue )
            
      // Try to retrieve putative matching master features
      val putativeMatchingMfts = notFullMftsByMapId.get(mapId)
      if ( putativeMatchingMfts.length > 0 ) {
      
        val ftMapping = FeatureMapper.computePairwiseFtMapping(
          singleFeatures.toArray,
          putativeMatchingMfts.toArray,
          ftMappingParams,
          false // was true in Prosper
        )
        
        val singleFtById = singleFeatures.map { ft => ft.id -> ft } toMap
        
        // Retrieve nearest matching children (considering elution time) and their id
        for( val(singleFtId, matchingMfts) <- ftMapping ) {
          
          // TODO: keep this ?
          if( matchingMfts.length == 1 ) {
            val singleFt = singleFtById(singleFtId)
            val matchingMft = matchingMfts(0)
            
            // Add single feature to matching not fulfilled master feature
            matchingMft.children ++= singleFt.children
            
            toDeleteSingleFtIdSet += singleFtId
          }
        }
      }
    }
    
    //print 'nb ft to remove: ' . scalar(keys(toDeleteAloneFtIdMap)) ."\n"
    
    // Remove from the master map single features which were merged
    new ArrayBuffer[Feature] ++ masterFeatures filter { ft => ! toDeleteSingleFtIdSet.contains( ft.id ) }
    
  }
}