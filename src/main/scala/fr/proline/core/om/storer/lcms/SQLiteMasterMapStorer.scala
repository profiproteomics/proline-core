package fr.proline.core.om.storer.lcms

import fr.proline.core.LcmsDb

class SQLiteMasterMapStorer( lcmsDb: LcmsDb ) extends IMasterMapStorer {
  
  import scala.collection.mutable.ArrayBuffer
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.SQLFormatterImplicits._
  import fr.proline.core.om.helper.SqlUtils.BoolToSQLStr
  import fr.proline.core.om.lcms.MapClasses.ProcessedMap
  import fr.proline.core.om.lcms.FeatureClasses.Feature
  
  def storeMasterMap( masterMap: ProcessedMap ): Unit = {
    
    val mapSetId = masterMap.mapSetId
    if( mapSetId <= 0 ) {
      throw new Exception("invalid map set id for the current master map")
    }
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Insert the master map in the processed_map and map tables
    val processedMapStorer = new SQLiteProcessedMapStorer(lcmsDb)
    val newMasterMapId = processedMapStorer.insertProcessedMap( masterMap )
    
    // Update the master map id
    masterMap.id = newMasterMapId
    
    // Update master map id of the map set in the database
    lcmsDbTx.execute( "UPDATE map_set SET master_map_id = " + newMasterMapId + " WHERE map_set_id = " + mapSetId )
    
    // Link the master map to the corresponding run maps
    processedMapStorer.linkProcessedMapToRunMaps( masterMap )
    
    // Instantiate a run map storer
    val runMapStorer = new SQLiteRunMapStorer(lcmsDb)
    val featureInsertStmt = runMapStorer.prepareStatementForFeatureInsert( lcmsDb.getOrCreateConnection() )
    
    // Update master feature map id and insert master features in the feature table
    for( mft <- masterMap.features ) {
      mft.mapId = newMasterMapId      
      mft.id = runMapStorer.insertFeatureUsingPreparedStatement( mft, featureInsertStmt )
    }
    
    featureInsertStmt.close()
    
    // Link master features to their children
    lcmsDbTx.executeBatch("INSERT INTO master_feature_item VALUES (?,?,?,?)") { statement => 
      masterMap.features.foreach { mft =>
        
        // Retrieve best child id
        val bestChildId = mft.bestChildId
        
        mft.children.foreach { childFt =>
          val isBestChild = if( childFt.id == bestChildId ) true else false
          statement.executeWith( mft.id, childFt.id, BoolToSQLStr(isBestChild,lcmsDb.boolStrAsInt), newMasterMapId )
        }
      }
    }
    
    // Insert processed map feature items
    processedMapStorer.insertProcessedMapFeatureItems( masterMap )
    
    ()
  
  }
    
}