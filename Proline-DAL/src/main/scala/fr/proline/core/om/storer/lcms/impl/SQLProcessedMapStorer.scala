package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._
import fr.profi.jdbc.StatementWrapper

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureClusterItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapFeatureItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapRunMapMappingTable
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.storer.lcms.IProcessedMapStorer

class SQLProcessedMapStorer(lcmsDbCtx: DatabaseConnectionContext) extends SQLRunMapStorer(lcmsDbCtx) with IProcessedMapStorer {
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = true ): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      // Insert processed map
      val newProcessedMapId = this.insertProcessedMap( ezDBC, processedMap )
      
      // Update processed map id
      processedMap.id = newProcessedMapId
      
      // Link the processed map to the corresponding run maps
      this.linkProcessedMapToRunMaps( ezDBC, processedMap )
      
      // Insert processed map feature items
      this.insertProcessedMapFeatureItems( ezDBC, processedMap )
      
    })
    
    // Store clusters
    this.storeFeatureClusters( processedMap.features )
  
  }
  
  protected def insertProcessedMap( ezDBC: EasyDBC, processedMap: ProcessedMap ): Long = {
    
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
    
    newMapId
  }
  
  protected def linkProcessedMapToRunMaps( ezDBC: EasyDBC, processedMap: ProcessedMap ): Unit = {
    
    ezDBC.executePrepared(LcmsDbProcessedMapRunMapMappingTable.mkInsertQuery) { statement => 
      processedMap.runMapIds.foreach { runMapId =>
        statement.executeWith( processedMap.id, runMapId )
      }
    }
    
  }
  
  protected def insertProcessedMapFeatureItems(ezDBC: EasyDBC, processedMap: ProcessedMap ): Unit = {
    
    val processedMapId = processedMap.id
    
    // Attach features to the processed map
    ezDBC.executePrepared(LcmsDbProcessedMapFeatureItemTable.mkInsertQuery) { statement => 
      processedMap.features.foreach { feature =>
        
        // Update feature map id
        feature.relations.mapId = processedMapId
        
        if( feature.isCluster ) {

          // Store cluster sub-features
          for( subFt <- feature.subFeatures ) {
            // Update sub-feature map id
            subFt.relations.mapId = processedMapId
            // Store the processed feature
            _insertProcessedMapFtItemUsingWrappedStatement( subFt, statement )
          }
        }
        else {
          // Store the processed feature
          _insertProcessedMapFtItemUsingWrappedStatement( feature, statement )
        }
      }
    }
    
  }
  
  def storeFeatureClusters( features: Seq[Feature] ): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
    
      // Insert features
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery, true) { featureInsertStmt =>
      
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
            statement.executeWith( clusterFt.id, subFt.id, clusterFt.relations.mapId )
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
    require( ft.relations.mapId > 0, "features must belong to a persisted map")
    
    // TODO: store properties
    
    statement.executeWith(
      ft.relations.mapId,
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