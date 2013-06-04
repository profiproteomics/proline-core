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
import scala.collection.JavaConversions.collectionAsScalaIterable
import java.util.HashSet
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
//import scala.collection.JavaConverters.asJavaCollectionConverter

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

  //A Optimiser: Prendre en entre la Map globale et l'incrementer...  
  private def countValidPSMInResultSet(rs: ResultSet, appliedPSMFilters: Array[IPeptideMatchFilter]): HashMap[Int, Int] = {
    appliedPSMFilters.foreach( psmFilter =>{
      var allPSMs = rs.peptideMatches.toSeq 
      if(rs.decoyResultSet.isDefined){
        allPSMs ++= rs.decoyResultSet.get.peptideMatches
      }
      psmFilter.filterPeptideMatches(allPSMs, true, false)
    })
    
    val resultMap  =  new HashMap[Int, Int]()
    rs.peptideMatches.filter(_.isValidated).foreach(psm =>{
        var psmCount = 0
    	if(resultMap.contains(psm.peptideId)){
    	  psmCount = resultMap.get(psm.peptideId).get
    	}
        psmCount += 1
		resultMap.put(psm.peptideId, psmCount)
    })
    
   resultMap
  }


  
  private def getLeavesRS(rsm: ResultSummary, execContext: IExecutionContext): Seq[ResultSet] = {

	  /* Wrap ExecutionContext in ProviderDecoratedExecutionContext for Provider service use */
      val providerContext = if (execContext.isInstanceOf[ProviderDecoratedExecutionContext]) {
        execContext.asInstanceOf[ProviderDecoratedExecutionContext]
      } else {
        new ProviderDecoratedExecutionContext(execContext)
      }
      
      
    var leavesRsIds : Seq[Int] = getLeafChildsID(rsm.getResultSetId, providerContext)
    var leavesRsBuilder  = Seq.newBuilder[ResultSet]
   
    val provider:IResultSetProvider=  providerContext.getProvider(classOf[IResultSetProvider])
    
    leavesRsIds.foreach(rsID =>{
      val resultRS = provider.getResultSet(rsID)
      if(resultRS.isDefined){
        leavesRsBuilder += resultRS.get
      } else{
        logger.warn(" !!! Unable to get leave search result with id "+rsID)
      }        
    })
    
    leavesRsBuilder.result
   
  }

    
  private def getLeafChildsID( rsId: Int, execContext: IExecutionContext) : Seq[Int] = {
    var allRSIds = Seq.newBuilder[Int]
    
	val jdbcWork = new JDBCWork() {
    
    	override def execute(con: Connection) {
    	    
    		val stmt = con.prepareStatement("select child_result_set_id from result_set_relation where result_set_relation.parent_result_set_id = :rsId")
			stmt.setInt(1, rsId)
			val sqlResultSet = stmt.executeQuery()
			var childDefined = false 
			while (sqlResultSet.next){
			  childDefined = true
			  allRSIds += sqlResultSet.getInt(1)
			} 
    		if(!childDefined)
    		  allRSIds += rsId
    		stmt.close()
          } // End of jdbcWork anonymous inner class
    }
    execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)
    
    
    allRSIds.result
  }
}