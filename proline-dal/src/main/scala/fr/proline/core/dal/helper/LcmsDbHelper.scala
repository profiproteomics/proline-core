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
    
    val scoringCols = LcmsDbFeatureScoringColumns
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx) { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,FeatureScoring]
      
      ezDBC.selectAndProcess( LcmsDbFeatureScoringTable.mkSelectQuery() ) { r =>
        
        // Build the feature scoring record
        val ftScoringId = r.getLong("id")
        
        val ftScoring = new FeatureScoring(
          id = ftScoringId,
          name = r.getString(scoringCols.NAME),
          description = r.getString(scoringCols.DESCRIPTION)
        )
        
        mapBuilder += ( ftScoringId -> ftScoring )        
        ()
      }
      
      mapBuilder.result()
    
    }//end DoJDBCReturningWork
  }
  
  def getPeakPickingSoftwareById(): Map[Long,PeakPickingSoftware] = {
    
    val ppsCols = LcmsDbPeakPickingSoftwareColumns
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx) { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,PeakPickingSoftware]
      
      ezDBC.selectAndProcess( LcmsDbPeakPickingSoftwareTable.mkSelectQuery() ) { r =>
        
        // Build the feature scoring record
        val ppsId = r.getLong("id")
        
        val pps = new PeakPickingSoftware(
          id = ppsId,
          name = r.getString(ppsCols.NAME),
          version = r.getString(ppsCols.VERSION),
          algorithm = r.getString(ppsCols.ALGORITHM)
        )
        
        mapBuilder += ( ppsId -> pps )        
        ()
      }
      
      mapBuilder.result()
    
    }//end doWork
  }
  
  def getPeakelFittingModelById(): Map[Long,PeakelFittingModel] = {
    
    val fittingModelCols = LcmsDbPeakelFittingModelColumns
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx) { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,PeakelFittingModel]
      
      ezDBC.selectAndProcess( LcmsDbPeakelFittingModelTable.mkSelectQuery() ) { r =>
        
        // Build the feature scoring record
        val peakelModelId = r.getLong("id")
        
        val peakelModel = new PeakelFittingModel(
          id = peakelModelId,
          name = r.getString(fittingModelCols.NAME)
        )
        
        mapBuilder += ( peakelModelId -> peakelModel )        
        ()
      }
      
      mapBuilder.result()
    } // end DoJDBCReturningWork
  }

  def findScanSequenceIdForRawFileIdentifier(rawFileIdent: String): Option[Long] = {
    require( rawFileIdent != null, "rawFileIdent is null" )

    val scanIdQuery = new SelectQueryBuilder1(LcmsDbScanSequenceTable).mkSelectQuery((t, c) =>
      List(t.ID) -> "WHERE " ~ t.RAW_FILE_IDENTIFIER ~ "= ?"
    )

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      ezDBC.selectHeadOption(scanIdQuery, rawFileIdent) { v => toLong(v.nextAny) }
    }// end DoJDBCReturningWork
  }

  def getScanInitialIdById( scanSeqIds: Seq[Long] ): Map[Long,Int] = {
    require( scanSeqIds != null, "scanSeqIds is null" )
    if( scanSeqIds.isEmpty ) return Map()
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx) { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Int]
      
      val idsQuery = new SelectQueryBuilder1( LcmsDbScanTable ).mkSelectQuery( (t,c) =>
        List(t.ID, t.INITIAL_ID) -> "WHERE "~ scanSeqIds.map(id => t.SCAN_SEQUENCE_ID + "=" + id).mkString(" OR ")
      )
      
      ezDBC.selectAndProcess( idsQuery ) { r =>
          val( scanId, scanInitialId ) = (toLong(r.nextAny), r.nextInt)
          mapBuilder += (scanId -> scanInitialId)
          ()
        }
          
      mapBuilder.result()
    
    }// end DoJDBCReturningWork
  }

  def getMs2EventIdsByFtId( rawMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    require( rawMapIds != null, "rawMapIds is null" )
    if( rawMapIds.isEmpty ) return Map()
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx) { ezDBC =>
    	
      val featureMs2EventsByFtId = new java.util.HashMap[Long,ArrayBuffer[Long]]
      
      val featureIdAndMs2Query = new SelectQueryBuilder1( LcmsDbFeatureMs2EventTable ).mkSelectQuery((t,c) =>
        List(t.FEATURE_ID, t.MS2_EVENT_ID) -> "WHERE "~ t.RAW_MAP_ID ~ " IN (" ~ rawMapIds.mkString(",") ~ ")"
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
    } // end DoJDBCReturningWork

  }
  
}