package fr.proline.core.dal.helper

import scala.collection.mutable.HashMap
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.util.primitives._

class PsDbHelper( sqlExec: SQLQueryExecution ) {
  
  def getUnimodIdByPtmId(): Map[Int,Int] = {
    
    val unimodIdByPtmId = new HashMap[Int,Int]
    
    sqlExec.selectAndProcess( "SELECT id, unimod_id FROM ptm" ) { r =>
      val ptmId: Int = toInt(r.nextAnyVal)
      unimodIdByPtmId += (ptmId -> r.nextInt )       
    }
    
    Map() ++ unimodIdByPtmId
  }  
  
}