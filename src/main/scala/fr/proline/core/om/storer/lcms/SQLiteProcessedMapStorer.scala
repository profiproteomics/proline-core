package fr.proline.core.om.storer.lcms

import fr.proline.core.LcmsDb
import net.noerd.prequel.IntFormattable
import net.noerd.prequel.ReusableStatement
import fr.proline.core.om.helper.SqlUtils.BoolToSQLStr

class SQLiteProcessedMapStorer( lcmsDb: LcmsDb ) extends IProcessedMapStorer {
  
  import scala.collection.mutable.ArrayBuffer
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.SQLFormatterImplicits._
  import fr.proline.core.om.lcms.MapClasses.ProcessedMap
  import fr.proline.core.om.lcms.FeatureClasses.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap ): Unit = {
    
    // Retrieve or create transaction
    val lcmsDbConn = lcmsDb.getOrCreateConnection()
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Create new map
    val runMapStorer = new SQLiteRunMapStorer( lcmsDb )
    val newMapId = runMapStorer.storeMap( processedMap, processedMap.modificationTimestamp )
     
    // Update processed map id
    processedMap.id = newMapId
    
    // Store the related a processed map
    val processedMapColumns = Seq( "id","number","normalization_factor","is_master","is_aln_reference","is_locked","map_set_id")
    val processedMapColNamesAsStr = processedMapColumns.mkString(",")
    /*stmt = lcmsDbConn.prepareStatement("INSERT INTO map("+processedMapColNamesAsStr+") VALUES(?, ?, ?, ?, ?, ?, ?)")
    val processedMapStmtBuilder = new ReusableStatement( stmt, lcmsDb.config.sqlFormatter )
      mapStmtBuilder << 
        newMapId <<
        processedMap.number <<
        processedMap.normalizationFactor <<
        BoolToSQLStr(processedMap.isMaster,lcmsDb.boolStrAsInt) <<
        BoolToSQLStr(processedMap.isAlnReference,lcmsDb.boolStrAsInt) <<
        BoolToSQLStr(processedMap.isLocked,lcmsDb.boolStrAsInt) <<
        processedMap.mapSetId
        
     // Execute statement
     stmt.execute()
     stmt.close()*/
     
    lcmsDbTx.execute("INSERT INTO processed_map("+processedMapColNamesAsStr+") VALUES(?,?,?,?,?,?,?)",
                        newMapId,
                        processedMap.number,
                        processedMap.normalizationFactor,
                        BoolToSQLStr(processedMap.isMaster,lcmsDb.boolStrAsInt),
                        BoolToSQLStr(processedMap.isAlnReference,lcmsDb.boolStrAsInt),
                        BoolToSQLStr(processedMap.isLocked,lcmsDb.boolStrAsInt),
                        processedMap.mapSetId
                      )
    
    // Link the processed map to the corresponding run maps
    lcmsDbTx.executeBatch("INSERT INTO processed_map_run_map VALUES(?,?,?,?,?,?,?)") { statement => 
      processedMap.runMapIds.foreach { runMapId =>
        statement.executeWith( newMapId, runMapId )
      }
    }
    
    /*val processedFtColumns = Seq( "processed_map_id","feature_id","calibrated_moz",
                                  "normalized_intensity","corrected_elution_time",
                                  "is_clusterized","selection_level")
    val processedFtColumnsAsStr = processedFtColumns.mkString(",")*/
    
    // Attach features to the processed map
    lcmsDbTx.executeBatch("INSERT INTO processed_map_feature_item VALUES("+ ("?" * 8).mkString(",") +")") { statement => 
      processedMap.features.foreach { feature =>
        
        if( feature.isCluster ) {
          
          // Update feature cluster map id
          feature.mapId = newMapId
          
          // Store processed sub-features
          for( subFt <- feature.subFeatures ) {
            insertProcessedFtUsingReusableStatement( subFt, statement )
          }
        }
        else {
          // Store the processed feature
          insertProcessedFtUsingReusableStatement( feature, statement )
        }
      }
    }

    // Store clusters
    this.storeFeatureClusters( processedMap.features )
    
    ()
  
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
    var nbSubFts = 0
    lcmsDbTx.executeBatch("INSERT INTO processed_map_feature_item VALUES("+ ("?" * 8).mkString(",") +")") { statement => 
      features.withFilter( _.isCluster ).foreach { ft =>
        nbSubFts += ft.subFeatures.length
        insertProcessedFtUsingReusableStatement( ft, statement )
      }
    }
    
    // Link feature clusters to their corresponding sub-features
    val subFtIds = new ArrayBuffer[Int](nbSubFts)
    lcmsDbTx.executeBatch("INSERT INTO feature_cluster_item VALUES(?,?,?)") { statement => 
      features.withFilter( _.isCluster ).foreach { clusterFt =>
        for( subFt <- clusterFt.subFeatures ) {
          subFtIds += subFt.id
          statement.executeWith( clusterFt.id, subFt.id, clusterFt.mapId )
        }
      }
    }
    
    // Set all sub-features of the processed map as clusterized
    subFtIds.grouped(lcmsDb.maxVariableNumber).foreach { tmpSubFtIds => {
      lcmsDbTx.execute( "UPDATE processed_map_feature_item SET is_clusterized = " + BoolToSQLStr(true,lcmsDb.boolStrAsInt) +
                        " WHERE feature_id IN (" + tmpSubFtIds.mkString(",") +")" )
      }
    }
    
    ()
  }
  
  private def insertProcessedFtUsingReusableStatement(ft: Feature, statement: ReusableStatement ): Unit = {
    
    val calibratedMoz = if( ft.calibratedMoz.isNaN ) None else Some(ft.calibratedMoz)
    val normalizedIntensity = if( ft.normalizedIntensity.isNaN ) None else Some(ft.normalizedIntensity)
    
    statement.executeWith( ft.mapId, ft.id, calibratedMoz, normalizedIntensity, ft.correctedElutionTime,                    
                           BoolToSQLStr(ft.isClusterized,lcmsDb.boolStrAsInt), ft.selectionLevel, Some(null) )
                           
  }
  
}