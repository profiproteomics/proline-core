package fr.proline.core.om.storer.lcms.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.tables.lcms.{LcmsDbProcessedMapMozCalibrationTable, LcmsDbProcessedMapRawMapMappingTable, LcmsDbProcessedMapTable}
import fr.proline.core.dal.{DoJDBCReturningWork, DoJDBCWork}
import fr.proline.core.om.model.lcms.{Feature, ProcessedMap}
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
    if(processedMap.mozCalibrations.isDefined)
      _insertProcessedMapMozCalib(ezDBC, processedMap)

    newMapId
  }

  // Insert processed map mozCalibration data
  private def _insertProcessedMapMozCalib( ezDBC: EasyDBC, processedMap: ProcessedMap): Unit ={
    if(processedMap.mozCalibrations.isEmpty)
      return

    ezDBC.executeInBatch(LcmsDbProcessedMapMozCalibrationTable.mkInsertQuery) { statement =>
      processedMap.mozCalibrations.get.foreach { mozCal =>
        if (mozCal.scanId < 0 || processedMap.id < 0 ){//Still no scan Id !
          this.logger.warn("!!!! Unable to get a Scan Id / Processed Map Id for processedMap "+processedMap.name+". No MoZCalibrationMap was saved !!!! ")
        } else {
          statement.executeWith(
            processedMap.id,
            mozCal.scanId,
            mozCal.mozList.mkString(" "),
            mozCal.deltaMozList.mkString(" "),
            mozCal.properties.map(ProfiJson.serialize(_))
          )
        }
      }
    }
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