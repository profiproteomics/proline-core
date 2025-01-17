package fr.proline.core.algo.msq.spectralcount

import java.sql.Connection
import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.algo.msi.filtering.ResultSummaryFilterBuilder
import fr.proline.core.om.model.msi.{IResultSummaryLike, LazyResultSet, LazyResultSummary, PeptideInstance, ResultSummary}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLLazyResultSummaryProvider
import fr.proline.repository.util.JDBCWork

import scala.collection.mutable
import scala.collection.mutable.HashMap

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
  def updatePepInstanceSC(rsm: LazyResultSummary, execContext: IExecutionContext): Unit
  
  def updatePepInstanceSC(rsm: ResultSummary, execContext: IExecutionContext): Unit = {
    this.updatePepInstanceSC(rsm.toLazyResultSummary(linkResultSetEntities = false, linkPeptideSets = false), execContext)
  }

}

class PepInstanceFilteringLeafSCUpdater extends IPepInstanceSpectralCountUpdater with LazyLogging {

  val loadedRSMByID = new mutable.HashMap[Long, LazyResultSummary]()
  val isChildOrUnionMergeByRSMID = new mutable.HashMap[Long, Boolean]()
  val loadedRSByID = new mutable.HashMap[Long, LazyResultSet]()

  /**
    * Verify if specified RSM is a leave RSM or issued from a merge RS AND the merge was done using Union Mode.
    * The verification is done by testing if more that one peptide Match could be specified for
    * peptide Instance ...
    * TODO #17738 workaround. To be update once more information will be stored in properties
    * @return
    */
  def getIsLeaveRSMOrUnionRSMerge(rsmID: Long, execContext: IExecutionContext): Boolean = {

    val start = System.currentTimeMillis()

    if(! isChildOrUnionMergeByRSMID.contains(rsmID)) { //not stored in cache

	    val rsId = getRSIdForRsmID(rsmID, execContext)
      val isChildOrUnionMerge = if (loadedRSByID.contains(rsId)) {
        val fewPepMatchByPepId = loadedRSByID(rsId).peptideMatches.groupBy(_.peptideId).filter(_._2.length>1)
        val isSearchRS = loadedRSByID(rsId).descriptor.isSearchResult

        fewPepMatchByPepId.nonEmpty || isSearchRS

      } else {
        //Don't have access to RS. Get Information fro DB
        var rsType: String = null
        var multiPSM = false
        val jdbcWork = new JDBCWork() {
            override def execute(con: Connection) {

              var pStmt = con.prepareStatement("SELECT type from result_set WHERE id = ?")
              pStmt.setLong(1, rsId)
              var sqlResultSet = pStmt.executeQuery()
              if (sqlResultSet.next)
                rsType = sqlResultSet.getString(1)
              pStmt.close()
              val time1 = System.currentTimeMillis()
              pStmt = con.prepareStatement("SELECT 1 WHERE EXISTS(SELECT countPM FROM (SELECT count(pm.id) as countPM FROM peptide_match pm WHERE pm.result_set_id = ?  GROUP BY pm.peptide_id) as countQuery WHERE countQuery.countPM>1)")
              pStmt.setLong(1, rsId)
              sqlResultSet = pStmt.executeQuery()
              val time2 = System.currentTimeMillis()
              logger.debug(" TEST if Merge UNION ! "+(time2-time1)+" ms")
              multiPSM=sqlResultSet.next
              pStmt.close()
            }

        } // End of jdbcWork anonymous inner class

          execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)
          if(rsType == null)
            throw new IllegalArgumentException("Unable to get Result Set Type for ResultSummary ID " + rsmID)

          multiPSM || ((rsType matches "SEARCH") || (rsType matches "DECOY_SEARCH"))
       }
      isChildOrUnionMergeByRSMID += rsmID -> isChildOrUnionMerge
    }
    val end = System.currentTimeMillis()
    logger.debug(" TEST isChildOrUnionMergeByRSMID : "+(end-start)+" ms")

    isChildOrUnionMergeByRSMID(rsmID)
  }

  def getRSIdForRsmID(rsmID: Long, execContext: IExecutionContext): Long = {
    if(loadedRSMByID.contains(rsmID))
      return loadedRSMByID(rsmID).resultSetId

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

    execContext.getMSIDbConnectionContext.doWork(jdbcWork, false)
    if (rsID > 0)
      rsID
    else
      throw new IllegalArgumentException("Unable to get Result Set ID for ResultSummary ID " + rsmID)
  }

  /**
   * *
   * Update Peptide Instances basic SC using filtering information for specified RSM
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
    def updatePepInstanceSC(rsm: LazyResultSummary, execContext: IExecutionContext): Unit = {
      updatePepInstanceSC(Seq(rsm), execContext)
  	}

  /**
   * *
   * Update Peptide Instances basic SC using filtering information for specified RSMs
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
  def updatePepInstanceSC(rsms: Seq[IResultSummaryLike], execContext: IExecutionContext): Unit = {

    val lazyRsms = rsms.map { rsmLike =>
      val lazyRsm = rsmLike match {
        case lazyRsm: LazyResultSummary => lazyRsm
        case rsm: ResultSummary => rsm.toLazyResultSummary(linkResultSetEntities = false, linkPeptideSets = false)
      }

      loadedRSMByID += (lazyRsm.id -> lazyRsm)
      loadedRSByID += (lazyRsm.resultSetId -> lazyRsm.lazyResultSet)

      lazyRsm
    }

    for( rsm <- lazyRsms) {
	    val startTime = System.currentTimeMillis()

      val pepIdSpectralCounter :HashMap[Long, CounterSC] = getRSMSpectralCounterByPepId(rsm, rsm.id, rsm.peptideInstances.map(_.peptideId).toSet,  execContext)

	    val endTime = System.currentTimeMillis()
	    logger.debug(" Needed Time to calculate SC (RSM) "+rsm.id+" for " +pepIdSpectralCounter.size+" =  " + (endTime - startTime) + " ms")

	    val tmpPepInstByPepIdBuilder = Map.newBuilder[Long,PeptideInstance]
	    rsm.peptideInstances.foreach(pepInst => {
	    	tmpPepInstByPepIdBuilder += pepInst.peptideId -> pepInst
	      })

	  	val tmpPepInstByPepId = tmpPepInstByPepIdBuilder.result
      pepIdSpectralCounter.foreach(sc => {
        val pepInstanceOp = tmpPepInstByPepId.get(sc._1)
        if (pepInstanceOp.isDefined) {
          pepInstanceOp.get.totalLeavesMatchCount = sc._2.spectralCountVal
        } else {
          throw new Exception("PeptideInstance associated to validated PeptideMatch not found for peptide id=" + sc._1)
        }
      })

    }

    //Clear Cache Map
    loadedRSByID.clear
    loadedRSMByID.clear
    isChildOrUnionMergeByRSMID.clear

  }

  def getRSMSpectralCounterByPepId(rootRSM: IResultSummaryLike, rsmID: Long, pepIDs: Set[Long], execContext: IExecutionContext): mutable.HashMap[Long, CounterSC] = {
    val scByPepID = new HashMap[Long, CounterSC]()
    val rsmProvider = new SQLLazyResultSummaryProvider(PeptideCacheExecutionContext(execContext))

    //***** RSM is leave : COUNT Valid PSM
    if (getIsLeaveRSMOrUnionRSMerge(rsmID, execContext)) {
      logger.trace(" RSM "+rsmID+" is leave RSM - Or validation of union RS merge")

      val rsm = if (loadedRSMByID.contains(rsmID))
        loadedRSMByID(rsmID)
      else {
        logger.trace("Need to read RSM "+rsmID)
        val rsmOpt = rsmProvider.getLazyResultSummary(rsmID, loadFullResultSet = true)
        require(rsmOpt.isDefined,"Unable to read resultaSummay with specified ID " + rsmID)
        loadedRSMByID += rsmID -> rsmOpt.get

        rsmOpt.get
      }

      logger.trace(" Calculate SC for RSM "+rsmID)

      //VDS comment :Could get info from PeptideInstance.peptideMatches.filter on isValidated... but should be sure
      // PeptideInstance.peptideMatches != null ...
      val validPepMatchesSet = rsm.peptideMatchesByPeptideSetId.flatMap(_._2).toSet
      logger.trace(" GOT validPepMatchesSet SC for RSM "+rsmID)

      // Peptide Instance SC = PepMatch Count
      for (psm <- validPepMatchesSet) {
        if (pepIDs.contains(psm.peptideId)) {
          scByPepID.getOrElseUpdate(psm.peptideId, CounterSC(0)).incr()
        }
      }
      logger.trace(" return scByPepID for RSM "+rsmID)

      scByPepID

    } else {

      val childIDsOpt = getRSMChildsID(rsmID, execContext)

      // ***** RSM is result of a Merge of ResultSet : count Leave RS "valid" PSM
      if (childIDsOpt.isEmpty) {
        logger.trace(" RSM "+rsmID+" is in RS hierarchy. Get leave RS  ")
        // Use RS hierarchy : If next level is merge RS, suppose all hierarchy is a RS merge
        //	Get leaves RS
        val leavesRSs = getLeavesRS(rsmID, execContext)

        //Get RSM root of RS Merge : May not be the "final" Root RSM !!!   if RSMs have then be merged ...
        val rsm = if (loadedRSMByID.contains(rsmID)) loadedRSMByID(rsmID)
        else {
          logger.trace("Need to read RSM "+rsmID)
          val rsmOpt = rsmProvider.getLazyResultSummary(rsmID, loadFullResultSet = true)
          require(rsmOpt.isDefined,"Unable to read resultaSummay with specified ID " + rsmID)

          loadedRSMByID += rsmID -> rsmOpt.get

          rsmOpt.get
        }

        //Get filters used for RSM root of RS Merge
        val appliedPSMFilters = ResultSummaryFilterBuilder.buildPeptideMatchFilters(rsm)

        logger.trace(" Calculate SC for leave RS ")
        // Get PepID->SC for each RS
        leavesRSs.foreach(rs => {
          val rsPepInstSC: mutable.HashMap[Long, CounterSC] = countValidPSMInResultSet(rs, appliedPSMFilters)
          // Merge result in global result
          rsPepInstSC.foreach(entry => {
            if (pepIDs.contains(entry._1)) {
              scByPepID.getOrElseUpdate(entry._1, CounterSC(0)).add(entry._2.spectralCountVal)
            }
          })
        })

        // ***** RSM is result of a Merge of ResultSummary : continue to go into hierarchy : child RSM could be RS merge result
      } else {
        logger.trace(" RSM "+rsmID+" is in RSM hierarchy. Go through RSM")
        //Use RSMs hierarchy.  rsm peptide Instance SC = sum child peptide Instance SC
        childIDsOpt.get.foreach(childID => {
          val childSCByPepID = getRSMSpectralCounterByPepId(rootRSM, childID, pepIDs, execContext)
          childSCByPepID.foreach(childSCEntry => {
            scByPepID.getOrElseUpdate(childSCEntry._1, CounterSC(0)).add(childSCEntry._2.spectralCountVal)
          })
        })
      }

      scByPepID
    }

  } //End getRSMSpectralCountByPepIdSet


  //A Optimiser: Prendre en entre la Map globale et l'incrementer...  
  def countValidPSMInResultSet(rs: LazyResultSet, appliedPSMFilters: Array[IPeptideMatchFilter]): mutable.HashMap[Long, CounterSC] = {
    appliedPSMFilters.foreach(psmFilter => {
      var allPSMs = rs.peptideMatches.toSeq
      if (rs.lazyDecoyResultSet.isDefined) {
        allPSMs ++= rs.lazyDecoyResultSet.get.peptideMatches
      }
      psmFilter.filterPeptideMatches(allPSMs, incrementalValidation = true, traceability = false)
    })

    val resultMap = new mutable.HashMap[Long, CounterSC]()
    rs.peptideMatches.filter(_.isValidated).foreach(psm => {
      resultMap.getOrElseUpdate(psm.peptideId, CounterSC(0)).incr()
    })

    resultMap
  }



  def getRSMChildsID(rsmID: Long, execContext: IExecutionContext): Option[Seq[Long]] = {

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

    execContext.getMSIDbConnectionContext.doWork(jdbcWork, false)

    childRSMIdsOp
  }

  def getLeavesRS(rsmID: Long, execContext: IExecutionContext): Seq[LazyResultSet] = {
    val providerContext = ProviderDecoratedExecutionContext(execContext) // Use Object factory

    val rsId = getRSIdForRsmID(rsmID, execContext)
    var leavesRsIds: Seq[Long] = getRSLeafChildsID(rsId, providerContext)
    var leavesRsBuilder = Seq.newBuilder[LazyResultSet]

    val provider: IResultSetProvider = providerContext.getProvider(classOf[IResultSetProvider])

    leavesRsIds.foreach(rsID => {
      if (!loadedRSByID.contains(rsID)) {
        val resultRS = provider.getResultSet(rsID)
        if (resultRS.isDefined) {
          loadedRSByID += rsID -> resultRS.get.toLazyResultSet()
          if(resultRS.get.decoyResultSet.isEmpty && resultRS.get.getDecoyResultSetId > 0){
             val decoyRS = provider.getResultSet(resultRS.get.getDecoyResultSetId)
             resultRS.get.decoyResultSet = decoyRS
              if (decoyRS.isDefined) 
                loadedRSByID += decoyRS.get.id -> decoyRS.get.toLazyResultSet()
          }
        } else {
          val msg = " !!! Unable to get leave search result with id " + rsID
          logger.warn(msg)
          throw new Exception(msg)
        }
      }
      leavesRsBuilder += loadedRSByID(rsID)
    })

    leavesRsBuilder.result
  }

  def getRSLeafChildsID(rsId: Long, execContext: IExecutionContext): Seq[Long] = {
    var allRSIds = Seq.newBuilder[Long]
    if(loadedRSByID.contains(rsId) && (loadedRSByID(rsId).descriptor.isSearchResult)){
       allRSIds += rsId
    } else {
     
      val jdbcWork = new JDBCWork() {
  
        override def execute(con: Connection) {
  
          val stmt = con.prepareStatement("select child_result_set_id from result_set_relation where result_set_relation.parent_result_set_id = ?")
          stmt.setLong(1, rsId)
          val sqlResultSet = stmt.executeQuery()
          var childDefined = false
          while (sqlResultSet.next) {
            childDefined = true
            val nextChildId = sqlResultSet.getInt(1)
            allRSIds ++= getRSLeafChildsID(nextChildId, execContext)
          }
          if (!childDefined)
            allRSIds += rsId
          stmt.close()
        } // End of jdbcWork anonymous inner class
      }
      execContext.getMSIDbConnectionContext.doWork(jdbcWork, false)
    }
    allRSIds.result
  }

 case class CounterSC(var spectralCountVal : Int){
   def incr(): Unit ={
     spectralCountVal = spectralCountVal+1
   }

   def add(incrementValue : Int): Unit ={
     spectralCountVal =  spectralCountVal + incrementValue
   }
 }

}