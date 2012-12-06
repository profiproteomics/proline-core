package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.storer.lcms.IRunMapStorer

class SQLiteRunMapStorer( lcmsDb: SQLQueryHelper ) extends IRunMapStorer {
  
  import scala.collection.mutable.ArrayBuffer
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.util.sql.BoolToSQLStr
  import fr.proline.core.om.model.lcms._

  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit = {
    
    // Retrieve or create transaction
    val lcmsDbConn = lcmsDb.connection
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Create new map
    val newRunMapId = this.insertMap( runMap, null )
    
    // Update run map id
    runMap.id = newRunMapId
    
    // Store the related run map   
    val peakPickingSoftwareId = if( runMap.featureScoring != null ) Some(runMap.peakPickingSoftware.id) else None
    val peakelFittingModelId = if( runMap.featureScoring != null ) Some(runMap.peakelFittingModel.id) else None
     
    lcmsDbTx.execute("INSERT INTO run_map VALUES (?,?,?,?)",
                        newRunMapId,
                        runMap.runId,
                        peakPickingSoftwareId,
                        peakelFittingModelId
                      )
                      
    // Prepare insert statement
    val featureInsertStmt = this.prepareStatementForFeatureInsert( lcmsDbConn )
    
    // Loop over features to import them
    val flattenedFeatures = new ArrayBuffer[Feature](runMap.features.length)
    for( ft <- runMap.features ) {
      ft.relations.mapId = newRunMapId
      
      val newFtId = this.insertFeatureUsingPreparedStatement( ft, featureInsertStmt )
      ft.id = newFtId
      
      flattenedFeatures += ft
      
      // Import overlapping features
      if( ft.overlappingFeatures != null ) {
        for( olpFt <- ft.overlappingFeatures ) {
          //ft.relations.mapId = newRunMapId
          
          val newFtId = this.insertFeatureUsingPreparedStatement( olpFt, featureInsertStmt )
          ft.id = newFtId
          
          flattenedFeatures += olpFt
        }
      }
    }
    
    // Release prepared statement
    featureInsertStmt.close()
    
    // Link the features to overlapping features
    lcmsDbTx.executeBatch("INSERT INTO feature_overlap_map VALUES (?,?,?)") { statement => 
      runMap.features.foreach { ft =>
        if( ft.overlappingFeatures != null ) {
          for( olpFt <- ft.overlappingFeatures ) {
            statement.executeWith( ft.id, olpFt.id, newRunMapId )
          }
        }
      }
    }
    
    // Link the features to MS2 scans
    lcmsDbTx.executeBatch("INSERT INTO feature_ms2_event VALUES (?,?,?)") { statement => 
      flattenedFeatures.foreach { ft =>
        if( ft.relations.ms2EventIds != null ) {
          for( ms2EventId <- ft.relations.ms2EventIds) statement.executeWith( ft.id, ms2EventId, newRunMapId )
        }
      }
    }
    
/*
    ////// Import isotopic patterns
    if( this.storePeaks && ft.hasIsotopicPatterns ) {
      
      val ipHashes
      foreach val ip (@{ft.isotopicPatterns}) {
        val ipHash = ref(ip) ne 'HASH' ? ip.attributesAsHashref : ip
        push( ipHashes, ipHash )
      }
      
      ////// Store properties in the MSI-DB
      val rdbObjectTree = Pairs::Lcms::RDBO::ObjectTree.new(
                              schema_name     = 'feature.isotopic_patterns',
                              serialized_data = encode_json( ipHashes ),
                             )
      rdbObjectTree.save
      
      ////// Attach isotopic patterns to the feature
      Pairs::Lcms::RDBO::FeatureObjectTreeMap.new(
                          feature_id = rdbFtId,
                          object_tree_id = rdbObjectTree.id,
                          schema_name = 'feature.isotopic_patterns',
                          db = lcmsRdb
                        ).save
                        
      // TODO store a link using map_object_tree_mapping
      
    }
    
    */

    ()
  
  }
  
  def insertMap( lcmsMap: LcmsMap, modificationTimestamp: java.util.Date ): Int = {
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    //val curDate = new java.util.Date
    val ftScoringId = if( lcmsMap.featureScoring != null ) Some(lcmsMap.featureScoring.id) else None
    val lcmsMapType = if( lcmsMap.isProcessed ) 1 else 0
    
    // TODO: store properties
    
    // Create a new map
    val mapColumns = Seq( "name","description","type","creation_timestamp","modification_timestamp","feature_scoring_id")
    val mapColNamesAsStr = mapColumns.mkString(",")
    
    //var stmt = lcmsDbConn.prepareStatement("INSERT INTO map("+mapColNamesAsStr+") VALUES(?, ?, ?, ?, ?, ?)")
    /*val mapRecordBuilder = new ReusableStatement( stmt, lcmsDb.config.sqlFormatter )
    mapRecordBuilder <<
      lcmsMap.name <<
      lcmsMap.description <<
      lcmsMapType <<
      lcmsMap.creationTimestamp <<
      modificationTimestamp <<
      ftScoringId
     
    // Execute statement
    stmt.execute()
    val newMapId = stmt.getGeneratedKeys().getInt("last_insert_rowid()") // SQLite specific query
    stmt.close()*/
    
    var newMapId = 0
    lcmsDbTx.executeBatch("INSERT INTO map("+mapColNamesAsStr+") VALUES(?,?,?,?,?,?)") { statement => 
      val mapDesc = if( lcmsMap.description == null ) None else Some(lcmsMap.description)
      
      statement.executeWith( lcmsMap.name,
                             mapDesc,
                             lcmsMapType,
                             lcmsDb.stringifyDate(lcmsMap.creationTimestamp),
                             lcmsDb.stringifyDate(modificationTimestamp),
                             ftScoringId
                            )
       newMapId = statement.wrapped.getGeneratedKeys().getInt("last_insert_rowid()") // SQLite specific query
    }
    
    newMapId
    
  }
  
  def prepareStatementForFeatureInsert( lcmsDbConn: java.sql.Connection ): java.sql.PreparedStatement = {
    lcmsDbConn.prepareStatement("INSERT INTO feature VALUES ("+ ("?" * 18).mkString(",") +")")
  }
  
  def insertFeatureUsingPreparedStatement( ft: Feature, stmt: java.sql.PreparedStatement ): Int = {
    
    val ftRelations = ft.relations
    val qualityScore = if( ft.qualityScore.isNaN ) None else Some(ft.qualityScore)
    val theoFtId = if( ftRelations.theoreticalFeatureId == 0 ) None else Some(ftRelations.theoreticalFeatureId)
    val compoundId = if( ftRelations.compoundId == 0 ) None else Some(ftRelations.compoundId)
    val mapLayerId = if( ftRelations.mapLayerId == 0 ) None else Some(ftRelations.mapLayerId)
    
    // TODO: store properties    
    
    val ftRecordBuilder = new ReusableStatement( stmt, lcmsDb.sqlFormatter )
    ftRecordBuilder <<
      Option.empty[Int] <<
      ft.moz <<
      ft.intensity <<
      ft.charge <<
      ft.elutionTime <<
      qualityScore <<
      ft.ms1Count <<
      ft.ms2Count <<
      BoolToSQLStr(ft.isCluster,false) <<
      BoolToSQLStr(ft.isOverlapping,false) <<
      Option(null) <<
      ftRelations.firstScanId <<
      ftRelations.lastScanId <<
      ftRelations.apexScanId <<
      theoFtId <<
      compoundId <<
      mapLayerId <<
      ftRelations.mapId
     
    // Execute statement
    stmt.execute()
    stmt.getGeneratedKeys().getInt("last_insert_rowid()") // SQLite specific query
    
  }
 
  
}