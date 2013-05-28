package fr.proline.core.algo.msq.spectralcount

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.om.model.msi.ResultSet
import scala.collection.mutable.HashMap
import fr.proline.core.om.model.msi.PeptideMatch
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.filtering.ResultSummaryFilterBuilder


trait IPepInstanceSpectralCountUpdater {
  
  def updatePepInstanceSC( rsm: ResultSummary, execContext: IExecutionContext ): Unit
  
}

object PepInstanceFilteringLeafSCUpdater extends IPepInstanceSpectralCountUpdater with Logging {

  def updatePepInstanceSC( rsm: ResultSummary, execContext: IExecutionContext ): Unit = {    
  
    var spectralCountByPepId = new HashMap[Int, Int]()
	val startTime =System.currentTimeMillis()
	val appliedPSMFilters = ResultSummaryFilterBuilder.buildPeptideMatchFilters(rsm)

	val validPeptideMatchesB  = Seq.newBuilder[PeptideMatch]
	rsm.getAllPeptideMatchesByPeptideSetId.values.foreach(a => {validPeptideMatchesB ++= a})
	val validPeptideMatches : Seq[PeptideMatch] = validPeptideMatchesB.result

	val isNativeRS = if(rsm.resultSet.isDefined) rsm.resultSet.get.isNative else true 
	//TODO FIXME VDS : Get isNative info from DB !!! 
	
    for (psmFilter <- appliedPSMFilters) {
      for (psm <- validPeptideMatches) {
        var pepSC = spectralCountByPepId.getOrElse(psm.peptideId, 0)
        if (isNativeRS)
          pepSC += 1
        else
          pepSC += countChildSC(psm)
        spectralCountByPepId.put(psm.peptideId, pepSC)
      }
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

  private def countChildSC(psm: PeptideMatch): Int = {

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

    0
  }

}