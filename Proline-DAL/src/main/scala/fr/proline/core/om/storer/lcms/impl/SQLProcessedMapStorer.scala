package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.jdbc.StatementWrapper

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.{DoJDBCWork,DoJDBCReturningWork}
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureClusterItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapFeatureItemTable
import fr.proline.core.dal.tables.lcms.LcmsDbProcessedMapRawMapMappingTable
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.storer.lcms._

class SQLProcessedMapStorer(
  override val lcmsDbCtx: LcMsDbConnectionContext,
  override val featureWriter: SQLFeatureWriter
) extends SQLRawMapStorer(lcmsDbCtx, featureWriter) with IProcessedMapStorer with LazyLogging {
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = true ): Unit = {    
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      if( processedMap.id < 0 ) {
        // Insert the processed map if not already done
        this._insertProcessedMap( ezDBC, processedMap )
      } else {
        // Else update mutable fields
        this._updateProcessedMap( ezDBC, processedMap )
      }
      
      // Insert processed map feature items
      featureWriter.insertProcessedMapFeatureItems( processedMap )
      
    }
    
    // Store clusters
    this.storeFeatureClusters( processedMap.features )
  
  }
  
  def insertProcessedMap( processedMap: ProcessedMap ): Long = {
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      this._insertProcessedMap(ezDBC,processedMap)
    }
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
    
    ezDBC.executeInBatch(LcmsDbProcessedMapRawMapMappingTable.mkInsertQuery) { statement => 
      processedMap.getRawMapIds.foreach { rawMapId =>
        statement.executeWith( processedMap.id, rawMapId )
      }
    }
    
  }
  
  def storeFeatureClusters( features: Seq[Feature] ): Unit = {
    this.featureWriter.insertFeatureClusters(features)
  }
}