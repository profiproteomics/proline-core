package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.LcmsDb
import fr.proline.core.om.storer.lcms.IProcessedMapStorer

class SQLiteProcessedMapStorer( lcmsDb: LcmsDb ) extends IProcessedMapStorer {
  
  import scala.collection.mutable.ArrayBuffer
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.lcms.ProcessedMap
  import fr.proline.core.om.model.lcms.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = true ): Unit = {
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Insert processed map
    val newProcessedMapId = this.insertProcessedMap( processedMap )
    
    // Update processed map id
    processedMap.id = newProcessedMapId
    
    // Link the processed map to the corresponding run maps
    this.linkProcessedMapToRunMaps( processedMap )
    
    // Insert processed map feature items
    this.insertProcessedMapFeatureItems( processedMap )

    // Store clusters
    this.storeFeatureClusters( processedMap.features )
    
    ()
  
  }
  
  def insertProcessedMap( processedMap: ProcessedMap ): Int = {
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Create new map
    val newMapId = new SQLiteRunMapStorer( lcmsDb ).insertMap( processedMap, processedMap.modificationTimestamp )
    
    // Store the related a processed map
    /*
    val processedMapColumns = Seq( "id","number","normalization_factor","is_master","is_aln_reference","is_locked","map_set_id")
    val processedMapColNamesAsStr = processedMapColumns.mkString(",")
    lcmsDbTx.execute("INSERT INTO processed_map("+processedMapColNamesAsStr+") VALUES (?,?,?,?,?,?,?)",
                    newMapId,
                    processedMap.number,
                    processedMap.normalizationFactor,
                    BoolToSQLStr(processedMap.isMaster,lcmsDb.boolStrAsInt),
                    BoolToSQLStr(processedMap.isAlnReference,lcmsDb.boolStrAsInt),
                    BoolToSQLStr(processedMap.isLocked,lcmsDb.boolStrAsInt),
                    processedMap.mapSetId
                  )
    */
                      
    lcmsDbTx.executeBatch("INSERT INTO processed_map VALUES (?,?,?,?,?,?,?)") { statement => 
      statement.executeWith(  newMapId,
                              processedMap.number,
                              processedMap.normalizationFactor,
                              BoolToSQLStr(processedMap.isMaster,lcmsDb.boolStrAsInt),
                              BoolToSQLStr(processedMap.isAlnReference,lcmsDb.boolStrAsInt),
                              BoolToSQLStr(processedMap.isLocked,lcmsDb.boolStrAsInt),
                              processedMap.mapSetId
                            )
    }
    
    newMapId
  }
  
  def linkProcessedMapToRunMaps( processedMap: ProcessedMap ): Unit = {
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    lcmsDbTx.executeBatch("INSERT INTO processed_map_run_map VALUES (?,?)") { statement => 
      processedMap.runMapIds.foreach { runMapId =>
        statement.executeWith( processedMap.id, runMapId )
      }
    }
    
  }
  
  def insertProcessedMapFeatureItems( processedMap: ProcessedMap ): Unit = {
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    val processedMapId = processedMap.id
    
    // Attach features to the processed map
    lcmsDbTx.executeBatch("INSERT INTO processed_map_feature_item VALUES("+ ("?" * 8).mkString(",") +")") { statement => 
      processedMap.features.foreach { feature =>
        
        // Update feature map id
        feature.mapId = processedMapId
        
        if( feature.isCluster ) {

          // Store cluster sub-features
          for( subFt <- feature.subFeatures ) {
            // Update sub-feature map id
            subFt.mapId = processedMapId
            // Store the processed feature
            insertProcessedMapFtItemUsingReusableStatement( subFt, statement )
          }
        }
        else {
          // Store the processed feature
          insertProcessedMapFtItemUsingReusableStatement( feature, statement )
        }
      }
    }
    
  }
  
  def storeFeatureClusters( features: Seq[Feature] ): Unit = {
    
    // Instantiate a run map storer
    val runMapStorer = new SQLiteRunMapStorer( lcmsDb )
    
    // Retrieve or create database connection and transaction
    val lcmsDbConn = lcmsDb.getOrCreateConnection()
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Prepare feature insert statement
    val featureInsertStmt = runMapStorer.prepareStatementForFeatureInsert( lcmsDbConn )
    
    // Store feature clusters 
    features.withFilter( _.isCluster ).foreach { clusterFt =>
        
      // Store the feature cluster
      val newFtId = runMapStorer.insertFeatureUsingPreparedStatement( clusterFt, featureInsertStmt )
      
      // Update feature cluster id
      clusterFt.id = newFtId
 
    }
    featureInsertStmt.close()
    
    // Store processed feature items corresponding to feature clusters
    //var nbSubFts = 0
    lcmsDbTx.executeBatch("INSERT INTO processed_map_feature_item VALUES("+ ("?" * 8).mkString(",") +")") { statement => 
      features.withFilter( _.isCluster ).foreach { ft =>
        //nbSubFts += ft.subFeatures.length
        insertProcessedMapFtItemUsingReusableStatement( ft, statement )
      }
    }
    
    // Link feature clusters to their corresponding sub-features
    //val subFtIds = new ArrayBuffer[Int](nbSubFts)
    lcmsDbTx.executeBatch("INSERT INTO feature_cluster_item VALUES(?,?,?)") { statement => 
      features.withFilter( _.isCluster ).foreach { clusterFt =>
        for( subFt <- clusterFt.subFeatures ) {
          //subFtIds += subFt.id
          statement.executeWith( clusterFt.id, subFt.id, clusterFt.mapId )
        }
      }
    }
    
    // Set all sub-features of the processed map as clusterized
    /*subFtIds.grouped(lcmsDb.maxVariableNumber).foreach { tmpSubFtIds => {
      lcmsDbTx.execute( "UPDATE processed_map_feature_item SET is_clusterized = " + BoolToSQLStr(true,lcmsDb.boolStrAsInt) +
                        " WHERE feature_id IN (" + tmpSubFtIds.mkString(",") +")" )
      }
    }*/
    
    ()
  }
  
  private def insertProcessedMapFtItemUsingReusableStatement( ft: Feature, statement: ReusableStatement ): Unit = {
    
    val calibratedMoz = if( ft.calibratedMoz.isNaN ) None else Some(ft.calibratedMoz)
    val normalizedIntensity = if( ft.normalizedIntensity.isNaN ) None else Some(ft.normalizedIntensity)
    
    statement.executeWith( ft.mapId, ft.id, calibratedMoz, normalizedIntensity, ft.correctedElutionTime,                    
                           BoolToSQLStr(ft.isClusterized,lcmsDb.boolStrAsInt), ft.selectionLevel, Some(null) )
                           
  }
  
}