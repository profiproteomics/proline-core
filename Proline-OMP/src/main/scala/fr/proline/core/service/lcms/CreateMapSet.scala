package fr.proline.core.service.lcms

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.impl.RunLoader
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.core.service.lcms._
import fr.proline.repository.IDatabaseConnector

object CreateMapSet {

  def apply( lcmsDbConnector: IDatabaseConnector, mapSetName: String, runMaps: Seq[RunMap], 
             clusteringParams: ClusteringParams ): MapSet = {
    
    val mapSetCreator = new CreateMapSet( lcmsDbConnector, mapSetName, runMaps, clusteringParams  )
    mapSetCreator.runService()
    mapSetCreator.createdMapSet
    
  }
  
}

class CreateMapSet( val lcmsDbConnector: IDatabaseConnector, mapSetName: String, runMaps: Seq[RunMap], 
                    clusteringParams: ClusteringParams  ) extends ILcmsService {

  var createdMapSet: MapSet = null
  
  def runService(): Boolean = {
    
    // Define some vars
    val mapCount = runMaps.length
    val curTime = new java.util.Date()
    
    val pps = runMaps(0).peakPickingSoftware
    //die "can't filter data which are ! produced by mzDBaccess" if pps.name ne 'mzDBaccess' and this.hasFeatureFilters
    
    // Load runs
    val runLoader = new RunLoader( ezDBC )
    val runIds = runMaps.map { _.runId }
    val runs = runLoader.getRuns( runIds )
    val runById = runs.map { run => run.id -> run } toMap
    
    // Check if a transaction is already initiated
    val wasInTransaction = ezDBC.isInTransaction()
    if( !wasInTransaction ) ezDBC.beginTransaction()
    
    //print "Create map set\n" if this.verbose
    
    // Create new map set
    var newMapSetId = 0
    
    // TODO: use ORM layer to do this
    ezDBC.executePrepared("INSERT INTO map_set (name,map_count,creation_timestamp) VALUES (?,?,?)") { stmt => 
      stmt.executeWith( mapSetName, mapCount, curTime )
      newMapSetId = stmt.generatedInt
    }
    
    // Instantiate a processed map storer
    val processedMapStorer = ProcessedMapStorer( lcmsQueryHelper )
    val processedMaps = new ArrayBuffer[ProcessedMap]
    
    // Iterate over run maps to convert them in processed maps and store them
    var mapNumber = 0
    var alnRefMapId = 0
    for( runMap <- runMaps ) {
      mapNumber += 1
      
      // Convert to processed map
      var processedMap = runMap.toProcessedMap( id = runMap.id, number = mapNumber, mapSetId = newMapSetId )
      
      // Clean the map
      val run = runById( runMap.runId )
      processedMap = CleanMaps( lcmsDbConnector, processedMap, run.scans, Some(clusteringParams) )
      
      // Set first map as default alignment reference
      if( mapCount == 1 ) processedMap.isAlnReference = true
      processedMaps += processedMap
      
      // Store the map
      processedMapStorer.storeProcessedMap( processedMap )
      
      // Update map set alignment reference map
      if( processedMap.isAlnReference ) {
        alnRefMapId = processedMap.id
        ezDBC.execute( "UPDATE map_set SET al_reference_map_id = "+ alnRefMapId +" WHERE id = " + newMapSetId )
      }
      
    }
    
    // Compute map summaries
    /*require Pairs::Lcms::Cmd::Update::MapSummary
    print "computing map summaries...\n" if this.verbose
    
    for( rdbProcessedMap <- rdbProcessedMaps ) {
      val mapSummaryUpdater = new Pairs::Lcms::Cmd::Update::MapSummary( project_id = this.projectId, map_id = rdbProcessedMap.id )
      mapSummaryUpdater.runWithVars( rdbProcessedMap.map )
    }*/
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) ezDBC.commitTransaction()
    
    createdMapSet = new MapSet(
                            id = newMapSetId,
                            name = mapSetName,
                            creationTimestamp = curTime,
                            childMaps = processedMaps.toArray,
                            alnReferenceMapId = alnRefMapId
                            )
    
    true
  }
  
}