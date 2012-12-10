package fr.proline.core.dal.helper

import fr.profi.jdbc.SQLQueryExecution

class PdiDbHelper( sqlExec: SQLQueryExecution ) {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.model.lcms._
  
  def getBioSequenceNameByTaxonAndId( bioSeqIds: Seq[Int]): Map[Pair[Int,Int],String] = {
    
    val proteinNameByTaxonAndId = new scala.collection.mutable.HashMap[Pair[Int,Int],String]
    
    bioSeqIds.grouped(sqlExec.getInExpressionCountLimit).foreach { tmpBioSeqIds =>
      
      val sqlQuery = "SELECT taxon_id, bio_sequence_id, name FROM seq_db_entry " +
                     "WHERE bio_sequence_id IN ("+ tmpBioSeqIds.mkString(",") + ") ORDER BY is_active DESC"
                     
      sqlExec.selectAndProcess( sqlQuery ) { r =>
        
        val taxonAndSeqId = new Pair(r.nextInt, r.nextInt )
        val bioSeqName = r.nextString
        
        if( ! proteinNameByTaxonAndId.contains( taxonAndSeqId ) )
          proteinNameByTaxonAndId += ( taxonAndSeqId -> bioSeqName )        
        
      }
    }
    
    Map() ++ proteinNameByTaxonAndId

  }  
  
}