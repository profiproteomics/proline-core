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
import fr.proline.core.algo.msi.ResultSetAdditioner
import fr.proline.core.dal.DoJDBCReturningWork

object ResultSetMerger {

  def _loadResultSet(rsId: Long, storerContext: StorerContext): ResultSet = {
    val rsProvider = getResultSetProvider(storerContext)
    val rs = rsProvider.getResultSet(rsId)
    if (rs.isEmpty) throw new IllegalArgumentException("Unknown ResultSet Id: " + rsId)
    rs.get
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSetProvider(execContext: IExecutionContext): IResultSetProvider = {

    if (execContext.isJPA) {
      new ORMResultSetProvider(execContext.getMSIDbConnectionContext, execContext.getPSDbConnectionContext, execContext.getUDSDbConnectionContext)
    } else {
      new SQLResultSetProvider(execContext.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext],
        execContext.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext],
        execContext.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext])
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
        _mergeFromResultsSets(resultSets.get, storerContext, msiDbCtx)
      } else {
        logger.info("Start merge from ResultSet Ids")
        _mergeFromResultsSetIds(resultSetIds.get, storerContext, msiDbCtx, execCtx)
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

  private def _mergeFromResultsSetIds(resultSetIds: Seq[Long], storerContext: StorerContext, msiDbCtx: DatabaseConnectionContext, execCtx: IExecutionContext) {

    var seqLengthByProtId = _buildSeqLength(resultSetIds, msiDbCtx)
    >>>

    var decoyRSIds = new ArrayBuffer[Long]

    var targetMergerAlgo = new ResultSetAdditioner(ResultSet.generateNewId, false, Some(seqLengthByProtId))
    for (rsId <- resultSetIds) {
      var resultSet = _loadResultSet(rsId, execCtx)
      decoyRSIds += resultSet.getDecoyResultSetId
      targetMergerAlgo.addResultSet(resultSet)
      resultSet = null
      System.gc()
      logger.info("Additioner state : "+ targetMergerAlgo.mergedProteinMatches.size +" ProMs, "+targetMergerAlgo.peptideById.size+" Peps,"+targetMergerAlgo.mergedProteinMatches.map(_.sequenceMatches).flatten.length+" SeqMs")
    }

    mergedResultSet = targetMergerAlgo.toResultSet    
    targetMergerAlgo = null
    
    var decoyMergerAlgo: ResultSetAdditioner = null

    if (decoyRSIds.length > 0) {
      seqLengthByProtId = _buildSeqLength(decoyRSIds, msiDbCtx)
      decoyMergerAlgo = new ResultSetAdditioner(ResultSet.generateNewId, true, Some(seqLengthByProtId))
      for (rsId <- decoyRSIds) {
        val resultSet = _loadResultSet(rsId, execCtx)
        decoyMergerAlgo.addResultSet(resultSet)
      }
    }

    

    DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>

      // Merge decoy result sets if they are defined
      if (decoyRSIds.length > 0) {
        val decoyRS = decoyMergerAlgo.toResultSet
        this._storeMergedResultSet(storerContext, msiEzDBC, decoyRS, decoyRSIds)
        // Then store merged decoy result set
        mergedResultSet.decoyResultSet = Some(decoyRS)
      }

      // Store merged target result set
      this._storeMergedResultSet(storerContext, msiEzDBC, mergedResultSet, resultSetIds)

    }, true) // end of JDBC work

  }

  private def _mergeFromResultsSets(resultSets: Seq[ResultSet], storerContext: StorerContext, msiDbCtx: DatabaseConnectionContext) {
    val decoyResultSets = for (
      rs <- resultSets if ((rs.decoyResultSet != null) && rs.decoyResultSet.isDefined)
    ) yield rs.decoyResultSet.get

    val allResultSets = resultSets ++ decoyResultSets

    val seqLengthByProtId = _buildSeqLength(allResultSets.map { _.id }, msiDbCtx)
    >>>

    // Merge target result sets
    mergedResultSet = this._mergeResultSets(resultSets, seqLengthByProtId)

    val decoyRS: Option[ResultSet] = {
      if (decoyResultSets.length > 0)
        Some(this._mergeResultSets(decoyResultSets, seqLengthByProtId))
      else None
    }

    DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>

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

  private def _loadResultSet(rsId: Long, execCtx: IExecutionContext): ResultSet = {
    val udsSQLCtx = execCtx.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext]
    val psSQLCtx = execCtx.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext]
    val msiSQLCtx = execCtx.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext]

    val rsProvider = new SQLResultSetProvider(msiSQLCtx, psSQLCtx, udsSQLCtx)

    // Load target result set
    val targetRsOpt = rsProvider.getResultSet(rsId)
    if (targetRsOpt == None)
      throw new Exception("can't load result set with id = " + rsId)

    targetRsOpt.get
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