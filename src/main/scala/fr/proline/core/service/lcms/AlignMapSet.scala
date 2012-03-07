package fr.proline.core.service.lcms

import fr.proline.core.LcmsDb
import fr.proline.core.service.IService
import fr.proline.core.service.lcms._
import fr.proline.core.algo.lcms.alignment.AlignmentParams
import fr.proline.core.algo.lcms.LcmsMapAligner
import fr.proline.core.om.model.lcms.MapSet
//import fr.proline.core.om.provider.sql.lcms.MapSetLoader
import fr.proline.core.utils.sql.BoolToSQLStr
import fr.proline.core.om.storer.lcms.MapAlnSetStorer

object AlignMapSet {

  def apply( lcmsDb: LcmsDb, mapSet: MapSet, 
             alnMethodName: String, alnParams: AlignmentParams ): Unit = {
    
    val mapSetAligner = new AlignMapSet( lcmsDb, mapSet, alnMethodName, alnParams  )
    mapSetAligner.runService()
    ()
    
  }
  
}

class AlignMapSet( lcmsDb: LcmsDb, mapSet: MapSet, 
                   alnMethodName: String, alnParams: AlignmentParams  ) {

  def runService(): Boolean = {
    
    //val mapSetLoader = new MapSetLoader( lcmsDb )
    //val mapSet = mapSetLoader.getMapSet( mapSetId )
    val mapSetId = mapSet.id
    
    // Check if a transaction is already initiated
    val wasInTransaction = lcmsDb.isInTransaction()
    
    // Retrieve database transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()    
    
    // Check if reference map already exists: if so delete alignments
    val existingAlnRefMapId = mapSet.alnReferenceMapId
    if( existingAlnRefMapId > 0 ) {
      
      lcmsDbTx.execute( "DELETE FROM map_alignment WHERE map_set_id = " + mapSetId )
      
      // Update processed reference map
      lcmsDbTx.execute( "UPDATE processed_map SET is_aln_reference = " + 
                        BoolToSQLStr(false,lcmsDb.boolStrAsInt)  + " WHERE id = " + existingAlnRefMapId )

    }
    
    // Copy maps while removing feature clusters
    // TODO: check if it is better or not
    val childMapsWithoutClusters = mapSet.childMaps.map { _.copyWithoutClusters }
     
    // Perform the map alignment
    val mapAligner = LcmsMapAligner( methodName = alnMethodName )
    val alnResult = mapAligner.computeMapAlignments( childMapsWithoutClusters, alnParams )
    
    val alnStorer = MapAlnSetStorer( lcmsDb )
    alnStorer.storeMapAlnSets( alnResult.mapAlnSets, mapSetId, alnResult.alnRefMapId )
    
     // Commit transaction if it was initiated locally
    if( !wasInTransaction ) lcmsDb.commitTransaction()
    
    // Update the maps the map set alignment sets
    mapSet.alnReferenceMapId = alnResult.alnRefMapId
    mapSet.mapAlnSets = alnResult.mapAlnSets
    
    true
  }
}