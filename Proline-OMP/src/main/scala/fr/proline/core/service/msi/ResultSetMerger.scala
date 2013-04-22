package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.{ResultSetMerger => ResultSetMergerAlgo}
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

object ResultSetMerger {

  def apply(
    execContext: IExecutionContext,
    resultSetIds: Seq[Int]): ResultSetMerger = {

    val rsProvider = getResultSetProvider(execContext)
    val resultSets = new ArrayBuffer[ResultSet](resultSetIds.size)

    for (rsId <- resultSetIds) {
      val rs = rsProvider.getResultSet(rsId)

      if (rs.isEmpty) {
        throw new IllegalArgumentException("Unknown ResultSet Id: " + rs)
      } else {
        resultSets += rs.get
      }
    }

    new ResultSetMerger(execContext,resultSets)
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
  resultSets: Seq[ResultSet]) extends IService with Logging {

  var mergedResultSet: ResultSet = null
  def mergedResultSetId = if(mergedResultSet != null) mergedResultSet.id else 0
  
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

      DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>
        
        val decoyResultSets = for (rs <- resultSets if rs.decoyResultSet.isDefined) yield rs.decoyResultSet.get
        val allResultSets = resultSets ++ decoyResultSets

        // Retrieve protein ids
        val proteinIdSet = new HashSet[Int]
        for (rs <- allResultSets) {
          val proteinMatches = rs.proteinMatches

          for (proteinMatch <- proteinMatches) {
            val proteinId = proteinMatch.getProteinId
            if (proteinId != 0) proteinIdSet += proteinId
          }
        }

        // Retrieve sequence length mapped by the corresponding protein id
        val msiDbHelper = new MsiDbHelper(msiDbCtx)
        val seqLengthByProtId = msiDbHelper.getSeqLengthByBioSeqId(proteinIdSet)
        >>>
        
        mergedResultSet = this._mergeAndStoreResultSet(storerContext,msiEzDBC,resultSets,seqLengthByProtId)
        
        if( decoyResultSets.length > 0 ) {
          mergedResultSet.decoyResultSet = Some(
            this._mergeAndStoreResultSet(
              storerContext,
              msiEzDBC,
              decoyResultSets,
              seqLengthByProtId
            )
          )
        } 
        
      }, true) // end of JDBC work

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
  
  private def _mergeAndStoreResultSet(
    storerContext: StorerContext,
    msiEzDBC: EasyDBC,    
    resultSets: Seq[ResultSet],
    seqLengthByProtId: Map[Int,Int] ): ResultSet = {

    logger.info("merging result sets...")
    val tmpMergedResultSet = rsMergerAlgo.mergeResultSets(resultSets, Some(seqLengthByProtId))
    >>>

    // Map peptide matches and protein matches by their tmp id
    val mergedPepMatchByTmpId = tmpMergedResultSet.peptideMatches.map { p => p.id -> p } toMap
    val protMatchByTmpId = tmpMergedResultSet.proteinMatches.map { p => p.id -> p } toMap

    logger.info("store result set...")

    //val rsStorer = new JPARsStorer()
    //storerContext = new StorerContext(udsDb, pdiDb, psDb, msiDb)
    //rsStorer.storeResultSet(tmpMergedResultSet, storerContext)
    
    val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
    rsStorer.storeResultSet(tmpMergedResultSet, storerContext)

    //msiDb.getEntityManager.flush() // Flush before returning to SQL msiEzDBC

    >>>

    // Link parent result set to its child result sets
    val parentRsId = tmpMergedResultSet.id
    val rsIds = resultSets.map { _.id } distinct

    // Insert result set relation between parent and its children
    val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()
    msiEzDBC.executePrepared(rsRelationInsertQuery) { stmt =>
      for (childRsId <- rsIds) stmt.executeWith(parentRsId, childRsId)
    }
    >>>

    tmpMergedResultSet
  }

}