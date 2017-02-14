package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbMasterFeatureItemTable
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.storer.lcms._

class SQLMasterMapStorer(
  override val lcmsDbCtx: LcMsDbConnectionContext,
  override val featureWriter: SQLFeatureWriter
) extends SQLProcessedMapStorer(lcmsDbCtx, featureWriter) with IMasterMapStorer {

  def storeMasterMap( masterMap: ProcessedMap ): Unit = {
    
    val mapSetId = masterMap.mapSetId
    if( mapSetId <= 0 ) {
      throw new Exception("invalid map set id for the current master map")
    }
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      // Insert the master map in the processed_map and map tables
      val newMasterMapId = this._insertProcessedMap( ezDBC, masterMap )
      
      // Update the master map id
      masterMap.id = newMasterMapId
      
      // Update master map id of the map set in the database
      ezDBC.execute( "UPDATE map_set SET master_map_id = " + newMasterMapId + " WHERE id = " + mapSetId )
      
      // Insert features
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { featureInsertStmt =>
        
        // Update master feature map id and insert master features in the feature table
        for( mft <- masterMap.features ) {
          mft.relations.processedMapId = newMasterMapId
          mft.id = this.featureWriter.insertFeatureUsingPreparedStatement( mft, featureInsertStmt )
        }
        
      }
      
      // Link master features to their children
      ezDBC.executeInBatch(LcmsDbMasterFeatureItemTable.mkInsertQuery) { statement =>
        
        masterMap.features.foreach { mft =>
          
          // Retrieve best child id
          val bestChildId = mft.relations.bestChildId
          
          mft.children.foreach { childFt =>
            val isBestChild = if( childFt.id == bestChildId ) true else false
            statement.executeWith( mft.id, childFt.id, isBestChild, newMasterMapId )
          }
        }
        
      }
      
      // Insert extra master map features data into to processed map feature items
      this.featureWriter.insertProcessedMapFeatureItems( masterMap )
    }
  
  }
    
}