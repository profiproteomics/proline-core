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
      storerContext = new StorerContext(execCtx)
      msiDbCtx = storerContext.getMSIDbConnectionContext

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbCtx.isInTransaction()
      if (!wasInTransaction) msiDbCtx.beginTransaction()

      DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>

        val tmpMergedResultSummary = {
          if (resultSummaries.isDefined) {
            logger.info("Start merge from existing ResultSets")
            _mergeFromResultsSummaries(resultSummaries.get, storerContext, msiDbCtx)
          } else {
            logger.info("Start merge from ResultSet Ids")
            _mergeFromResultsSummaryIds(resultSummaryIds.get, storerContext, msiDbCtx, execCtx)
          }
        }

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

        logger.info("store result set...")

        //val rsStorer = new JPARsStorer()
        //storerContext = new StorerContext(udsDb, pdiDb, psDb, msiDb)
        //rsStorer.storeResultSet(mergedResultSet, storerContext)
        val rsStorer = RsStorer(msiDbCtx)
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
        >>>

        mergedResultSummary = tmpMergedResultSummary
      }, true) // End of JDBC work

      // Commit transaction if it was initiated locally
      if (!wasInTransaction) msiDbCtx.commitTransaction()

      msiTransacOk = true

    } finally {

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

      /*if (storerContext != null) {
        storerContext.closeAll()
      }*/

    }

    true
  }

  private def _mergeFromResultsSummaries(resultSummaries: Seq[ResultSummary], storerContext: StorerContext, msiDbCtx: DatabaseConnectionContext): ResultSummary = {
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
    val seqLengthByProtId = new MsiDbHelper(msiDbCtx).getSeqLengthByBioSeqId(proteinIdSet)
    >>>

    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type
    val peptideSetScoring = PepSetScoring.withName(resultSummaries(0).peptideSets(0).scoreType)
    val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring)
    val rsmMerger = new RsmMergerAlgo(pepSetScoreUpdater)

    logger.info("merging result summaries...")
    rsmMerger.mergeResultSummaries(resultSummaries, seqLengthByProtId)
  }

  private def _mergeFromResultsSummaryIds(resultSummaryIds: Seq[Long], storerContext: StorerContext, msiDbCtx: DatabaseConnectionContext, execCtx: IExecutionContext): ResultSummary = {
    val seqLengthByProtId = _buildSeqLength(resultSummaryIds, msiDbCtx)
    >>>
    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val scorings = msiDbHelper.getScoringByResultSummaryIds(resultSummaryIds)
    val peptideSetScoring = PepSetScoring.withName( scorings(0) )
    val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring)
    val rsmBuilder = new ResultSummaryBuilder(ResultSummary.generateNewId(), pepSetScoreUpdater, Some(seqLengthByProtId))

    logger.info("merging result summaries...")
    for (rsmId <- resultSummaryIds) {
        val resultSummary = ResultSummaryMerger._loadResultSummary(rsmId, execCtx)
        rsmBuilder.addResultSummary(resultSummary)
      }
    
    rsmBuilder.toResultSummary()
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