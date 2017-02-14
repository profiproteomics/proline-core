package fr.proline.core.dal.helper

import scala.collection.mutable.HashMap
import fr.profi.jdbc.SQLQueryExecution
import fr.profi.util.primitives._

class PsDbHelper( sqlExec: SQLQueryExecution ) {
  
  // Unimod Id are Long
  def getUnimodIdByPtmId(): Map[Long,Long] = {
    
    val unimodIdByPtmId = new HashMap[Long,Long]
    
    sqlExec.selectAndProcess( "SELECT id, unimod_id FROM ptm" ) { r =>
      val ptmId = toLong(r.nextAny)
      val unimodId = r.nextAny
      if(unimodId != null)
    	  unimodIdByPtmId += (ptmId -> toLong(unimodId) )       
    }
    
    Map() ++ unimodIdByPtmId
  }  
  
}