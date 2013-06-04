package fr.proline.core.algo.msq.spectralcount

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.om.model.msi.ResultSet
import scala.collection.mutable.HashMap
import fr.proline.core.om.model.msi.PeptideMatch
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.filtering.ResultSummaryFilterBuilder
import fr.proline.repository.util.JDBCWork
import java.sql.Connection
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter


trait IPepInstanceSpectralCountUpdater {
  
  def updatePepInstanceSC( rsm: ResultSummary, execContext: IExecutionContext ): Unit
  
}

object PepInstanceFilteringLeafSCUpdater extends IPepInstanceSpectralCountUpdater with Logging {

  private def getIsLeave(rsm: ResultSummary, execContext: IExecutionContext): Boolean = {
    var rsType: String = null
    val jdbcWork = new JDBCWork() {
      override def execute(con: Connection) {
        val getPMQuery = "SELECT type from result_set WHERE id = ?"
        val pStmt = con.prepareStatement(getPMQuery)
        pStmt.setInt(1, rsm.getResultSetId)
        val sqlResultSet = pStmt.executeQuery()
        if (sqlResultSet.next)
          rsType = sqlResultSet.getString(1)
        pStmt.close()
      }

    } // End of jdbcWork anonymous inner class    	  
    execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)
    if ((rsType matches "SEARCH") || (rsType matches "DECOY_SEARCH"))
      return true
    else
      return false
  }
  
  
  /***
   * Update Peptide Instances basic SC using filtering information.
   * In case of leave RSM, basic SC = PSM count
   * In case of RSM (RSM.X) validated from a merged RS the following computation will be done : 
   *  - Get all leaves RS from RSM.Rs
   *  - For each leave RS apply RSM.X PSM filters and create a Map : Peptide ID-> Basic Spectral Count
   *  - Update RSM.X peptide instance SC using the sum of leave RS BSC. 
   */
  def updatePepInstanceSC( rsm: ResultSummary, execContext: IExecutionContext ): Unit = {    
  
    var spectralCountByPepId = new HashMap[Int, Int]()
	val startTime =System.currentTimeMillis()

	val validPeptideMatchesB  = Seq.newBuilder[PeptideMatch]
	rsm.getAllPeptideMatchesByPeptideSetId.values.foreach(a => {validPeptideMatchesB ++= a})
	val validPeptideMatches : Seq[PeptideMatch] = validPeptideMatchesB.result

	val isNativeRS = getIsLeave(rsm, execContext)
	
	if(isNativeRS){
	  
	   //VDS comment :Could get info from PeptideInstance.peptideMatches.filter on isValidated... but should be sure
	  // PeptideInstance.peptideMatches != null ...
	  
	   // Peptide Instance SC = PepMatch Count
	   for (psm <- validPeptideMatches) {
		   var pepSC = spectralCountByPepId.getOrElse(psm.peptideId, 0)
		   pepSC += 1		
		   spectralCountByPepId.put(psm.peptideId, pepSC)
      }
		
	} else { 
		//Get leaves RS
		val leavesRSs = getLeavesRS(rsm, execContext)
		val appliedPSMFilters = ResultSummaryFilterBuilder.buildPeptideMatchFilters(rsm)
		
		// Get PepID->SC for each RS
		leavesRSs.foreach(rs =>{
			val rsPepInstSC : HashMap[Int, Int] = countValidPSMInResultSet(rs,appliedPSMFilters)
			// Merge result in global result 
			rsPepInstSC.foreach(entry => {
			  var pepSC = spectralCountByPepId.getOrElse(entry._1, 0)
			  pepSC += entry._2		
			  spectralCountByPepId.put(entry._1, pepSC)
			})			
		})			 
	}
	
    val endTime =System.currentTimeMillis()
    logger.debug(" Needed Time to calculate "+validPeptideMatches.length+" = "+(endTime - startTime)+" ms")
    
    val tmpPepInstByPepId = Map() ++ rsm.peptideInstances.map { pepInst => ( pepInst.peptideId -> pepInst ) }     
    spectralCountByPepId.foreach(pepSc => { 
    	if(tmpPepInstByPepId.get(pepSc._1).isDefined){
    	  tmpPepInstByPepId.get(pepSc._1).get.totalLeavesMatchCount = pepSc._2
    	}else{
    		throw new Exception("PeptideInstance associated to validated PeptideMatch not found for peptide id=" + pepSc._1 )
    	}
    })
  }

  //TODO VDS !!! 
  private def countValidPSMInResultSet(rs: ResultSet, appliedPSMFilters: Array[IPeptideMatchFilter]): HashMap[Int, Int] = {
    new HashMap[Int, Int]()
  }
  
  //TODO VDS !!! 
  private def getLeavesRS(rsm: ResultSummary, execContext: IExecutionContext): Seq[ResultSet] = {

    //     val jdbcWork = new JDBCWork() {
    //
    //    	  override def execute(con: Connection) {
    //    		  val stmt = con.createStatement()
    //
    //			  stmt.execute...
    //
    //			  stmt.close()
    //    	  }
    //
    //      } // End of jdbcWork anonymous inner class
    //      execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)

    Seq.empty[ResultSet]
  }

}