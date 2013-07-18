package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context._
import fr.proline.core.algo.msi.{ ResultSummaryMerger => RsmMergerAlgo }
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
import fr.proline.core.algo.msi.ResultSummaryBuilder

object ResultSummaryMerger {

  def _loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {
    val rsmProvider = getResultSummaryProvider(execContext)

    val rsm = rsmProvider.getResultSummary(rsmId, true)
    if (rsm.isEmpty) {
      throw new IllegalArgumentException("Unknown ResultSummary Id: " + rsmId)
    }

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
  resultSummaries: Option[Seq[ResultSummary]]) extends IService with Logging {

  var mergedResultSummary: ResultSummary = null

  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    //this.msiDb.closeConnection()

  }

  def runService(): Boolean = {
    var storerContext: StorerContext = null
    var msiDbCtx: DatabaseConnectionContext = null
    var msiTransacOk: Boolean = false

    try {
      storerContext = StorerContext(execCtx)
      msiDbCtx = storerContext.getMSIDbConnectionContext

      /* Check if a transaction is already initiated */
      val wasInTransaction = msiDbCtx.isInTransaction

      if (!wasInTransaction) {
        msiDbCtx.beginTransaction()
      }

      mergedResultSummary = if (resultSummaries.isDefined) {
        logger.info("Start merge from existing ResultSummaries")
        _mergeFromResultsSummaries(resultSummaries.get, storerContext)
      } else {
        logger.info("Start merge from ResultSummary Ids")
        _mergeFromResultsSummaryIds(resultSummaryIds.get, storerContext)
      }

      /* Commit transaction if it was initiated locally */
      if (!wasInTransaction) {
        msiDbCtx.commitTransaction()
      }

      msiTransacOk = true
    } finally {

      if (storerContext != null) {
        storerContext.clear()
      }

      if (msiDbCtx.isInTransaction && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          msiDbCtx.rollbackTransaction()
        } catch {
          case ex: Exception => logger.error("Error rollbacking MSI Db Transaction", ex)
        }

      }

    }

    true
  }

  private def _mergeFromResultsSummaries(resultSummaries: Seq[ResultSummary], storerContext: StorerContext): ResultSummary = {

    val decoyResultSummaries = new ArrayBuffer[ResultSummary]
    for (rsm <- resultSummaries) {
      val optionalDecoyRSM = rsm.decoyResultSummary
      if (optionalDecoyRSM.isDefined) {
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
    val rsmMerger = new RsmMergerAlgo(pepSetScoreUpdater)

    var mergedDecoyRSMId: Long = -1L
    var mergedDecoyRSId: Long = -1L

    if (decoyResultSummaries.length > 0) {

      // Retrieve protein ids
      val proteinIdSet = new HashSet[Long]
      for (rsm <- decoyResultSummaries) {

        val optionalRS = rsm.resultSet
        require(optionalRS.isDefined, "ResultSummary must contain a valid ResultSet")

        for (proteinMatch <- optionalRS.get.proteinMatches) {

          val proteinId = proteinMatch.getProteinId
          if (proteinId != 0L) {
            proteinIdSet += proteinId
          }

        }

      }

      // Retrieve sequence length mapped by the corresponding protein id
      val seqLengthByProtId = new MsiDbHelper(storerContext.getMSIDbConnectionContext).getSeqLengthByBioSeqId(proteinIdSet)
      logger.debug("Merging DECOY ResultSummaries ...")
      val mergedDecoyRSM = rsmMerger.mergeResultSummaries(resultSummaries, seqLengthByProtId)

      logger.debug("Storing DECOY ResultSummary ...")
      _storeResultSummary(mergedDecoyRSM, storerContext)
      mergedDecoyRSMId = mergedDecoyRSM.id
      mergedDecoyRSId = mergedDecoyRSM.getResultSetId
    }

    // Retrieve protein ids
    val proteinIdSet = new HashSet[Long]
    for (rsm <- resultSummaries) {

      val optionalRS = rsm.resultSet
      require(optionalRS.isDefined, "ResultSummary must contain a valid ResultSet")

      for (proteinMatch <- optionalRS.get.proteinMatches) {

        val proteinId = proteinMatch.getProteinId
        if (proteinId != 0L) {
          proteinIdSet += proteinId
        }

      }

    }

    // Retrieve sequence length mapped by the corresponding protein id
    val seqLengthByProtId = new MsiDbHelper(storerContext.getMSIDbConnectionContext).getSeqLengthByBioSeqId(proteinIdSet)
    >>>

    logger.info("Merging TARGET ResultSummaries ...")
    val mergedTargetRSM = rsmMerger.mergeResultSummaries(resultSummaries, seqLengthByProtId)

    /* Set Ids of decoy RSM and RS */
    if (mergedDecoyRSMId > 0L) {
      mergedTargetRSM.setDecoyResultSummaryId(mergedDecoyRSMId)
    }

    if (mergedDecoyRSId > 0L) {
      mergedTargetRSM.resultSet.get.setDecoyResultSetId(mergedDecoyRSId)
    }

    logger.info("Storing TARGET ResultSummary ...")
    _storeResultSummary(mergedTargetRSM, storerContext)

    mergedTargetRSM
  }

  private def _mergeFromResultsSummaryIds(resultSummaryIds: Seq[Long], storerContext: StorerContext): ResultSummary = {
    val seqLengthByProtId = _buildSeqLength(resultSummaryIds, storerContext.getMSIDbConnectionContext)
    >>>
    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type
    val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)
    val scorings = msiDbHelper.getScoringByResultSummaryIds(resultSummaryIds)
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
      var decoyRsmBuilder = new ResultSummaryBuilder(ResultSummary.generateNewId(), true, pepSetScoreUpdater, Some(seqLengthByProtId))

      logger.debug("Merging DECOY ResultSummaries ...")
      for (rsmId <- resultSummaryIds) {
        val resultSummary = ResultSummaryMerger._loadResultSummary(rsmId, execCtx)
        decoyRsmBuilder.addResultSummary(resultSummary)
      }

      var mergedDecoyRSM = decoyRsmBuilder.toResultSummary

      decoyRsmBuilder = null // Eligible for Garbage collection

      logger.debug("Storing DECOY ResultSummary ...")
      _storeResultSummary(mergedDecoyRSM, storerContext)

      mergedDecoyRSMId = mergedDecoyRSM.id
      mergedDecoyRSId = mergedDecoyRSM.getResultSetId

      mergedDecoyRSM = null // Eligible for Garbage collection
    }

    var rsmBuilder = new ResultSummaryBuilder(ResultSummary.generateNewId(), false, pepSetScoreUpdater, Some(seqLengthByProtId))

    logger.debug("Merging TARGET ResultSummaries ...")
    for (rsmId <- resultSummaryIds) {
      val resultSummary = ResultSummaryMerger._loadResultSummary(rsmId, execCtx)
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
    _storeResultSummary(mergedTargetRSM, storerContext)

    mergedTargetRSM
  }

  private def _storeResultSummary(tmpMergedResultSummary: ResultSummary, storerContext: StorerContext) {

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

      // Do not link RS but RSM when merging ReultSummaries
      //      val parentRsId = mergedResultSet.id
      //      val rsIds = if (resultSummaries.isDefined) resultSummaries.get.map { _.getResultSetId }.distinct else { resultSummaryIds.get }
      //
      //      // Insert result set relation between parent and its children
      //      val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()
      //      msiEzDBC.executePrepared(rsRelationInsertQuery) { stmt =>
      //        for (childRsId <- rsIds) stmt.executeWith(parentRsId, childRsId)
      //      }
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
        val newProtMatchIds = proteinSet.proteinMatchIds.map { protMatchByTmpId(_).id }
        proteinSet.proteinMatchIds = newProtMatchIds
      }

      // Store result summary
      logger.info("store result summary...")
      RsmStorer(execCtx.getMSIDbConnectionContext).storeResultSummary(tmpMergedResultSummary, execCtx)
    }, true)
    >>>

  }

  private def _buildSeqLength(resultSummaryIds: Seq[Long], msiDbCtx: DatabaseConnectionContext): Map[Long, Int] = {
    val msiDbHelper = new MsiDbHelper(msiDbCtx)

    val resultSetIds = msiDbHelper.getResultSetIdByResultSummaryId(resultSummaryIds)

    // Retrieve protein ids
    val proteinIdSet = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT bio_sequence_id FROM protein_match " +
          "WHERE bio_sequence_id is not null " +
          "AND result_set_id IN (" + resultSetIds.values.mkString(",") + ") ")
    })

    // Retrieve sequence length mapped by the corresponding protein id
    msiDbHelper.getSeqLengthByBioSeqId(proteinIdSet)
  }

}
