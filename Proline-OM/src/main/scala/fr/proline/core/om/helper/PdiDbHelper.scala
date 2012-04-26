package fr.proline.core.om.helper

import fr.proline.core.PdiDb

class PdiDbHelper( pdiDb: PdiDb ) {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._
  
  def getBioSequenceNameByTaxonAndId( bioSeqIds: Seq[Int]): Map[Pair[Int,Int],String] = {
    
    val pdiDbTx = pdiDb.getOrCreateTransaction
    val proteinNameByTaxonAndId = new scala.collection.mutable.HashMap[Pair[Int,Int],String]
    
    bioSeqIds.grouped(pdiDb.maxVariableNumber).foreach { tmpBioSeqIds =>
      
      val sqlQuery = "SELECT taxon_id, bio_sequence_id, name FROM seq_db_entry " +
                     "WHERE bio_sequence_id IN ("+ tmpBioSeqIds.mkString(",") + ") ORDER BY is_active DESC"
                     
      pdiDbTx.selectAndProcess( sqlQuery ) { r =>
        
        val taxonAndSeqId = new Pair(r.nextInt.get, r.nextInt.get )
        val bioSeqName = r.nextString.get
        
        if( ! proteinNameByTaxonAndId.contains( taxonAndSeqId ) )
          proteinNameByTaxonAndId += ( taxonAndSeqId -> bioSeqName )        
        
      }
    }
    
    Map() ++ proteinNameByTaxonAndId

  }  
  
}