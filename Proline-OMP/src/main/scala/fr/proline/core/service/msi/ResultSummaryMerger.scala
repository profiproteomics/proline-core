package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context._
import fr.proline.core.algo.msi.scoring.{ PepSetScoring, PeptideSetScoreUpdater }
import fr.proline.core.dal._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.{ RsStorer, RsmStorer }
import fr.proline.repository.DriverType
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.algo.msi.ResultSummaryAdder
import fr.proline.core.algo.msi.AdditionMode
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import scala.collection.mutable.SetBuilder
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable

object ResultSummaryMerger {

  def _loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {
    val rsmProvider = getResultSummaryProvider(execContext)

    val rsm = rsmProvider.getResultSummary(rsmId, true)
    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)

    rsm.get
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSummaryProvider(execContext: IExecutionContext): IResultSummaryProvider = {

    new SQLResultSummaryProvider(execContext.getMSIDbConnectionContext,
      execContext.getPSDbConnectionContext,
      execContext.getUDSDbConnectionContext)
  }

}

class ResultSummaryMerger(
  execCtx: IExecutionContext,
  resultSummaryIds: Option[Seq[Long]],
  resultSummaries: Option[Seq[ResultSummary]],
  aggregationMode: Option[AdditionMode.Value] = None) extends IService with LazyLogging {

  var mergedResultSummary: ResultSummary = null

  override protected def beforeInterruption = {
    // Release database connections
    logger.info("Do NOTHING")
    //this.msiDb.closeConnection()

  }

  def runService(): Boolean = {
    var storerContext: StorerContext = null
    var msiDbCtx: DatabaseConnectionContext = null
    var localMSITransaction: Boolean = false
    var msiTransacOk: Boolean = false

    try {
      storerContext = StorerContext(execCtx)
      msiDbCtx = storerContext.getMSIDbConnectionContext

      /* Check if a transaction is already initiated */
      if (!msiDbCtx.isInTransaction) {
        msiDbCtx.beginTransaction()
        localMSITransaction = true
        msiTransacOk = false
      }

      mergedResultSummary = if (resultSummaries.isDefined) {
        logger.info("Start merge from existing ResultSummaries")
        _mergeFromResultsSummaries(resultSummaries.get, aggregationMode, storerContext)
      } else {
        logger.info("Start merge from ResultSummary Ids")
        _mergeFromResultsSummaryIds(resultSummaryIds.get, aggregationMode, storerContext)
      }

      /* Commit transaction if it was initiated locally */
      if (localMSITransaction) {
        msiDbCtx.commitTransaction()
      }

      msiTransacOk = true
    } finally {

      if (storerContext != null) {
        storerContext.clearContext()
      }

      if (localMSITransaction && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          msiDbCtx.rollbackTransaction()
        } catch {
          case ex: Exception => logger.error("Error rollbacking MSI Db Transaction", ex)
        }

      }

    }

    msiTransacOk
  }

  private def _mergeFromResultsSummaries(resultSummaries: Seq[ResultSummary], aggregationMode: Option[AdditionMode.Value], storerContext: StorerContext): ResultSummary = {

    val decoyResultSummaries = new ArrayBuffer[ResultSummary]

    for (rsm <- resultSummaries) {
      val optionalDecoyRSM = rsm.decoyResultSummary

      if (optionalDecoyRSM != null && optionalDecoyRSM.isDefined) {
        decoyResultSummaries += optionalDecoyRSM.get
      } else {

        val decoyRSMId = rsm.getDecoyResultSummaryId
        if (decoyRSMId > 0L) {
          decoyResultSummaries += ResultSummaryMerger._loadResultSummary(decoyRSMId, storerContext)
        }

      }

    }

    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type
    val peptideSetScoring = PepSetScoring.withName(resultSummaries(0).peptideSets(0).scoreType)
    val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring)

    var mergedDecoyRSMId: Long = -1L
    var mergedDecoyRSId: Long = -1L

    if (!decoyResultSummaries.isEmpty) {
      val distinctRSMIds = scala.collection.mutable.Set.empty[Long]
      val distinctChildRSIdsB = Seq.newBuilder[Long]

      // Retrieve protein ids
      val proteinIdSet = new HashSet[Long]
      for (decoyRSM <- decoyResultSummaries) {

        val rsmPK = decoyRSM.id
        if (rsmPK > 0L) {
          distinctRSMIds += rsmPK
        }

        val optionalRS = decoyRSM.resultSet
        require(optionalRS.isDefined, "ResultSummary must contain a valid ResultSet")
    	
        //Save associated RS Ids
        distinctChildRSIdsB += optionalRS.get.id
    	
        for (proteinMatch <- optionalRS.get.proteinMatches) {

          val proteinId = proteinMatch.getProteinId
          if (proteinId != 0L) {
            proteinIdSet += proteinId
          }

        }

      }

      logger.debug("Merging DECOY ResultSummaries ...")
      val mergedDecoyRSM = new ResultSummaryAdder(
        resultSetId = ResultSummary.generateNewId(),
        isDecoy = true,
        pepSetScoreUpdater = pepSetScoreUpdater,
        additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
      )
      .addResultSummaries(decoyResultSummaries)
      .toResultSummary()


      logger.debug("Storing DECOY ResultSummary ...")
      _storeResultSummary(storerContext, mergedDecoyRSM, distinctRSMIds.toSet, distinctChildRSIdsB.result)
      mergedDecoyRSMId = mergedDecoyRSM.id
      mergedDecoyRSId = mergedDecoyRSM.getResultSetId
    }

    // Retrieve protein ids
    val proteinIdSet = new HashSet[Long]

    val distinctRSMIds = scala.collection.mutable.Set.empty[Long] //RSM Ids to merge
    val distinctChildRSIdsB = Seq.newBuilder[Long] //Associated RS Ids to merge

    for (rsm <- resultSummaries) {

      val rsmPK = rsm.id
      if (rsmPK > 0L) {
        distinctRSMIds += rsmPK
      }

      val optionalRS = rsm.resultSet
      require(optionalRS.isDefined, "ResultSummary must contain a valid ResultSet")
      distinctChildRSIdsB += optionalRS.get.id //Save associated RS Id
      
      for (proteinMatch <- optionalRS.get.proteinMatches) {

        val proteinId = proteinMatch.getProteinId
        if (proteinId != 0L) {
          proteinIdSet += proteinId
        }

      }

    }

    logger.info("Merging TARGET ResultSummaries ...")
    val mergedTargetRSM = new ResultSummaryAdder(
      resultSetId = ResultSummary.generateNewId(),
      isDecoy = true,
      pepSetScoreUpdater = pepSetScoreUpdater,
      additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
    )
    .addResultSummaries(resultSummaries)
    .toResultSummary()

    /* Set Ids of decoy RSM and RS */
    if (mergedDecoyRSMId > 0L) {
      mergedTargetRSM.setDecoyResultSummaryId(mergedDecoyRSMId)
    }

    if (mergedDecoyRSId > 0L) {
      mergedTargetRSM.resultSet.get.setDecoyResultSetId(mergedDecoyRSId)
    }

    logger.info("Storing TARGET ResultSummary ...")
    _storeResultSummary(storerContext, mergedTargetRSM, distinctRSMIds.toSet, distinctChildRSIdsB.result)

    mergedTargetRSM
  }

  private def _mergeFromResultsSummaryIds(resultSummaryIds: Seq[Long], aggregationMode: Option[AdditionMode.Value], storerContext: StorerContext): ResultSummary = {
    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type
    val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)
    val scorings = msiDbHelper.getScoringsByResultSummaryIds(resultSummaryIds)
    val peptideSetScoring = PepSetScoring.withName(scorings(0))
    val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring)

    logger.debug("TARGET ResultSummary Ids : " + resultSummaryIds.mkString(" | "))
    val decoyRSMIds = msiDbHelper.getDecoyRsmIds(resultSummaryIds)
    logger.debug("DECOY ResultSummary Ids : " + decoyRSMIds.mkString(" | "))

    val nTargetRSM = resultSummaryIds.length
    val nDecoyRSM = decoyRSMIds.length

    if (nDecoyRSM != nTargetRSM) {
      logger.warn("Inconsistent number of TARGET ResultSummaries: " + nTargetRSM + " number of DECOY ResultSummaries: " + nDecoyRSM)
    }

    var mergedDecoyRSMId: Long = -1L
    var mergedDecoyRSId: Long = -1L

    if (nDecoyRSM > 0) {
      val distinctRSMIds = scala.collection.mutable.Set.empty[Long]
      
      val distinctChildRSIdsB = Seq.newBuilder[Long]

      var decoyRsmBuilder = new ResultSummaryAdder(
        resultSetId = ResultSummary.generateNewId(),
        isDecoy = true,
        pepSetScoreUpdater = pepSetScoreUpdater,
        additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
      )

      logger.debug("Merging DECOY ResultSummaries ...")
      for (decoyRSMId <- decoyRSMIds) {
        val decoyRSM = ResultSummaryMerger._loadResultSummary(decoyRSMId, execCtx)

        val rsmPK = decoyRSM.id
        if (rsmPK > 0L) {
          distinctRSMIds += rsmPK
        }
        
        distinctChildRSIdsB +=  decoyRSM.getResultSetId
        decoyRsmBuilder.addResultSummary(decoyRSM)
      }

      var mergedDecoyRSM = decoyRsmBuilder.toResultSummary

      decoyRsmBuilder = null // Eligible for Garbage collection

      logger.debug("Storing DECOY ResultSummary ...")
      _storeResultSummary(storerContext, mergedDecoyRSM, distinctRSMIds.toSet, distinctChildRSIdsB.result)

      mergedDecoyRSMId = mergedDecoyRSM.id
      mergedDecoyRSId = mergedDecoyRSM.getResultSetId

      mergedDecoyRSM = null // Eligible for Garbage collection
    }

    val distinctRSMIds = scala.collection.mutable.Set.empty[Long]
    val distinctChildRSIdsB =  Seq.newBuilder[Long]
    
    var rsmBuilder = new ResultSummaryAdder(
      resultSetId = ResultSummary.generateNewId(),
      isDecoy = false,
      pepSetScoreUpdater = pepSetScoreUpdater,
      additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
    )

    logger.debug("Merging TARGET ResultSummaries ...")
    for (rsmId <- resultSummaryIds) {
      val resultSummary = ResultSummaryMerger._loadResultSummary(rsmId, execCtx)

      val rsmPK = resultSummary.id
      if (rsmPK > 0L) {
        distinctRSMIds += rsmPK
      }
      
      distinctChildRSIdsB += resultSummary.getResultSetId
      rsmBuilder.addResultSummary(resultSummary)
    }

    val mergedTargetRSM = rsmBuilder.toResultSummary

    rsmBuilder = null // Eligible for Garbage collection

    if (mergedDecoyRSMId > 0L) {
      mergedTargetRSM.setDecoyResultSummaryId(mergedDecoyRSMId)
    }

    if (mergedDecoyRSId > 0L) {
      mergedTargetRSM.resultSet.get.setDecoyResultSetId(mergedDecoyRSId)
    }

    logger.debug("Storing TARGET ResultSummary ...")
    _storeResultSummary(storerContext, mergedTargetRSM, distinctRSMIds.toSet, distinctChildRSIdsB.result)

    mergedTargetRSM
  }

  private def _storeResultSummary(storerContext: StorerContext, tmpMergedResultSummary: ResultSummary, childrenRSMIds: Set[Long], childrenRSIds : Seq[Long]) {

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>

      val proteinSets = tmpMergedResultSummary.proteinSets
      logger.debug("Nb ProteinSets:" + tmpMergedResultSummary.proteinSets.length)

      // Validate all protein sets
      proteinSets.foreach { _.isValidated = true }

      // Retrieve the merged result set
      val mergedResultSet = tmpMergedResultSummary.resultSet.get
      val peptideInstances = tmpMergedResultSummary.peptideInstances
      val pepInstanceByPepId = peptideInstances.map { pepInst => pepInst.peptide.id -> pepInst } toMap

      // Map peptide matches and proptein matches by their tmp id
      val mergedPepMatchByTmpId = mergedResultSet.peptideMatches.map { p => p.id -> p } toMap
      val protMatchByTmpId = mergedResultSet.proteinMatches.map { p => p.id -> p } toMap

      logger.debug("Storing ResultSet ...")

      //val rsStorer = new JPARsStorer()
      //storerContext = new StorerContext(udsDb, pdiDb, psDb, msiDb)
      //rsStorer.storeResultSet(mergedResultSet, storerContext)
      val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
      rsStorer.storeResultSet(mergedResultSet, storerContext)

      //msiDbCtx.getEntityManager.flush() // Flush before returning to SQL msiEzDBC

      >>>

      // Update peptide match ids referenced in peptide instances
      for (pepInstance <- peptideInstances) {
        val oldPepMatchIds = pepInstance.peptideMatchIds

        val oldPepMatchPropsById = pepInstance.peptideMatchPropertiesById

        // Retrieve new pep match ids and re-map peptide match RSM properties with the new ids
        val newPepMatchIds = new ArrayBuffer[Long](pepInstance.getPeptideMatchIds.length)
        val newPepMatchPropsById = new HashMap[Long, PeptideMatchResultSummaryProperties]

        pepInstance.bestPeptideMatchId = mergedPepMatchByTmpId(pepInstance.bestPeptideMatchId).id

        for (oldPepMatchId <- oldPepMatchIds) {
          val newPepMatchId = mergedPepMatchByTmpId(oldPepMatchId).id
          newPepMatchIds += newPepMatchId

          if (oldPepMatchPropsById != null) {
            newPepMatchPropsById += newPepMatchId -> oldPepMatchPropsById(oldPepMatchId)
          }

        }

        pepInstance.peptideMatchIds = newPepMatchIds.toArray

        if (oldPepMatchPropsById != null) {
          pepInstance.peptideMatchPropertiesById = newPepMatchPropsById.toMap
        }

      }

      // Update protein match ids referenced in peptide sets
      val peptideSets = tmpMergedResultSummary.peptideSets
      for (peptideSet <- peptideSets) {
        val newProtMatchIds = peptideSet.proteinMatchIds.map { protMatchByTmpId(_).id }
        peptideSet.proteinMatchIds = newProtMatchIds
      }

      // Update protein match ids referenced in protein sets
      for (proteinSet <- proteinSets) {
        val newSameSetProtMatchIds = proteinSet.getSameSetProteinMatchIds.map { protMatchByTmpId(_).id }
        proteinSet.samesetProteinMatchIds = newSameSetProtMatchIds
        val newSubSetProtMatchIds = proteinSet.getSubSetProteinMatchIds.map { protMatchByTmpId(_).id }
        proteinSet.subsetProteinMatchIds = newSubSetProtMatchIds
        
      }

      // Store result summary
      logger.info("store result summary...")
      RsmStorer(execCtx.getMSIDbConnectionContext).storeResultSummary(tmpMergedResultSummary, execCtx)

      >>>

      //Store link between RS & RSM ( mergedResultSet.mergedResultSummaryId ) 
      mergedResultSet.mergedResultSummaryId = tmpMergedResultSummary.id

      val rsUpdateQuery = "UPDATE "+MsiDbResultSetTable.name+" SET "+ MsiDbResultSetTable.columns.MERGED_RSM_ID +" = ? WHERE "+MsiDbResultSetTable.columns.ID +" = ? "
      msiEzDBC.executePrepared(rsUpdateQuery){stmt => stmt.executeWith(tmpMergedResultSummary.id, mergedResultSet.id)}
      
      if (!childrenRSMIds.isEmpty) {
        /* Do not link RS but RSM when merging ReultSummaries */
        val parentRSMId = tmpMergedResultSummary.id

        logger.debug("Linking children ResultSummaries to parent #" + parentRSMId)

        // Insert result set relation between parent and its children
        val rsmRelationInsertQuery = MsiDbResultSummaryRelationTable.mkInsertQuery()

        msiEzDBC.executePrepared(rsmRelationInsertQuery) { stmt =>
          for (childRSMId <- childrenRSMIds) stmt.executeWith(parentRSMId, childRSMId)
        }

      }

    if (!childrenRSIds.isEmpty) {
        /* link also RS when merging ReultSummaries */
        val parentRSId = tmpMergedResultSummary.getResultSetId

        logger.debug("Linking children RSM.ResultSet to parent #" + parentRSId)

        // Insert result set relation between parent and its children
        val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()

        msiEzDBC.executePrepared(rsRelationInsertQuery) { stmt =>
          for (childRSId <- childrenRSIds) stmt.executeWith(parentRSId, childRSId)
        }

      }
      
    }, true)
    >>>

  }

}
