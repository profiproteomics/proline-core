package fr.proline.core.service.lcms

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.impl._
import fr.proline.core.om.storer.lcms.MasterMapStorer
import fr.proline.core.service.lcms._
import fr.proline.repository.IDatabaseConnector

object CreateMasterMap {

  def apply(
    lcmsDbCtx: DatabaseConnectionContext,
    mapSet: MapSet,
    masterFtFilter: fr.proline.core.algo.lcms.filtering.Filter,
    ftMappingParams: FeatureMappingParams,
    normalizationMethod: Option[String]
  ): ProcessedMap = {

    val masterMapCreator = new CreateMasterMap(lcmsDbCtx, mapSet, masterFtFilter, ftMappingParams, normalizationMethod)
    masterMapCreator.runService()
    masterMapCreator.createdMasterMap

  }

}

class CreateMasterMap(
  val lcmsDbCtx: DatabaseConnectionContext,
  mapSet: MapSet,
  masterFtFilter: fr.proline.core.algo.lcms.filtering.Filter,
  ftMappingParams: FeatureMappingParams,
  normalizationMethod: Option[String]
) extends ILcMsService {

  var createdMasterMap: ProcessedMap = null

  def runService(): Boolean = {

    // Retrieve reference map id and check if alignment has been performed
    val alnRefMapId = mapSet.alnReferenceMapId
    if (alnRefMapId == 0) {
      throw new Exception("the alignment of LCMS maps must be performed first")
    }

    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDbCtx.isInTransaction()
    if (!wasInTransaction) lcmsDbCtx.beginTransaction()

    // Delete current master map if it exists
    if (mapSet.masterMap != null) {
      
      DoJDBCWork.withEzDBC( lcmsDbCtx, { ezDBC =>
        
        val existingMasterMapId = mapSet.masterMap.id
  
        // Delete links between master map features and child features
        logger.info("delete links between master map features and child features...")
        ezDBC.execute("DELETE FROM master_feature_item WHERE master_map_id = " + existingMasterMapId)
  
        // Delete master map features
        logger.info("delete master map features...")
        ezDBC.execute("DELETE FROM feature WHERE map_id = " + existingMasterMapId)
  
        /*
        // Make several requests
        masterMap.features.map( _.id ).grouped(maxNbIters).foreach( tmpMftIds => {
          lcmsDbTx.execute("DELETE FROM feature WHERE id IN (" + tmpMftIds.mkString(",") +")")
        })*/
  
        // Delete existing processed map feature items
        logger.info("delete processed map feature item for master map...")
        ezDBC.execute("DELETE FROM processed_map_feature_item WHERE processed_map_id = " + existingMasterMapId)
  
        // Delete existing master map
        logger.info("delete existing master map...")
        ezDBC.execute("DELETE FROM processed_map WHERE id = " + existingMasterMapId)
        ezDBC.execute("DELETE FROM map WHERE id = " + existingMasterMapId)
  
        // Update map set
        this.mapSet.masterMap = null
        
      })

    }

    // Build the master map
    logger.info("building master map...")
    val newMasterMap = MasterMapBuilder.buildMasterMap(mapSet, masterFtFilter, ftMappingParams)

    // Update map set
    mapSet.masterMap = newMasterMap
    
    if (normalizationMethod != None && mapSet.childMaps.length > 1) {

      // Instantiate a Cmd for map set normalization
      logger.info("normalizing maps...")
      
      // TODO: create a service which updates the normalized intensities
      MapSetNormalizer(normalizationMethod.get).normalizeFeaturesIntensity(mapSet)

      // Update master map feature intensity
      logger.info("updating master map feature data...")
      mapSet.masterMap = newMasterMap.copy(features = MasterMapBuilder.rebuildMftsUsingBestChild(newMasterMap.features))

    }

    logger.info("saving master map...")
    val masterMapStorer = MasterMapStorer(lcmsDbCtx)
    masterMapStorer.storeMasterMap(mapSet.masterMap)
    
    // Commit transaction if it was initiated locally
    if (!wasInTransaction) lcmsDbCtx.commitTransaction()

    true
  }

}