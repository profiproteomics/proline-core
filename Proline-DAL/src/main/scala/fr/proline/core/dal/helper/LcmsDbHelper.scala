package fr.proline.core.dal.helper

import fr.profi.jdbc.SQLQueryExecution

class LcmsDbHelper( sqlExec: SQLQueryExecution ) {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._

  // TODO: put in a LcmsDb helper
  def getFeatureScoringById(): Map[Int,FeatureScoring] = {
    
    var colNames: Seq[String] = null
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,FeatureScoring]
    
    sqlExec.selectAndProcess( "SELECT * FROM feature_scoring" ) { r =>
        
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the feature scoring record
      val ftScoringRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
      val ftScoringId = ftScoringRecord("id").asInstanceOf[Int]
      
      val ftScoring = new FeatureScoring( id = ftScoringId,
                                          name = ftScoringRecord("name").asInstanceOf[String],
                                          description = ftScoringRecord("description").asInstanceOf[String]
                                         )
      
      mapBuilder += ( ftScoringId -> ftScoring )
      
      ()
    }
    
    mapBuilder.result()
  }
  
  // TODO: put in a LcmsDb helper
  def getPeakPickingSoftwareById(): Map[Int,PeakPickingSoftware] = {
    
    var colNames: Seq[String] = null
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,PeakPickingSoftware]
    
    sqlExec.selectAndProcess( "SELECT * FROM peak_picking_software" ) { r =>
        
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the feature scoring record
      val ppsRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
      val ppsId = ppsRecord("id").asInstanceOf[Int]
      
      val pps = new PeakPickingSoftware( id = ppsId,
                                         name = ppsRecord("name").asInstanceOf[String],
                                         version = ppsRecord("version").asInstanceOf[String],
                                         algorithm = ppsRecord("algorithm").asInstanceOf[String]
                                        )
      
      mapBuilder += ( ppsId -> pps )
      
      ()
    }
    
    mapBuilder.result()
  }

  // TODO: put in a LcmsDb helper
  def getPeakelFittingModelById(): Map[Int,PeakelFittingModel] = {
    
    var colNames: Seq[String] = null
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,PeakelFittingModel]
    
    sqlExec.selectAndProcess( "SELECT * FROM peakel_fitting_model" ) { r =>
        
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the feature scoring record
      val peakelModelRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
      val peakelModelId = peakelModelRecord("id").asInstanceOf[Int]
      
      val peakelModel = new PeakelFittingModel( id = peakelModelId,
                                                name = peakelModelRecord("name").asInstanceOf[String]
                                               )
      
      mapBuilder += ( peakelModelId -> peakelModel )
      
      ()
    }
    
    mapBuilder.result()
  }
  

  def getScanInitialIdById( runIds: Seq[Int] ): Map[Int,Int] = {
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Int]
    
    sqlExec.selectAndProcess(
        "SELECT id, initial_id FROM scan WHERE run_id IN (" + runIds.mkString(",") + ")"  ) { r =>
        val( scanId, scanInitialId ) = (r.nextInt, r.nextInt)
        mapBuilder += (scanId -> scanInitialId)
        ()
      }
        
    mapBuilder.result()
  }
  

  def getMs2EventIdsByFtId( runMapIds: Seq[Int] ): Map[Int,Array[Int]] = {
    
    val featureMs2EventsByFtId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    sqlExec.selectAndProcess( 
        "SELECT feature_id, ms2_event_id FROM feature_ms2_event " + 
        "WHERE run_map_id IN (" + runMapIds.mkString(",") + ")" ) { r =>
          
        val( featureId, ms2EventId ) = (r.nextInt, r.nextInt)
        if( !featureMs2EventsByFtId.containsKey(featureId) ) {
          featureMs2EventsByFtId.put(featureId, new ArrayBuffer[Int](1) )
        }
        featureMs2EventsByFtId.get(featureId) += ms2EventId
        ()
      }
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
    for( ftId <- featureMs2EventsByFtId.keySet().toArray() ) { 
      mapBuilder += ( ftId.asInstanceOf[Int] -> featureMs2EventsByFtId.get(ftId).toArray[Int] )
    }
    mapBuilder.result()

  }
  
}