package fr.proline.core.dal.helper

import fr.profi.jdbc.SQLQueryExecution
import fr.proline.util.primitives._

class PdiDbHelper( sqlExec: SQLQueryExecution ) {
  
  import scala.collection.mutable.ArrayBuffer
  
  def getBioSequenceNameByTaxonAndId( bioSeqIds: Seq[Long]): Map[Pair[Long,Long],String] = {
    
    val proteinNameByTaxonAndId = new scala.collection.mutable.HashMap[Pair[Long,Long],String]
    
    bioSeqIds.grouped(sqlExec.getInExpressionCountLimit).foreach { tmpBioSeqIds =>
      
      val sqlQuery = "SELECT taxon_id, bio_sequence_id, name FROM seq_db_entry " +
                     "WHERE bio_sequence_id IN ("+ tmpBioSeqIds.mkString(",") + ") ORDER BY is_active DESC"
                     
      sqlExec.selectAndProcess( sqlQuery ) { r =>
        
        val taxonAndSeqId = new Pair(toLong(r.nextAny), toLong(r.nextAny) )
        val bioSeqName = r.nextString
        
        if( ! proteinNameByTaxonAndId.contains( taxonAndSeqId ) )
          proteinNameByTaxonAndId += ( taxonAndSeqId -> bioSeqName )        
        
      }
    }
    
    Map() ++ proteinNameByTaxonAndId

  }  
  
}