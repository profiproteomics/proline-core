package fr.proline.core.service.lcms

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._
import fr.profi.api.service.IService
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.lcms.LcmsDbMapSetTable
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.storer.lcms.ProcessedMapStorer
import fr.proline.core.service.lcms._
import fr.proline.repository.IDatabaseConnector

// TODO: rename into StoreMapSet
object CreateMapSet {

  def apply(
    lcmsDbCtx: LcMsDbConnectionContext,
    mapSetName: String,
    processedMaps: Seq[ProcessedMap]
    //clusteringParams: ClusteringParams
  ): MapSet = {
    
    val mapSetCreator = new CreateMapSet( lcmsDbCtx, mapSetName, processedMaps  )
    mapSetCreator.runService()
    mapSetCreator.createdMapSet
    
  }
  
}

// TODO: rename into StoreMapSet
class CreateMapSet(
  val lcmsDbCtx: LcMsDbConnectionContext,
  mapSetName: String,
  processedMaps: Seq[ProcessedMap]
  //rawMaps: Seq[RawMap],
  //clusteringParams: ClusteringParams
) extends ILcMsService {

  var createdMapSet: MapSet = null
  
  def runService(): Boolean = {
    
    // Define some vars
    val mapCount = processedMaps.length
    val curTime = new java.util.Date()
    
    //val pps = rawMaps(0).peakPickingSoftware
    //die "can't filter data which are ! produced by mzDBaccess" if pps.name ne 'mzDBaccess' and this.hasFeatureFilters
    
    // Load runs
    //val scanSeqProvider = new SQLScanSequenceProvider( lcmsDbCtx )
    //val runIds = rawMaps.map { _.runId }
    //val runs = scanSeqProvider.getScanSequences( runIds )
    //val runById = runs.map { run => run.runId -> run } toMap
    
    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if( !wasInTransaction ) lcmsDbCtx.beginTransaction()
    
    //print "Create map set\n" if this.verbose
    
    // Define some vars
    var newMapSetId: Long = 0L
    //val processedMaps = new ArrayBuffer[ProcessedMap]
    
    DoJDBCWork.withEzDBC( lcmsDbCtx) { ezDBC =>
      
      // Insert a new map set
      val mapSetInsertQuery = LcmsDbMapSetTable.mkInsertQuery( (c,colsList) => 
        List( c.NAME,c.MAP_COUNT,c.CREATION_TIMESTAMP)
      )      
      ezDBC.executePrepared(mapSetInsertQuery,true) { stmt => 
        stmt.executeWith( mapSetName, mapCount, curTime )
        newMapSetId = stmt.generatedLong
      }
      
      // Iterate over run maps to convert them in processed maps and store them
      var mapNumber = 0
      
      // Instantiate a processed map storer
      val processedMapStorer = ProcessedMapStorer( lcmsDbCtx )
      
      logger.info("saving the processed maps...")
      for( processedMap <- processedMaps ) {
        mapNumber += 1
        
        // Convert to processed map
        //var processedMap = rawMap.toProcessedMap( number = mapNumber, mapSetId = newMapSetId )
        
        // Update the map set id of the processed map
        processedMap.mapSetId = newMapSetId
        
        // Insert the processed map
        processedMapStorer.insertProcessedMap( processedMap )
        
        // Clean the map
        //val run = runById( rawMap.runId )
        //processedMap = CleanMaps( lcmsDbCtx, processedMap, run.scans, Some(clusteringParams) )
        
        // Set first map as default alignment reference
        if( mapCount == 1 ) processedMap.isAlnReference = true
        //processedMaps += processedMap
        
      }
      
      // Compute map summaries
      /*require Pairs::Lcms::Cmd::Update::MapSummary
      print "computing map summaries...\n" if this.verbose
      
      for( rdbProcessedMap <- rdbProcessedMaps ) {
        val mapSummaryUpdater = new Pairs::Lcms::Cmd::Update::MapSummary( project_id = this.projectId, map_id = rdbProcessedMap.id )
        mapSummaryUpdater.runWithVars( rdbProcessedMap.map )
      }*/
      
    }
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) lcmsDbCtx.commitTransaction()
    
    createdMapSet = new MapSet(
      id = newMapSetId,
      name = mapSetName,
      creationTimestamp = curTime,
      childMaps = processedMaps.toArray
    )
    
    true
  }
  
}