package fr.proline.core.service.lcms

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.service.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentParams
import fr.proline.core.algo.lcms.LcmsMapAligner
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.storer.lcms.MapAlnSetStorer
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.repository.IDatabaseConnector

object AlignMapSet {

  def apply( lcmsDbConnector: IDatabaseConnector, mapSet: MapSet, 
             alnMethodName: String, alnParams: AlignmentParams ): Unit = {
    
    val mapSetAligner = new AlignMapSet( lcmsDbConnector, mapSet, alnMethodName, alnParams  )
    mapSetAligner.runService()
    ()
    
  }
  
}

class AlignMapSet( val lcmsDbConnector: IDatabaseConnector, mapSet: MapSet, 
                   alnMethodName: String, alnParams: AlignmentParams  ) extends MXLcmsService {

  def runService(): Boolean = {
    
    //val mapSetLoader = new MapSetLoader( lcmsDb )
    //val mapSet = mapSetLoader.getMapSet( mapSetId )
    val mapSetId = mapSet.id
    
    // Check if a transaction is already initiated
    val wasInTransaction = ezDBC.isInTransaction 
    if( !wasInTransaction ) ezDBC.beginTransaction()
    
    // Check if reference map already exists: if so delete alignments
    val existingAlnRefMapId = mapSet.alnReferenceMapId
    if( existingAlnRefMapId > 0 ) {
      
      ezDBC.execute( "DELETE FROM map_alignment WHERE map_set_id = " + mapSetId )
      
      // Update processed reference map
      ezDBC.execute( "UPDATE processed_map SET is_aln_reference = ? WHERE id = ?", false, existingAlnRefMapId )

    }
    
    // Copy maps while removing feature clusters
    // TODO: check if it is better or not
    val childMapsWithoutClusters = mapSet.childMaps.map { _.copyWithoutClusters }
     
    // Perform the map alignment
    val mapAligner = LcmsMapAligner( methodName = alnMethodName )
    val alnResult = mapAligner.computeMapAlignments( childMapsWithoutClusters, alnParams )
    
    val alnStorer = MapAlnSetStorer( lcmsQueryHelper )
    alnStorer.storeMapAlnSets( alnResult.mapAlnSets, mapSetId, alnResult.alnRefMapId )
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) ezDBC.commitTransaction()
    
    // Update the maps the map set alignment sets
    mapSet.alnReferenceMapId = alnResult.alnRefMapId
    mapSet.mapAlnSets = alnResult.mapAlnSets
    
    true
  }
}