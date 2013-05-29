package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbMasterFeatureItemTable
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.storer.lcms.IMasterMapStorer

class SQLMasterMapStorer(lcmsDbCtx: DatabaseConnectionContext) extends SQLProcessedMapStorer(lcmsDbCtx) with IMasterMapStorer {

  def storeMasterMap( masterMap: ProcessedMap ): Unit = {
    
    val mapSetId = masterMap.mapSetId
    if( mapSetId <= 0 ) {
      throw new Exception("invalid map set id for the current master map")
    }
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
    
      // Insert the master map in the processed_map and map tables
      val newMasterMapId = this.insertProcessedMap( ezDBC, masterMap )
      
      // Update the master map id
      masterMap.id = newMasterMapId
      
      // Update master map id of the map set in the database
      ezDBC.execute( "UPDATE map_set SET master_map_id = " + newMasterMapId + " WHERE id = " + mapSetId )
      
      // Link the master map to the corresponding run maps
      this.linkProcessedMapToRunMaps( ezDBC, masterMap )
      
      // Insert features
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery, true) { featureInsertStmt =>
      
        // Update master feature map id and insert master features in the feature table
        for( mft <- masterMap.features ) {
          mft.relations.mapId = newMasterMapId      
          mft.id = this.insertFeatureUsingPreparedStatement( mft, featureInsertStmt )
        }
        
      }
      
      // Link master features to their children
      ezDBC.executePrepared(LcmsDbMasterFeatureItemTable.mkInsertQuery) { statement =>
        
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
      this.insertProcessedMapFeatureItems( ezDBC, masterMap )
    
    })
  
  }
    
}