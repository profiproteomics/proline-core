package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy._
import fr.profi.jdbc.StatementWrapper

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{DoJDBCWork,DoJDBCReturningWork}
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureClusterItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapFeatureItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapRunMapMappingTable
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.storer.lcms.IProcessedMapStorer

class SQLProcessedMapStorer(lcmsDbCtx: DatabaseConnectionContext) extends SQLRunMapStorer(lcmsDbCtx) with IProcessedMapStorer with Logging {
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = true ): Unit = {    
      
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      if( processedMap.id < 0 ) {
        // Insert the processed map if not already done
        this._insertProcessedMap( ezDBC, processedMap )
      } else {
        // Else update mutable fields
        this._updateProcessedMap( ezDBC, processedMap )
      }
      
      // Insert processed map feature items
      this.insertProcessedMapFeatureItems( ezDBC, processedMap )
      
    })
    
    // Store clusters
    this.storeFeatureClusters( processedMap.features )
  
  }
  
  def insertProcessedMap( processedMap: ProcessedMap ): Long = {    
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      this._insertProcessedMap(ezDBC,processedMap)
    })
  }
  
  protected def _insertProcessedMap( ezDBC: EasyDBC, processedMap: ProcessedMap ): Long = {
    
    // Insert map data
    val newMapId = this.insertMap( ezDBC, processedMap, processedMap.modificationTimestamp )
    
    // Insert processed map data
    ezDBC.executePrepared(LcmsDbProcessedMapTable.mkInsertQuery) { statement => 
      statement.executeWith(
        newMapId,
        processedMap.number,
        processedMap.normalizationFactor,
        processedMap.isMaster,
        processedMap.isAlnReference,
        processedMap.isLocked,
        processedMap.mapSetId
      )
    }
    
    // Update processed map id
    processedMap.id = newMapId
    
    // Link the processed map to the corresponding run maps
    this.linkProcessedMapToRunMaps( ezDBC, processedMap )
    
    newMapId
 
  }
  
  protected def _updateProcessedMap( ezDBC: EasyDBC, processedMap: ProcessedMap ): Unit = {
    
    this.logger.info("updating processed map #"+processedMap.id)
    
    // TODO: implement a mkUpdateQuery
    val updateSQLquery = "UPDATE "+ LcmsDbProcessedMapTable.name +
    " SET normalization_factor=?, is_aln_reference=?, is_locked=? WHERE id =" + processedMap.id
    
    // Insert processed map data
    ezDBC.executePrepared(updateSQLquery) { statement => 
      statement.executeWith(
        processedMap.normalizationFactor,
        processedMap.isAlnReference,
        processedMap.isLocked
      )
    }
 
  }
  
  protected def linkProcessedMapToRunMaps( ezDBC: EasyDBC, processedMap: ProcessedMap ): Unit = {
    
    ezDBC.executePrepared(LcmsDbProcessedMapRunMapMappingTable.mkInsertQuery) { statement => 
      processedMap.getRunMapIds.foreach { runMapId =>
        statement.executeWith( processedMap.id, runMapId )
      }
    }
    
  }
  
  protected def insertProcessedMapFeatureItems(ezDBC: EasyDBC, processedMap: ProcessedMap ): Unit = {
    
    val processedMapId = processedMap.id
    require( processedMapId > 0, "the processed map must have been persisted first")
    
    this.logger.info("storing features for processed map #"+processedMapId)
    
    // Create a HashSet which avoids to store the same feature multiple times
    val storedFtIdSet = new collection.mutable.HashSet[Long]
    
    // Attach features to the processed map
    ezDBC.executePrepared(LcmsDbProcessedMapFeatureItemTable.mkInsertQuery) { statement => 
      processedMap.features.foreach { feature =>
        if( storedFtIdSet.contains(feature.id) == false ) {
          
          // Update feature map id
          feature.relations.processedMapId = processedMapId
          
          if( feature.isCluster ) {
            
            // Store cluster sub-features which have not been already stored
            for( subFt <- feature.subFeatures if storedFtIdSet.contains(subFt.id) == false ) {
              // Update sub-feature map id
              subFt.relations.processedMapId = processedMapId
              // Store the processed feature
              _insertProcessedMapFtItemUsingWrappedStatement( subFt, statement )
              // Memorize this feature has been stored
              storedFtIdSet += subFt.id
            }
          }
          else {
            // Store the processed feature
            _insertProcessedMapFtItemUsingWrappedStatement( feature, statement )
          }
          
          // Memorize this feature has been stored
          storedFtIdSet += feature.id
        }
      }
    }
    
  }
  
  def storeFeatureClusters( features: Seq[Feature] ): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
    
      // Insert features
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { featureInsertStmt =>
      
        // Store feature clusters 
        features.withFilter( _.isCluster ).foreach { clusterFt =>
            
          // Store the feature cluster
          val newFtId = this.insertFeatureUsingPreparedStatement( clusterFt, featureInsertStmt )
          
          // Update feature cluster id
          clusterFt.id = newFtId
     
        }
      }
      
      // Store processed feature items corresponding to feature clusters
      ezDBC.executePrepared(LcmsDbProcessedMapFeatureItemTable.mkInsertQuery) { statement => 
        features.withFilter( _.isCluster ).foreach { ft =>
          _insertProcessedMapFtItemUsingWrappedStatement( ft, statement )
        }
      }
      
      // Link feature clusters to their corresponding sub-features
      //val subFtIds = new ArrayBuffer[Int](nbSubFts)
      ezDBC.executePrepared(LcmsDbFeatureClusterItemTable.mkInsertQuery) { statement => 
        features.withFilter( _.isCluster ).foreach { clusterFt =>
          for( subFt <- clusterFt.subFeatures ) {
            //subFtIds += subFt.id
            statement.executeWith( clusterFt.id, subFt.id, clusterFt.relations.processedMapId )
          }
        }
      }
      
      // Set all sub-features of the processed map as clusterized
      /*subFtIds.grouped(lcmsDb.maxVariableNumber).foreach { tmpSubFtIds => {
        lcmsDb.execute( "UPDATE processed_map_feature_item SET is_clusterized = " + BoolToSQLStr(true,lcmsDb.boolStrAsInt) +
                          " WHERE feature_id IN (" + tmpSubFtIds.mkString(",") +")" )
        }
      }*/
    
    })
    
  }
  
  private def _insertProcessedMapFtItemUsingWrappedStatement( ft: Feature, statement: StatementWrapper ): Unit = {
    
    require( ft.id > 0, "features must be persisted first")
    require( ft.relations.processedMapId > 0, "features must belong to a persisted processed map")
    
    // TODO: store properties
    
    statement.executeWith(
      ft.relations.processedMapId,
      ft.id,
      ft.getCalibratedMozOrMoz,
      ft.getNormalizedIntensityOrIntensity,
      ft.getCorrectedElutionTimeOrElutionTime,
      ft.isClusterized,
      ft.selectionLevel,
      Option.empty[String]
    )
                           
  }
  
}