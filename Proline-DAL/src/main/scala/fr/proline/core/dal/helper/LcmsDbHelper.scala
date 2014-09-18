package fr.proline.core.dal.helper

import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{DoJDBCReturningWork,DoJDBCWork}
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1,SelectQueryBuilder2}
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.profi.util.primitives._
  
class LcmsDbHelper( lcmsDbCtx: DatabaseConnectionContext ) {
  
  def getFeatureScoringById(): Map[Long,FeatureScoring] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      var colNames: Seq[String] = null
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,FeatureScoring]
      
      ezDBC.selectAndProcess( LcmsDbFeatureScoringTable.mkSelectQuery() ) { r =>
          
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
      
      ezDBC.selectAndProcess( LcmsDbPeakPickingSoftwareTable.mkSelectQuery() ) { r =>
          
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
      
      ezDBC.selectAndProcess( LcmsDbPeakelFittingModelTable.mkSelectQuery() ) { r =>
          
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
    
      val scanIdQuery = new SelectQueryBuilder1(LcmsDbScanSequenceTable).mkSelectQuery( (t,c) =>
        List(t.ID) -> "WHERE "~ t.RAW_FILE_NAME ~ "= ?"
      ) 
      
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>    
      ezDBC.selectHeadOption( scanIdQuery, rawFileName ) { v => toLong(v.nextAny) }    
    })
  }

  def getScanInitialIdById( scanSeqIds: Seq[Long] ): Map[Long,Int] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Int]
      
      val idsQuery = new SelectQueryBuilder1( LcmsDbScanTable ).mkSelectQuery( (t,c) =>
        List(t.ID, t.INITIAL_ID) -> "WHERE "~ t.SCAN_SEQUENCE_ID ~ "IN (" ~ scanSeqIds.mkString(",") ~ ")"
      )
      
      ezDBC.selectAndProcess( idsQuery ) { r =>
          val( scanId, scanInitialId ) = (toLong(r.nextAny), r.nextInt)
          mapBuilder += (scanId -> scanInitialId)
          ()
        }
          
      mapBuilder.result()
    
    })
  }
  

  def getMs2EventIdsByFtId( rawMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    	
      val featureMs2EventsByFtId = new java.util.HashMap[Long,ArrayBuffer[Long]]
      
      val featureIdAndMs2Query = new SelectQueryBuilder1( LcmsDbFeatureMs2EventTable ).mkSelectQuery((t,c) =>
        List(t.FEATURE_ID, t.MS2_EVENT_ID) -> "WHERE "~ t.RUN_MAP_ID ~ "IN (" ~ rawMapIds.mkString(",") ~ ")"
      ) 
      ezDBC.selectAndProcess( featureIdAndMs2Query ) { r =>
            
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