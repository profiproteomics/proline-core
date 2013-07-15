package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.{ ResultSetMerger => ResultSetMergerAlgo }
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.repository.DriverType
import fr.proline.core.algo.msi.ResultSetBuilder
import fr.proline.core.dal.DoJDBCReturningWork

object ResultSetMerger {

  def _loadResultSet(rsId: Long, execContext: IExecutionContext): ResultSet = {
    val rsProvider = getResultSetProvider(execContext)
    val rs = rsProvider.getResultSet(rsId)
    if (rs.isEmpty) throw new IllegalArgumentException("Unknown ResultSet Id: " + rsId)
    rs.get
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSetProvider(execContext: IExecutionContext): IResultSetProvider = {

    if (execContext.isJPA) {
      new ORMResultSetProvider(execContext.getMSIDbConnectionContext, execContext.getPSDbConnectionContext, execContext.getUDSDbConnectionContext)
    } else {
      new SQLResultSetProvider(execContext.getMSIDbConnectionContext,
        execContext.getPSDbConnectionContext,
        execContext.getUDSDbConnectionContext)
    }

  }
}

class ResultSetMerger(
  execCtx: IExecutionContext,
  resultSetIds: Option[Seq[Long]],
  resultSets: Option[Seq[ResultSet]]) extends IService with Logging {

  var mergedResultSet: ResultSet = null
  def mergedResultSetId = if (mergedResultSet != null) mergedResultSet.id else 0L

  // Merge result sets
  private val rsMergerAlgo = new ResultSetMergerAlgo()

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
      //storerContext = new StorerContext(ContextFactory.buildExecutionContext(dbManager, projectId, true))
      storerContext = new StorerContext(execCtx)
      msiDbCtx = storerContext.getMSIDbConnectionContext

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbCtx.isInTransaction()
      if (!wasInTransaction) msiDbCtx.beginTransaction()

      if (resultSets.isDefined) {
        logger.info("Start merge from existing ResultSets")
        _mergeFromResultsSets(resultSets.get, storerContext)
      } else {
        logger.info("Start merge from ResultSet Ids")
        _mergeFromResultsSetIds(resultSetIds.get, storerContext)
      }

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

    this.beforeInterruption()

    true
  }

  private def _mergeFromResultsSetIds(resultSetIds: Seq[Long], storerContext: StorerContext) {
    val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)
    
    logger.debug("TARGET ResultSet Ids : " + resultSetIds.mkString(" | "))

    val decoyRSIds = msiDbHelper.getDecoyRsIds(resultSetIds)
    
    logger.debug("DECOY ResultSet Ids : " + decoyRSIds.mkString(" | "))

    val nTargetRS = resultSetIds.length
    val nDecoyRS = decoyRSIds.length

    if (nDecoyRS != nTargetRS) {
      logger.warn("Inconsistent number of TARGET ResultSets: " + nTargetRS + " number of DECOY ResultSets: " + nDecoyRS)
    }

    var mergedDecoyRSId: Long = -1L

    if (decoyRSIds.length > 0) {
      var seqLengthByProtId: Map[Long, Int] = _buildSeqLength(decoyRSIds, storerContext.getMSIDbConnectionContext)

      var decoyMergerAlgo: ResultSetBuilder = new ResultSetBuilder(ResultSet.generateNewId, true, Some(seqLengthByProtId))

      for (rsId <- decoyRSIds) {
        val resultSet = ResultSetMerger._loadResultSet(rsId, execCtx)
        decoyMergerAlgo.addResultSet(resultSet)
      }

      var decoyRS: ResultSet = decoyMergerAlgo.toResultSet

      decoyMergerAlgo = null // Eligible for Garbage collection      
      seqLengthByProtId = null

      DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>
        /* Store merged decoy result set */
        _storeMergedResultSet(storerContext, msiEzDBC, decoyRS, decoyRSIds)
      }, true) // end of JDBC work

      mergedDecoyRSId = decoyRS.id

      logger.debug("Merged DECOY ResultSet Id: " + mergedDecoyRSId)

      decoyRS = null // Eligible for Garbage collection
    }

    var seqLengthByProtId: Map[Long, Int] = _buildSeqLength(resultSetIds, storerContext.getMSIDbConnectionContext)

    var targetMergerAlgo: ResultSetBuilder = new ResultSetBuilder(ResultSet.generateNewId, false, Some(seqLengthByProtId))

    for (rsId <- resultSetIds) {
      val resultSet = ResultSetMerger._loadResultSet(rsId, execCtx)
      targetMergerAlgo.addResultSet(resultSet)
      logger.info("Additioner state : " + targetMergerAlgo.mergedProteinMatches.size + " ProMs, " + targetMergerAlgo.peptideById.size + " Peps," + targetMergerAlgo.mergedProteinMatches.map(_.sequenceMatches).flatten.length + " SeqMs")
    }

    mergedResultSet = targetMergerAlgo.toResultSet

    targetMergerAlgo = null // Eligible for Garbage collection
    seqLengthByProtId = null

    if (mergedDecoyRSId > 0L) {
      mergedResultSet.setDecoyResultSetId(mergedDecoyRSId)
    }

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>
      /* Store merged target result set */
      _storeMergedResultSet(storerContext, msiEzDBC, mergedResultSet, resultSetIds)
    }, true) // end of JDBC work

    logger.debug("Merged TARGET ResultSet Id: " + mergedResultSet.id)
  }

  private def _mergeFromResultsSets(resultSets: Seq[ResultSet], storerContext: StorerContext) {
    val decoyResultSets = for (
      rs <- resultSets if ((rs.decoyResultSet != null) && rs.decoyResultSet.isDefined)
    ) yield rs.decoyResultSet.get

    val allResultSets = resultSets ++ decoyResultSets

    val seqLengthByProtId = _buildSeqLength(allResultSets.map { _.id }, storerContext.getMSIDbConnectionContext)
    >>>

    // Merge target result sets
    mergedResultSet = this._mergeResultSets(resultSets, seqLengthByProtId)

    val decoyRS: Option[ResultSet] = {
      if (decoyResultSets.length > 0)
        Some(this._mergeResultSets(decoyResultSets, seqLengthByProtId))
      else None
    }

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>

      // Merge decoy result sets if they are defined
      if (decoyResultSets.length > 0) {
        val rsIds = resultSets

        this._storeMergedResultSet(storerContext, msiEzDBC, decoyRS.get, decoyResultSets.map { _.id } distinct)
        // Then store merged decoy result set
        mergedResultSet.decoyResultSet = Some(decoyRS.get)
      }

      // Store merged target result set
      this._storeMergedResultSet(storerContext, msiEzDBC, mergedResultSet, resultSets.map { _.id } distinct)

    }, true) // end of JDBC work

  }

  private def _mergeResultSets(
    resultSets: Seq[ResultSet],
    seqLengthByProtId: Map[Long, Int]): ResultSet = {

    logger.info("merging result sets...")
    val tmpMergedResultSet = rsMergerAlgo.mergeResultSets(resultSets, Some(seqLengthByProtId))
    >>>

    // Map peptide matches and protein matches by their tmp id
    //val mergedPepMatchByTmpId = tmpMergedResultSet.peptideMatches.map { p => p.id -> p } toMap
    //val protMatchByTmpId = tmpMergedResultSet.proteinMatches.map { p => p.id -> p } toMap

    tmpMergedResultSet
  }

  private def _storeMergedResultSet(
    storerContext: StorerContext,
    msiEzDBC: EasyDBC,
    resultSet: ResultSet,
    childrenRSIds: Seq[Long]) {

    logger.info("storing merged result set...")

    val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
    rsStorer.storeResultSet(resultSet, storerContext)

    >>>

    // Link parent result set to its child result sets
    val parentRsId = resultSet.id

    // Insert result set relation between parent and its children
    val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()
    msiEzDBC.executePrepared(rsRelationInsertQuery) { stmt =>
      for (childRsId <- childrenRSIds) stmt.executeWith(parentRsId, childRsId)
    }
    >>>

  }

  private def _buildSeqLength(resultSetIds: Seq[Long], msiDbCtx: DatabaseConnectionContext): Map[Long, Int] = {
    // Retrieve protein ids
    val proteinIdSet = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT bio_sequence_id FROM protein_match " +
          "WHERE bio_sequence_id is not null " +
          "AND result_set_id IN (" + resultSetIds.mkString(",") + ") ")
    })

    // Retrieve sequence length mapped by the corresponding protein id
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    msiDbHelper.getSeqLengthByBioSeqId(proteinIdSet)
  }

}