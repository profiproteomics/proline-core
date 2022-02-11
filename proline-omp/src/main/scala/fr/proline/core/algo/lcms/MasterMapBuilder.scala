package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.lcms._
import filtering._

object BuildMasterMap {
  
  def apply(
    mapSet: MapSet,
    scanSeqs: Seq[LcMsScanSequence],
    masterFtFilter: Option[Filter],
    ftMappingParams: FeatureMappingParams,
    ftClusteringParams: ClusteringParams // used to clusterize ambiguous ft mappings
  ): ProcessedMap = {
    
    val masterMapBuilder = new MasterMapBuilder(
      mapSet,
      scanSeqs,
      masterFtFilter,
      ftMappingParams,
      ftClusteringParams
    )
    
    masterMapBuilder.buildMasterMap()
  }

}

// TODO: create an algo which clusterize master features based on child feature id

class MasterMapBuilder(
    mapSet: MapSet,
    scanSeqs: Seq[LcMsScanSequence],
    masterFtFilter: Option[Filter],
    ftMappingParams: FeatureMappingParams,
    ftClusteringParams: ClusteringParams // used to clusterize ambiguous ft mappings
  ) extends LazyLogging {
  
  // Check that map set maps have been aligned
  require( mapSet.getAlnReferenceMap.isDefined,"can't build a master map with unaligned maps")
  
  // Retrieve some vars
  private val childMaps = mapSet.childMaps
  private val nbMaps = childMaps.length
  //private val processedMapIdByRawMapId = mapSet.getProcessedMapIdByRawMapId
  private val alnRefMap = mapSet.getAlnReferenceMap.get
  private val alnRefMapId = alnRefMap.id
  //private val mapAlnSets = mapSet.mapAlnSets
  //private val normalizationFactors = mapSet.getNormalizationFactorByMapId
  
  private val scanSeqByRunId = Map() ++ scanSeqs.map( ss => ss.runId -> ss)
  private val ftClustererByChildMapId = Map() ++ mapSet.childMaps.map { childMap =>
    val scanSeq = scanSeqByRunId(childMap.runId.get)    
    childMap.id -> new FeatureClusterer(childMap, scanSeq.scans, ftClusteringParams)
  }
  
  // TODO: check filter name (must be intensity or relative_intensity)
  val ftSelector = FeatureSelector( masterFtFilter.map(_.name) )
  val filterTree = FilterNode(filter = if (masterFtFilter.isDefined) masterFtFilter.get else null)
  
  def buildMasterMap(): ProcessedMap = {
    
    // Initialize master map using filtered features
    val masterFtBuilders = new ArrayBuffer[MasterFeatureBuilder]()
    masterFtBuilders ++= alnRefMap.features filter { _.selectionLevel >= 2 } map { ft => new MasterFeatureBuilder(ft,nbMaps) }
    
    // Check if we have multiple maps
    if( nbMaps > 1 ) {
      
      // Feed master map with the best features (above intensity threshold)
      for( childMap <- childMaps ) {
        if( childMap.id != alnRefMapId ) {
          
          // Filter the child map features
          ftSelector.selectFeatures( childMap, filterTree )
          
          // Retrieve the features above the provided threshold
          val highIntensityFts = childMap.features filter { _.selectionLevel >= 2 }
          
          // Feed the master map with the filtered features
          this._feedMasterMapFeatures( childMap.id, masterFtBuilders, highIntensityFts, true )
        }
      }
      
      this.logger.info( "feed master map with low quality features" )
      
      // Feed master map with low quality features (under intensity threshold)
      for( childMap <- childMaps ) {
        
        // Retrieve the features under the provided threshold
        val lowIntensityFts = childMap.features filter { _.selectionLevel < 2 }
        
        if( lowIntensityFts.length > 0 ) {
          // Assign these poor quality features to existing master features
          // but don't make them master features if they can't be assigned
          this._feedMasterMapFeatures( childMap.id, masterFtBuilders, lowIntensityFts, false )
        }
      }
      
      // Try to map single features to existing master features with charge error tolerance
      // print "map alone features to existing master features with charge error tolerance\n"
      // Note this has been disabled in Proline because we expect the charge states to be correctly computed
      //masterFts = this._mergeMasterFeaturesWithChargeTol(masterFtBuilders)
    }
    
    val clusterizedMasterFtBuilders = this._cluterizeMasterFeatures( masterFtBuilders )
    
    // --- Create the master map with master features ---
    val curTime = new java.util.Date()
    
    val masterMap = ProcessedMap(
      id = ProcessedMap.generateNewId(),
      number = 0,
      name = alnRefMap.name,
      features = this._buildMasterFeaturesUsingBestChild( clusterizedMasterFtBuilders ).toArray,
      isMaster = true,
      isAlnReference = false,
      isProcessed = true,
      creationTimestamp = curTime,
      modificationTimestamp = curTime,
      mapSetId = mapSet.id,
      rawMapReferences = mapSet.getRawMapIds().map(RawMapIdentifier(_))
    )
    
    masterMap
  }
  
  private def _buildMasterFeaturesUsingBestChild( mftBuilders: Seq[MasterFeatureBuilder] ): Seq[Feature] = {
    
    val masterFeatures = new ArrayBuffer[Feature](mftBuilders.length)
    for( mftBuilder <- mftBuilders ) {
      
      val mftChildren = mftBuilder.children
      
      // Retrieve the highest feature child
      val highestFtChild = mftChildren.reduceLeft { (a,b) => 
        if( a.getNormalizedIntensityOrIntensity > b.getNormalizedIntensityOrIntensity ) a else b
      }
      mftBuilder.bestFeature = highestFtChild
      
      masterFeatures += mftBuilder.toMasterFeature()
    }
    
    masterFeatures
  }
  
  private def _feedMasterMapFeatures(
    childMapId: Long,
    mftBuilders: ArrayBuffer[MasterFeatureBuilder],
    childMapFeatures: Array[Feature],
    addNonMatchingFeatures: Boolean
  ): Unit = {
    
    val mftBestFeatures = mftBuilders.map(_.bestFeature)
    val mftBuilderByBestFtId = Map() ++ mftBuilders.map { mftBuilder => mftBuilder.bestFeature.id -> mftBuilder }
    val ftClusterer = ftClustererByChildMapId(childMapId)
    
    // Iterate over all child map features in order to correct their elution time
    for( childFt <- childMapFeatures ) {
      
      val childMapId = childFt.relations.processedMapId
      require( childMapId != 0, "a processed map id must be defined for each child feature (m/z=" + childFt.moz +")")
      
      //val childProcessedMapId = if( childFt.isCluster ) childMapId
      //else processedMapIdByRawMapId(childFt.relations.rawMapId)
      
      if( childMapId != mapSet.getAlnReferenceMapId ) {
        // Calculate corrected elution time using the elution time alignment
        val correctedTime = mapSet.convertElutionTime(childFt.elutionTime, childMapId, mapSet.getAlnReferenceMapId)
        childFt.correctedElutionTime = correctedTime.map(_._1)
      }

    }
    //print "compute pairwise ft mapping\n" if ! addNonMatchingFeatures
    
    // Align child map features with master map features
    val ftMapping = FeatureMapper.computePairwiseFtMapping(
      mftBestFeatures,
      childMapFeatures,
      ftMappingParams
    )
    /*val revFtMapping = FeatureMapper.computePairwiseFtMapping(
      childMapFeatures,
      mftBestFeatures,
      ftMappingParams
    )*/
    
    // Retrieve nearest matching children (considering elution time) and their id
    val matchingFtIdSet = new HashSet[Long]()
    
    for( (mftBestFtId, matchingChildFeatures) <- ftMapping ) {
      
      val mftBuilder = mftBuilderByBestFtId(mftBestFtId)
      
      /*if( matchingChildFeatures.length == 1 ) newChildFeatures = matchingChildFeatures
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
      }*/
      
      matchingFtIdSet ++= matchingChildFeatures.map(_.id)
      
      if( matchingChildFeatures.length == 1 ) {
        // Append unambiguous matching child to the current list of master feature children
        mftBuilder.children ++= matchingChildFeatures
      } else if ( matchingChildFeatures.length > 1 ) {
        
        // Unclusterize ambiguous children
        val unclusterizedChildFts = new ArrayBuffer[Feature]
        matchingChildFeatures.foreach { childFt =>
          childFt.eachSubFeatureOrThisFeature { subFt =>
            unclusterizedChildFts += subFt
          }
        }
        
        // Re-clusterize ambiguous children
        mftBuilder.children += ftClusterer.buildFeatureCluster(unclusterizedChildFts)
      }
      
    }
    
    if( addNonMatchingFeatures ) _addNonMatchingFeatures(mftBuilders,childMapFeatures,matchingFtIdSet)
    
  }
  
  private def _addNonMatchingFeatures(
     mftBuilders: ArrayBuffer[MasterFeatureBuilder],
     childMapFeatures: Array[Feature],
     matchingFtIdSet: HashSet[Long]
  ) {
      
    // Retrieve child map features which weren't aligned with master map
    val nonMatchingChildFts = childMapFeatures filter { ft => ! matchingFtIdSet.contains( ft.id ) }
    
    // Set the corrected elution time using the elution time alignment
    /*nonMatchingChildFts.foreach { childFt =>
      val correctedTime = mapSet.convertElutionTime(childFt.elutionTime, childFt.relations.mapId, mapSet.alnReferenceMapId)
      childFt.correctedElutionTime = Some(correctedTime)
    }*/
    
    // Convert child features into master features
    val newMftBuilders = nonMatchingChildFts.map { ft => new MasterFeatureBuilder(ft,nbMaps) }
    
    // Add non-matching child features to the master map
    mftBuilders ++= newMftBuilders
    
  }

  private def _mergeMasterFeaturesWithChargeTol(
    mftBuilders: ArrayBuffer[MasterFeatureBuilder]
  ): ArrayBuffer[MasterFeatureBuilder] = {
    
    // Retrieve master map and number of maps
    val rawMapIds = mapSet.getRawMapIds
    val nbMaps = rawMapIds.length
    
    // Build a hash map of unfulfilled master features and another one for single features
    val notFullMftsByMapId = new HashMap[Long,ArrayBuffer[MasterFeatureBuilder]]
    val singleChildMftsByMapId = new HashMap[Long,ArrayBuffer[MasterFeatureBuilder]]
    
    // Initialize the hash maps
    for( mapId <- rawMapIds ) {
      notFullMftsByMapId.put( mapId, new ArrayBuffer[MasterFeatureBuilder](0) )
      singleChildMftsByMapId.put( mapId, new ArrayBuffer[MasterFeatureBuilder](0) )
    }
    
    for( mftBuilder <- mftBuilders ) {
      
      // Determine the number of matched maps
      val matchedMapIdSet = mftBuilder.bestFeature.getRawMapIds.toSet
      val nbMatchedMaps = matchedMapIdSet.size
      
      // If master feature contains a single feature
      if( mftBuilder.children.length == 1 ) {
        val rawMapId = matchedMapIdSet.head
        //println("runmap id set="+matchedMapIdSet.mkString(","))
        //println("runmap id list ="+ rawMapIds.mkString(","))
        singleChildMftsByMapId( rawMapId ) += mftBuilder
      }
      // If master feature is not fulfilled
      else if( nbMatchedMaps < nbMaps ) {
        
        val unmatchedMapIds = rawMapIds filter { ! matchedMapIdSet.contains(_) } 
        
        for( mapId <- unmatchedMapIds ) {
          notFullMftsByMapId(mapId) += mftBuilder
        }
      }
      
    }
    
    //print "compute pairwise ft mapping with charge tolerance\n"
    
    // Align child map features with master map features    
    // Iterate over single features of each map
    val toDeleteSingleFtIdSet = new scala.collection.mutable.HashSet[Long]()
    
    for( ( mapId, singleChildMfts ) <- singleChildMftsByMapId ) {
            
      // Try to retrieve putative matching master features
      val putativeMatchingMfts = notFullMftsByMapId(mapId)
      
      if ( putativeMatchingMfts.length > 0 ) {
      
        val ftMapping = FeatureMapper.computePairwiseFtMapping(
          singleChildMfts.map(_.bestFeature),
          putativeMatchingMfts.map(_.bestFeature),
          ftMappingParams,
          false // was true in Prosper
        )
        
        val bestMftChildById = Map() ++ singleChildMfts.map { mft => mft.bestFeature.id -> mft.bestFeature }
        val putativeMatchingMftByBestFtId = Map() ++ putativeMatchingMfts.map { mft => mft.bestFeature.id -> mft }
        
        // Retrieve nearest matching children (considering elution time) and their id
        for( (singleChildMftBestFtId, matchingFeatures) <- ftMapping ) {
          
          // TODO: keep this ?
          if( matchingFeatures.length == 1 ) {
            val matchingMftBuilder = putativeMatchingMftByBestFtId( matchingFeatures(0).id )            
            val singleFt = bestMftChildById(singleChildMftBestFtId)            
            
            // Add single feature to matching not fulfilled master feature
            matchingMftBuilder.children += singleFt
            
            toDeleteSingleFtIdSet += singleChildMftBestFtId
          }
        }
      }
    }
    
    //print 'nb ft to remove: ' . scalar(keys(toDeleteAloneFtIdMap)) ."\n"
    
    // Remove from the master map single features which were merged
    mftBuilders.filter( mftb => ! toDeleteSingleFtIdSet.contains( mftb.bestFeature.id ) )
    
  }
  
  // Clusterize master features sharing at last a child feature
  private def _cluterizeMasterFeatures( mftBuilders: Seq[MasterFeatureBuilder] ): Seq[MasterFeatureBuilder] = {
    
    def eachMftBuilderAndSubfFt( onEachMftBuilderAndChildFt: (MasterFeatureBuilder,Feature) => Unit ) {
      for( mftBuilder <- mftBuilders ) {
        mftBuilder.eachSubFeature( subFt => onEachMftBuilderAndChildFt(mftBuilder,subFt) )
      }
    }
    
    // Index master feature builders by child feature id
    val mftBuilderByFtId = new HashMap[Long,ArrayBuffer[MasterFeatureBuilder]]
    eachMftBuilderAndSubfFt( (mftb: MasterFeatureBuilder,ft: Feature) => 
      mftBuilderByFtId.getOrElseUpdate(ft.id, new ArrayBuffer[MasterFeatureBuilder] ) += mftb
    )
    
    // Find ambiguous sub features and clusterize corresponding master features
    val clusterizedMftBuilderIds = new HashSet[Long]
    val newMftBuilders = new ArrayBuffer[MasterFeatureBuilder]
    
    eachMftBuilderAndSubfFt { (mftb: MasterFeatureBuilder,ft: Feature) =>
      
      val ambiguousMftBuilders = mftBuilderByFtId(ft.id)
      
      // Check if this master feature contains multiple ambiguous feature mappings
      // and if it has not been already clusterized
      if( ambiguousMftBuilders.length > 1 && clusterizedMftBuilderIds.contains(mftb.id) == false ) {
        
        // Create an aggregation of MFT Builders feature having at least a shared sub-feature
        val ambiguousFeatureSet = new HashSet[Feature]()
        for( ambiguousMftBuilder <- ambiguousMftBuilders ) {
          ambiguousMftBuilder.eachSubFeature { ambiguousSubFt =>            
            for( mftb <- mftBuilderByFtId(ambiguousSubFt.id) ) {
              clusterizedMftBuilderIds += mftb.id
              mftb.eachSubFeature( ambiguousFeatureSet += _ )
            }
          }
        }
        
        // Group features by map child id
        val ftsByChildMapId = ambiguousFeatureSet.toSeq.groupBy( _.relations.processedMapId )
        
        // Iterate over each group of features to build feature clusters
        val mftChildren = new ArrayBuffer[Feature]
        for( (childMapId, features) <- ftsByChildMapId ) {
          if( features.length == 1 ) {
            mftChildren += features.head
          } else {
            
            if( ftClustererByChildMapId.contains(childMapId) == false ) {
              logger.error("can't find a clusterer for child map with id ="+childMapId)
            }
            
            val ftClusterer = ftClustererByChildMapId(childMapId)
            mftChildren += ftClusterer.buildFeatureCluster(features)
          }
        }
        
        // Create new master feature
        newMftBuilders += new MasterFeatureBuilder(bestFeature = mftChildren.head, children = mftChildren)
      }
    }
    
    // Append master features which have not been clusterized
    for( mftBuilder <- mftBuilders; if clusterizedMftBuilderIds.contains(mftBuilder.id) == false ) {
      newMftBuilders += mftBuilder
    }
    
    // Check that built master features do not contain multiple features for a given child map
    newMftBuilders.foreach { mftb =>
      val mftChildMapIds = mftb.children.map(_.relations.processedMapId)
      require(
        mftChildMapIds.length == mftChildMapIds.distinct.length,
        "master feature must not contain multiple features for a given child map"
      )
    }
    
    newMftBuilders
  }
  
}