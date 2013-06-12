package fr.proline.core.dal.helper

import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{DoJDBCReturningWork,DoJDBCWork}
import fr.proline.core.om.model.lcms._
import fr.proline.util.primitives._
  
class LcmsDbHelper( lcmsDbCtx: DatabaseConnectionContext ) {
  
  def getFeatureScoringById(): Map[Long,FeatureScoring] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      var colNames: Seq[String] = null
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,FeatureScoring]
      
      ezDBC.selectAndProcess( "SELECT * FROM feature_scoring" ) { r =>
          
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the feature scoring record
        val ftScoringRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
        val ftScoringId = toLong(ftScoringRecord("id"))
        
        val ftScoring = new FeatureScoring( id = ftScoringId,
                                            name = ftScoringRecord("name").asInstanceOf[String],
                                            description = ftScoringRecord("description").asInstanceOf[String]
                                           )
        
        mapBuilder += ( ftScoringId -> ftScoring )
        
        ()
      }
      
      mapBuilder.result()
    
    })
  }
  
  def getPeakPickingSoftwareById(): Map[Long,PeakPickingSoftware] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      var colNames: Seq[String] = null
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,PeakPickingSoftware]
      
      ezDBC.selectAndProcess( "SELECT * FROM peak_picking_software" ) { r =>
          
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the feature scoring record
        val ppsRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
        val ppsId = toLong(ppsRecord("id"))
        
        val pps = new PeakPickingSoftware( id = ppsId,
                                           name = ppsRecord("name").asInstanceOf[String],
                                           version = ppsRecord("version").asInstanceOf[String],
                                           algorithm = ppsRecord("algorithm").asInstanceOf[String]
                                          )
        
        mapBuilder += ( ppsId -> pps )
        
        ()
      }
      
      mapBuilder.result()
    
    })
  }
  
  def getPeakelFittingModelById(): Map[Long,PeakelFittingModel] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      var colNames: Seq[String] = null
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,PeakelFittingModel]
      
      ezDBC.selectAndProcess( "SELECT * FROM peakel_fitting_model" ) { r =>
          
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the feature scoring record
        val peakelModelRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
        val peakelModelId = toLong(peakelModelRecord("id"))
        
        val peakelModel = new PeakelFittingModel( id = peakelModelId,
                                                  name = peakelModelRecord("name").asInstanceOf[String]
                                                 )
        
        mapBuilder += ( peakelModelId -> peakelModel )
        
        ()
      }
      
      mapBuilder.result()
    })
  }
  
  def getScanSequenceIdForRawFileName( rawFileName: String ): Option[Long] = {    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>    
      ezDBC.selectHeadOption( "SELECT id FROM run WHERE raw_file_name = ?", rawFileName ) { v => toLong(v.nextAny) }    
    })
  }

  def getScanInitialIdById( runIds: Seq[Long] ): Map[Long,Int] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Int]
      
      ezDBC.selectAndProcess(
          "SELECT id, initial_id FROM scan WHERE run_id IN (" + runIds.mkString(",") + ")"  ) { r =>
          val( scanId, scanInitialId ) = (toLong(r.nextAny), r.nextInt)
          mapBuilder += (scanId -> scanInitialId)
          ()
        }
          
      mapBuilder.result()
    
    })
  }
  

  def getMs2EventIdsByFtId( runMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      val featureMs2EventsByFtId = new java.util.HashMap[Long,ArrayBuffer[Long]]
      ezDBC.selectAndProcess( 
          "SELECT feature_id, ms2_event_id FROM feature_ms2_event " + 
          "WHERE run_map_id IN (" + runMapIds.mkString(",") + ")" ) { r =>
            
          val( featureId, ms2EventId ) = (toLong(r.nextAny), toLong(r.nextAny))
          if( !featureMs2EventsByFtId.containsKey(featureId) ) {
            featureMs2EventsByFtId.put(featureId, new ArrayBuffer[Long](1) )
          }
          featureMs2EventsByFtId.get(featureId) += ms2EventId
          ()
        }
      
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
      for( ftId <- featureMs2EventsByFtId.keySet().toArray() ) { 
        mapBuilder += ( toLong(ftId) -> featureMs2EventsByFtId.get(ftId).toArray[Long] )
      }
      mapBuilder.result()
    
    })

  }
  
}