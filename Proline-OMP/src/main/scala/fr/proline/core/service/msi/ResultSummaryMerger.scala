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
    if (rsm.isEmpty) throw new IllegalArgumentException("Unknown ResultSummary Id: " + rsmId)
    rsm.get
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSummaryProvider(execContext: IExecutionContext): IResultSummaryProvider = {

    new SQLResultSummaryProvider(execContext.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext],
      execContext.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext],
      execContext.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext])

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

    var storerContext: StorerContext = null // For JPA use
    var msiDbCtx: DatabaseConnectionContext = null
    var msiTransacOk: Boolean = false

    try {
      storerContext = StorerContext(execCtx)
      msiDbCtx = storerContext.getMSIDbConnectionContext

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbCtx.isInTransaction()
      if (!wasInTransaction) msiDbCtx.beginTransaction()

      val tmpMergedResultSummary = {
        if (resultSummaries.isDefined) {
          logger.info("Start merge from existing ResultSets")
          _mergeFromResultsSummaries(resultSummaries.get, storerContext)
        } else {
          logger.info("Start merge from ResultSet Ids")
          _mergeFromResultsSummaryIds(resultSummaryIds.get, storerContext)
        }
      }

      //      _storeResultSummary(tmpMergedResultSummary, storerContext, msiEzDBC)

      // Commit transaction if it was initiated locally
      if (!wasInTransaction) msiDbCtx.commitTransaction()

      msiTransacOk = true

    } finally {
      
      if (storerContext != null) {
        storerContext.clear()
      }

      if (msiDbCtx.isInTransaction() && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          // Rollback is not useful for SQLite and has locking issue
          // http://www.sqlite.org/lang_transaction.html
          if (msiDbCtx.getDriverType() != DriverType.SQLITE)
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
      for(rsm <- resultSummaries ) {
      if (rsm.decoyResultSummary.isDefined) {
        decoyResultSummaries += rsm.decoyResultSummary.get
      } else {
        if (rsm.decoyResultSummaryId > 0L) decoyResultSummaries += ResultSummaryMerger._loadResultSummary(rsm.decoyResultSummaryId, storerContext)
      }
    }

    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type
    val peptideSetScoring = PepSetScoring.withName(resultSummaries(0).peptideSets(0).scoreType)
    val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring)
    val rsmMerger = new RsmMergerAlgo(pepSetScoreUpdater)

    var mergedDecoyRSId: Long = -1L
    if (decoyResultSummaries.length > 0) {

      // Retrieve protein ids
      val proteinIdSet = new HashSet[Long]
      for (rsm <- decoyResultSummaries) {

        val resultSetAsOpt = rsm.resultSet
        require(resultSetAsOpt != None, "the result summary must contain a result set")

        for (proteinMatch <- resultSetAsOpt.get.proteinMatches) {
          val proteinId = proteinMatch.getProteinId
          if (proteinId != 0L) proteinIdSet += proteinId
        }
      }

      // Retrieve sequence length mapped by the corresponding protein id
      val seqLengthByProtId = new MsiDbHelper(storerContext.getMSIDbConnectionContext).getSeqLengthByBioSeqId(proteinIdSet)
      logger.info("Merging DECOY Result Summaries...")
      val tmpMergedResultSummary = rsmMerger.mergeResultSummaries(resultSummaries, seqLengthByProtId)
      logger.info("Storing DECOY result summary...")
      var decoyRsm = _storeResultSummary(tmpMergedResultSummary, storerContext)
      mergedDecoyRSId = decoyRsm.getResultSetId
    }

    // Retrieve protein ids
    val proteinIdSet = new HashSet[Long]
    for (rsm <- resultSummaries) {

      val resultSetAsOpt = rsm.resultSet
      require(resultSetAsOpt != None, "the result summary must contain a result set")

      for (proteinMatch <- resultSetAsOpt.get.proteinMatches) {
        val proteinId = proteinMatch.getProteinId
        if (proteinId != 0L) proteinIdSet += proteinId
      }
    }

    // Retrieve sequence length mapped by the corresponding protein id
    val seqLengthByProtId = new MsiDbHelper(storerContext.getMSIDbConnectionContext).getSeqLengthByBioSeqId(proteinIdSet)
    >>>

    logger.info("Merging TARGET Result Summaries...")
    val tmpMergedResultSummary = rsmMerger.mergeResultSummaries(resultSummaries, seqLengthByProtId)
     if (mergedDecoyRSId > 0L) {
      tmpMergedResultSummary.resultSet.get.setDecoyResultSetId(mergedDecoyRSId)
    }

    logger.info("Storing TARGET result summary...")
    _storeResultSummary(tmpMergedResultSummary, storerContext)

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

    logger.debug("TARGET ResultSummaries Ids : " + resultSummaryIds.mkString(" | "))
    val decoyRSummaryIds = msiDbHelper.getDecoyRsmIds(resultSummaryIds)
    logger.debug("DECOY ResultSet Ids : " + decoyRSummaryIds.mkString(" | "))

    val nTargetRS = resultSummaryIds.length
    val nDecoyRS = decoyRSummaryIds.length

    if (nDecoyRS != nTargetRS) {
      logger.warn("Inconsistent number of TARGET ResultSets: " + nTargetRS + " number of DECOY ResultSets: " + nDecoyRS)
    }

    var mergedDecoyRSId: Long = -1L

    if (decoyRSummaryIds.length > 0) {
      val decoyRsmBuilder = new ResultSummaryBuilder(ResultSummary.generateNewId(), true, pepSetScoreUpdater, Some(seqLengthByProtId))
      logger.info("Merging DECOY result summaries...")
      for (rsmId <- resultSummaryIds) {
        val resultSummary = ResultSummaryMerger._loadResultSummary(rsmId, execCtx)
        decoyRsmBuilder.addResultSummary(resultSummary)
      }

      var decoyRsm = decoyRsmBuilder.toResultSummary()
      logger.info("Storing DECOY result summary...")
      _storeResultSummary(decoyRsm, storerContext)
      mergedDecoyRSId = decoyRsm.getResultSetId
      decoyRsm = null
    }

    val rsmBuilder = new ResultSummaryBuilder(ResultSummary.generateNewId(), false, pepSetScoreUpdater, Some(seqLengthByProtId))
    logger.info("Merging TARGET result summaries...")
    for (rsmId <- resultSummaryIds) {
      val resultSummary = ResultSummaryMerger._loadResultSummary(rsmId, execCtx)
      rsmBuilder.addResultSummary(resultSummary)
    }

    val targetRsm = rsmBuilder.toResultSummary()
    if (mergedDecoyRSId > 0L) {
      targetRsm.resultSet.get.setDecoyResultSetId(mergedDecoyRSId)
    }

    logger.info("Storing TARGET result summary...")
    _storeResultSummary(targetRsm, storerContext)
  }

  private def _storeResultSummary(tmpMergedResultSummary: ResultSummary, storerContext: StorerContext): ResultSummary = {

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>

      val proteinSets = tmpMergedResultSummary.proteinSets
      logger.info("nb protein sets:" + tmpMergedResultSummary.proteinSets.length)

      // Validate all protein sets
      proteinSets.foreach { _.isValidated = true }

      // Retrieve the merged result set
      val mergedResultSet = tmpMergedResultSummary.resultSet.get
      val peptideInstances = tmpMergedResultSummary.peptideInstances
      val pepInstanceByPepId = peptideInstances.map { pepInst => pepInst.peptide.id -> pepInst } toMap

      // Map peptide matches and proptein matches by their tmp id
      val mergedPepMatchByTmpId = mergedResultSet.peptideMatches.map { p => p.id -> p } toMap
      val protMatchByTmpId = mergedResultSet.proteinMatches.map { p => p.id -> p } toMap

      logger.info("Store result set...")

      //val rsStorer = new JPARsStorer()
      //storerContext = new StorerContext(udsDb, pdiDb, psDb, msiDb)
      //rsStorer.storeResultSet(mergedResultSet, storerContext)
      val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
      rsStorer.storeResultSet(mergedResultSet, storerContext)

      //msiDbCtx.getEntityManager.flush() // Flush before returning to SQL msiEzDBC

      >>>

      // Link parent result set to its child result sets
      val parentRsId = mergedResultSet.id
      val rsIds = if (resultSummaries.isDefined) resultSummaries.get.map { _.getResultSetId }.distinct else { resultSummaryIds.get }

      // Insert result set relation between parent and its children
      val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()
      msiEzDBC.executePrepared(rsRelationInsertQuery) { stmt =>
        for (childRsId <- rsIds) stmt.executeWith(parentRsId, childRsId)
      }
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

        if (oldPepMatchPropsById != null)
          pepInstance.peptideMatchPropertiesById = newPepMatchPropsById.toMap

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

    tmpMergedResultSummary
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