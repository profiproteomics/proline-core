package fr.proline.core.service.lcms

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.lcms._
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.impl._
import fr.proline.core.om.storer.lcms.MasterMapStorer
import fr.proline.core.service.lcms._
import fr.proline.repository.IDatabaseConnector

object CreateMasterMap {

  def apply( lcmsDb: IDatabaseConnector, mapSet: MapSet,
             masterFtFilter: fr.proline.core.algo.lcms.filtering.Filter,
             ftMappingParams: FeatureMappingParams,
             normalizationMethod: Option[String] ): ProcessedMap = {
    
    val masterMapCreator = new CreateMasterMap( lcmsDb, mapSet, masterFtFilter, ftMappingParams, normalizationMethod )
    masterMapCreator.runService()
    masterMapCreator.createdMasterMap
    
  }
  
}

class CreateMasterMap( val lcmsDbConnector: IDatabaseConnector, mapSet: MapSet,
                       masterFtFilter: fr.proline.core.algo.lcms.filtering.Filter,
                       ftMappingParams: FeatureMappingParams,
                       normalizationMethod: Option[String] ) extends MXLcmsService {
  
  var createdMasterMap: ProcessedMap = null
  
  def runService(): Boolean = {
    
    // Retrieve reference map id and check if alignment has been performed
    val alnRefMapId = mapSet.alnReferenceMapId
    if( alnRefMapId == 0 ) {
      throw new Exception( "the alignment of LCMS maps must be performed first" )
    }
    
    // Check if a transaction is already initiated
    val wasInTransaction = ezDBC.isInTransaction()
    if( !wasInTransaction ) ezDBC.beginTransaction()
    
    // Delete current master map if it exists
    if( mapSet.masterMap != null ) {
      val existingMasterMapId = mapSet.masterMap.id
         
      // Delete links between master map features and child features
      println( "delete links between master map features and child features..." )        
      ezDBC.execute( "DELETE FROM master_feature_item WHERE master_map_id = " + existingMasterMapId )
      
      // Delete master map features
      println( "delete master map features..." )      
      ezDBC.execute( "DELETE FROM feature WHERE map_id = " + existingMasterMapId )
      
      /*
      // Make several requests
      masterMap.features.map( _.id ).grouped(maxNbIters).foreach( tmpMftIds => {
        lcmsDbTx.execute("DELETE FROM feature WHERE id IN (" + tmpMftIds.mkString(",") +")")
      })*/

      // Delete existing processed map feature items
      println( "delete processed map feature item for master map..." )
      ezDBC.execute( "DELETE FROM processed_map_feature_item WHERE processed_map_id = " + existingMasterMapId )
      
      // Delete existing master map
      println( "delete existing master map..." )
      ezDBC.execute( "DELETE FROM processed_map WHERE id = " + existingMasterMapId )
      ezDBC.execute( "DELETE FROM map WHERE id = " + existingMasterMapId )
      
      // Update map set
      this.mapSet.masterMap = null
      
    }
    
    // Build the master map
    println( "building master map..." )
    val newMasterMap = MasterMapBuilder.buildMasterMap(mapSet, masterFtFilter, ftMappingParams )
    
    // Update map set
    mapSet.masterMap = newMasterMap
    
    if( normalizationMethod != None && mapSet.childMaps.length > 1 ) {
      
      // Instantiate a Cmd for map set normalization
      println( "normalizing maps..." )
      MapSetNormalizer( normalizationMethod.get ).normalizeFeaturesIntensity( mapSet )
      
      // Update master map feature intensity
      println( "updating master map feature data..." )      
      mapSet.masterMap = newMasterMap.copy( features = MasterMapBuilder.rebuildMftsUsingBestChild(newMasterMap.features) )
      
    }
    
    println( "saving master map..." )
    val masterMapStorer = MasterMapStorer( lcmsQueryHelper )
    masterMapStorer.storeMasterMap( mapSet.masterMap )
    
    true
  }

}