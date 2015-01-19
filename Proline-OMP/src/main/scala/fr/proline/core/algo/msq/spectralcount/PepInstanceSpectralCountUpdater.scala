package fr.proline.core.algo.msq.spectralcount

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.om.model.msi.ResultSet
import scala.collection.mutable.HashMap
import fr.proline.core.om.model.msi.PeptideMatch
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.algo.msi.filtering.ResultSummaryFilterBuilder
import fr.proline.repository.util.JDBCWork
import java.sql.Connection
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import scala.collection.JavaConversions.collectionAsScalaIterable
import java.util.HashSet
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.model.msi.PeptideInstance
//import scala.collection.JavaConverters.asJavaCollectionConverter

trait IPepInstanceSpectralCountUpdater {
  /**
   * *
   * Update Peptide Instances basic SC using filtering information.
   * In case of leave RSM, basic SC = PSM count
   * In case of RSM (RSM.X) validated from a merged RS the following computation will be done :
   *  - Get all leaves RS from RSM.Rs
   *  - For each leave RS apply RSM.X PSM filters and create a Map : Peptide ID-> Basic Spectral Count
   *  - Update RSM.X peptide instance SC using the sum of leave RS BSC.
   * In case of RSM (RSM.Y) issued from a mergeRSM service, the following computation will be done :
   *  - Get the leave RSM and compute SC (  basic SC = valid PSM count)
   *  - update RSM.Y peptide instance SC using the sum of leave RSM peptide instance SC
   *  
   *  @param rsm ResultSummary to calculate SC for
   *  @param execContext ExecutionContext to use for getting
   *  
   *  NO persistence is DONE !!
   */
  def updatePepInstanceSC(rsm: ResultSummary, execContext: IExecutionContext): Unit

}

object PepInstanceFilteringLeafSCUpdater extends IPepInstanceSpectralCountUpdater with Logging {

  private def getIsLeaveRSM(rsmID: Long, execContext: IExecutionContext): Boolean = {
    var rsType: String = null
    val rsId = getRSIdForRsmID(rsmID, execContext)

    val jdbcWork = new JDBCWork() {
      override def execute(con: Connection) {
        
        val pStmt = con.prepareStatement("SELECT type from result_set WHERE id = ?")
        pStmt.setLong(1, rsId)
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

  private def getRSIdForRsmID(rsmID: Long, execContext: IExecutionContext): Long = {
    var rsID: Long = -1

    val jdbcWork = new JDBCWork() {

      override def execute(con: Connection) {
        val pStmt = con.prepareStatement("SELECT result_set_id from result_summary WHERE id = ?")
        pStmt.setLong(1, rsmID)
        val sqlResultSet = pStmt.executeQuery()
        if (sqlResultSet.next) {
          rsID = sqlResultSet.getLong(1)
          pStmt.close()
        }
      }
    } // End of jdbcWork anonymous inner class    	 

    execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)
    if (rsID > 0)
      return rsID
    else
      throw new IllegalArgumentException("Unable to get Result Set ID for ResultSummary ID " + rsmID)
  }

  /**
   * *
   * Update Peptide Instances basic SC using filtering information.
   * In case of leave RSM, basic SC = PSM count
   * In case of RSM (RSM.X) validated from a merged RS the following computation will be done :
   *  - Get all leaves RS from RSM.Rs
   *  - For each leave RS apply RSM.X PSM filters and create a Map : Peptide ID-> Basic Spectral Count
   *  - Update RSM.X peptide instance SC using the sum of leave RS BSC.
   * In case of RSM (RSM.Y) issued from a mergeRSM service, the following computation will be done :
   *  - Get the leave RSM and compute SC (  basic SC = valid PSM count)
   *  - update RSM.Y peptide instance SC using the sum of leave RSM peptide instance SC
   *  
   *  
   */
  def updatePepInstanceSC(rsm: ResultSummary, execContext: IExecutionContext): Unit = {
  
    val startTime = System.currentTimeMillis()
	
    val spectralCountByPepId :HashMap[Long, Int] = getRSMSpectralCountByPepId(rsm, rsm.id, rsm.peptideInstances.map(_.peptideId),  execContext)

    val endTime = System.currentTimeMillis()
    logger.debug(" Needed Time to calculate SC (RSM) "+rsm.id+" for " +spectralCountByPepId.size+" =  " + (endTime - startTime) + " ms")

    val tmpPepInstByPepIdBuilder = Map.newBuilder[Long,PeptideInstance]
    rsm.peptideInstances.foreach(pepInst => { 
    	tmpPepInstByPepIdBuilder += pepInst.peptideId -> pepInst 
      })
      
  	val tmpPepInstByPepId = tmpPepInstByPepIdBuilder.result
    spectralCountByPepId.foreach(pepSc => {
      if (tmpPepInstByPepId.get(pepSc._1).isDefined) {
        tmpPepInstByPepId.get(pepSc._1).get.totalLeavesMatchCount = pepSc._2
      } else {
        throw new Exception("PeptideInstance associated to validated PeptideMatch not found for peptide id=" + pepSc._1)
      }
    })
    
  }

  private def getRSMSpectralCountByPepId(rootRSM: ResultSummary, rsmID: Long, pepID: Seq[Long], execContext: IExecutionContext): HashMap[Long, Int] = {
    val scByPepID = new HashMap[Long, Int]()

    //***** RSM is leave : COUNT Valid PSM
    if (getIsLeaveRSM(rsmID, execContext)) {
      logger.trace(" RSM "+rsmID+" is leave RSM ")
      var rsm: ResultSummary = null
      if (rootRSM.id == rsmID)
       rsm = rootRSM
      else {
        logger.trace("Need to read RSM "+rsmID)
        val rsmOp = getResultSummaryProvider(execContext).getResultSummary(rsmID, true)
        if (rsmOp.isEmpty)
          throw new IllegalArgumentException("Unable to read resultaSummay with specified ID " + rsmID)
        rsm = rsmOp.get
      }
      
      logger.trace(" Calculate SC for RSM "+rsmID)
      
      //VDS comment :Could get info from PeptideInstance.peptideMatches.filter on isValidated... but should be sure
      // PeptideInstance.peptideMatches != null ...
      val validePepMatchesSet = rsm.getAllPeptideMatchesByPeptideSetId.flatMap(_._2).toSet

      // Peptide Instance SC = PepMatch Count
      for (psm <- validePepMatchesSet) {
        if (pepID.contains(psm.peptideId)) {
          var pepSC = scByPepID.getOrElse(psm.peptideId, 0)
          pepSC += 1
          scByPepID.put(psm.peptideId, pepSC)
        }
      }
      return scByPepID

    } else {
      
      val childIDsOpt = getRSMChildsID(rsmID, execContext)

      // ***** RSM is result of a Merge of ResultSet : count Leave RS "valid" PSM 
      if (childIDsOpt.isEmpty) {
	  	logger.trace(" RSM "+rsmID+" is in RS hierarchy. Get leave RS  ")
        // Use RS hierarchy : If next level is merge RS, suppose all hierarchy is a RS merge          
        //	Get leaves RS
        val leavesRSs = getLeavesRS(rsmID, execContext)

        //Get RSM root of RS Merge : May not be the "final" Root RSM !!!   if RSMs have then be merged ...
        var rsm: ResultSummary = null
        if (rootRSM.id == rsmID)
         rsm = rootRSM
        else {
            logger.trace("Need to read RSM "+rsmID)
          val rsmOp = getResultSummaryProvider(execContext).getResultSummary(rsmID, true)
          if (rsmOp.isEmpty)
            throw new IllegalArgumentException("Unable to read resultaSummay with specified ID " + rsmID)
          rsm = rsmOp.get
        }

        //Get filters used for RSM root of RS Merge
        val appliedPSMFilters = ResultSummaryFilterBuilder.buildPeptideMatchFilters(rsm)

        logger.trace(" Calculate SC for leave RS ")
        // Get PepID->SC for each RS
        leavesRSs.foreach(rs => {
          val rsPepInstSC: HashMap[Long, Int] = countValidPSMInResultSet(rs, appliedPSMFilters)
          // Merge result in global result 
          rsPepInstSC.foreach(entry => {
            if (pepID.contains(entry._1)) {
              var pepSC = scByPepID.getOrElse(entry._1, 0)
              pepSC += entry._2
              scByPepID.put(entry._1, pepSC)
            }
          })
        })

        // ***** RSM is result of a Merge of ResultSummary : continue to go into hierarchy : child RSM could be RS merge result  
      } else {
        logger.trace(" RSM "+rsmID+" is in RSM hierarchy. Go through RSM")
        //Use RSMs hierarchy.  rsm peptide Instance SC = sum child peptide Instance SC
        childIDsOpt.get.foreach(childID => {
          val childSCByPepID = getRSMSpectralCountByPepId(rootRSM, childID, pepID, execContext)
          childSCByPepID.foreach(childSCEntry => {
            var pepSC = scByPepID.getOrElse(childSCEntry._1, 0)
            pepSC += childSCEntry._2
            scByPepID.put(childSCEntry._1, pepSC)
          })
        })

      }
      return scByPepID
    }

  }

  //A Optimiser: Prendre en entre la Map globale et l'incrementer...  
  private def countValidPSMInResultSet(rs: ResultSet, appliedPSMFilters: Array[IPeptideMatchFilter]): HashMap[Long, Int] = {
    appliedPSMFilters.foreach(psmFilter => {
      var allPSMs = rs.peptideMatches.toSeq
      if (rs.decoyResultSet.isDefined) {
        allPSMs ++= rs.decoyResultSet.get.peptideMatches
      }
      psmFilter.filterPeptideMatches(allPSMs, true, false)
    })

    val resultMap = new HashMap[Long, Int]()
    rs.peptideMatches.filter(_.isValidated).foreach(psm => {
      var psmCount: Int = 0
      if (resultMap.contains(psm.peptideId)) {
        psmCount = resultMap.get(psm.peptideId).get
      }
      psmCount += 1
      resultMap.put(psm.peptideId, psmCount)
    })

    resultMap
  }

  private def getResultSummaryProvider(execContext: IExecutionContext): IResultSummaryProvider = {

    new SQLResultSummaryProvider(msiDbCtx = execContext.getMSIDbConnectionContext,
      psDbCtx = execContext.getPSDbConnectionContext,
      udsDbCtx = execContext.getUDSDbConnectionContext)

  }

  private def getRSMChildsID(rsmID: Long, execContext: IExecutionContext): Option[Seq[Long]] = {

    var childRSMIdsOp: Option[Seq[Long]] = null
    val jdbcWork = new JDBCWork() {

      override def execute(con: Connection) {
        var childRSMIds = Seq.newBuilder[Long]
        val stmt = con.prepareStatement("select child_result_summary_id from result_summary_relation where result_summary_relation.parent_result_summary_id = ?")
        stmt.setLong(1, rsmID)
        val sqlResultSet = stmt.executeQuery()
        var childDefined = false
        while (sqlResultSet.next) {
          childDefined = true
          childRSMIds += sqlResultSet.getLong(1)
        }
        if (!childDefined)
          childRSMIdsOp = None
        else
          childRSMIdsOp = Some(childRSMIds.result)
        stmt.close()
      } // End of jdbcWork anonymous inner class
    }

    execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)

    return childRSMIdsOp
  }

  private def getLeavesRS(rsmID: Long, execContext: IExecutionContext): Seq[ResultSet] = {
    val providerContext = ProviderDecoratedExecutionContext(execContext) // Use Object factory

    val rsId = getRSIdForRsmID(rsmID, execContext)
    var leavesRsIds: Seq[Long] = getLeafChildsID(rsId, providerContext)
    var leavesRsBuilder = Seq.newBuilder[ResultSet]

    val provider: IResultSetProvider = providerContext.getProvider(classOf[IResultSetProvider])

    leavesRsIds.foreach(rsID => {
      val resultRS = provider.getResultSet(rsID)
      if (resultRS.isDefined) {
        leavesRsBuilder += resultRS.get
      } else {
        val msg = " !!! Unable to get leave search result with id " + rsID
        logger.warn(msg)
        throw new Exception(msg)
      }
    })

    leavesRsBuilder.result

  }

  private def getLeafChildsID(rsId: Long, execContext: IExecutionContext): Seq[Long] = {
    var allRSIds = Seq.newBuilder[Long]

    val jdbcWork = new JDBCWork() {

      override def execute(con: Connection) {

        val stmt = con.prepareStatement("select child_result_set_id from result_set_relation where result_set_relation.parent_result_set_id = ?")
        stmt.setLong(1, rsId)
        val sqlResultSet = stmt.executeQuery()
        var childDefined = false
        while (sqlResultSet.next) {
          childDefined = true
          val nextChildId = sqlResultSet.getInt(1)
          allRSIds ++= getLeafChildsID(nextChildId, execContext)
        }
        if (!childDefined)
          allRSIds += rsId
        stmt.close()
      } // End of jdbcWork anonymous inner class
    }
    execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)

    allRSIds.result
  }
}