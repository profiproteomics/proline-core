package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.AdditionMode
import fr.proline.core.algo.msi.ResultSetAdder
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.context._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext

object ResultSetMerger {

  def _loadResultSet(rsId: Long, execContext: IExecutionContext, useJPA: Boolean): ResultSet = {
    val rsProvider = getResultSetProvider(execContext, useJPA)

    val rs = rsProvider.getResultSet(rsId)
    require(rs.isDefined, "Unknown ResultSet Id: " + rsId)

    rs.get
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSetProvider(execContext: IExecutionContext, useJPA: Boolean): IResultSetProvider = {

    if (execContext.isJPA && useJPA) {
      new ORMResultSetProvider(
        execContext.getMSIDbConnectionContext,
        execContext.getPSDbConnectionContext,
        execContext.getUDSDbConnectionContext
      )
    } else {
      new SQLResultSetProvider(
        execContext.getMSIDbConnectionContext,
        execContext.getPSDbConnectionContext,
        execContext.getUDSDbConnectionContext
      )
    }

  }

}

class ResultSetMerger(
  execCtx: IExecutionContext,
  resultSetIds: Option[Seq[Long]],
  resultSets: Option[Seq[ResultSet]],  
  aggregationMode: Option[AdditionMode.Value] = None,
  useJPA: Boolean = false
) extends IService with LazyLogging {

  var mergedResultSet: ResultSet = null

  def mergedResultSetId = if (mergedResultSet == null) 0L else mergedResultSet.id

  override protected def beforeInterruption = {
    logger.info("ResultSetMerger thread is going to be interrupted.")
  }

  def runService(): Boolean = {
    
    val storerContext = StorerContext(execCtx)
    var isMsiDbTxOk: Boolean = false

    try {
      isMsiDbTxOk = storerContext.getMSIDbConnectionContext.tryInTransaction {
        if (resultSets.isDefined) {
          logger.info("Start merge from existing ResultSets")
          _mergeFromResultSets(resultSets.get, aggregationMode, storerContext)
        } else {
          logger.info("Start merge from ResultSet Ids")
          _mergeFromResultSetIds(resultSetIds.get, aggregationMode, storerContext)
        }
      }

    } finally {

      if (storerContext != null) {
        storerContext.clearContext()
      }

    }

    isMsiDbTxOk
  }

  private def _mergeFromResultSetIds(
    resultSetIds: Seq[Long],
    aggregationMode: Option[AdditionMode.Value],
    storerContext: StorerContext
  ) {
    
    val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)
    val decoyRsIds = msiDbHelper.getDecoyRsIds(resultSetIds)
    
    this._mergeAndStoreResultSets(
      resultSetIds,
      decoyRsIds,
      (rsId: Long) => ResultSetMerger._loadResultSet(rsId, execCtx, useJPA),
      aggregationMode,
      storerContext
    )
  }

  private def _mergeFromResultSets(
    resultSets: Seq[ResultSet],
    aggregationMode: Option[AdditionMode.Value],
    storerContext: StorerContext
  ) {
    
    val rsCount = resultSets.length
    val targetRsIds = new ArrayBuffer[Long](rsCount)
    val decoyRsIds = new ArrayBuffer[Long](rsCount)
    val resultSetById = new LongMap[ResultSet](rsCount * 2)
    
    for (rs <- resultSets) {
      
      val targetRsId = rs.id
      targetRsIds += targetRsId
      
      resultSetById.put(targetRsId, rs)
      
      val decoyRsId = rs.getDecoyResultSetId
      if (decoyRsId > 0L) decoyRsIds += decoyRsId
      
      val decoyRsOpt = rs.decoyResultSet

      if (decoyRsOpt.isDefined) {
        resultSetById.put(decoyRsId, decoyRsOpt.get)
      }
      // TODO: should we load the decoy RS automatically ?
      /* else if (decoyRsId > 0L) {
        resultSetById.put(decoyRsId, ResultSetMerger._loadResultSet(decoyRsId, storerContext, useJPA))
      }*/
    }
    
    executeOnProgress() //execute registered action during progress
    
    // Check that resultSets have FULL content
    for (rs <- resultSetById.values) {
    	require( rs.isValidatedContent == false, "use ResultSummaryMerger if you want to deal with validated result sets")
    }
    
    this._mergeAndStoreResultSets(
      targetRsIds,
      decoyRsIds,
      (rsId: Long) => resultSetById(rsId),
      aggregationMode,
      storerContext
    )
  }
  
  private def _mergeAndStoreResultSets(
    targetRsIds: Seq[Long],
    decoyRsIds: Seq[Long],
    resultSetProvider: Long => ResultSet,
    aggregationMode: Option[AdditionMode.Value],
    storerContext: StorerContext
  ) {
    
    logger.debug("TARGET ResultSet Ids: " + targetRsIds.mkString("[",", ","]"))
    logger.debug("DECOY ResultSet Ids: " + decoyRsIds.mkString("[",", ","]"))

    val targetRsCount = targetRsIds.length
    val decoyRsCount = decoyRsIds.length

    if (targetRsCount != decoyRsCount) {
      logger.warn(s"Inconsistent number of TARGET-DECOY ResultSets: $targetRsCount TARGET RS VS $decoyRsCount DECOY RS")
    }

    mergedResultSet = {
      logger.debug("Merging TARGET ResultSets ...")
      
      val targetRsAdder = new ResultSetAdder(
        resultSetId = ResultSet.generateNewId,
        isValidatedContent = false,
        isDecoy = false,
        additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
      )      
      for (rsId <- targetRsIds) {
        targetRsAdder.addResultSet(resultSetProvider(rsId))
        //logger.info("Additioner state : " + targetMergerAlgo.mergedProteinMatches.size + " ProMs, " + targetMergerAlgo.peptideById.size + " Peps," + targetMergerAlgo.mergedProteinMatches.map(_.sequenceMatches).flatten.length + " SeqMs")
      }
      
      
      targetRsAdder.toResultSet
    }
    
    executeOnProgress() //execute registered action during progress
    
    if (decoyRsCount > 0) {
      logger.debug("Merging DECOY ResultSets ...")
      
      val decoyRsAdder = new ResultSetAdder(
        resultSetId = ResultSet.generateNewId,
        isValidatedContent = false,
        isDecoy = true,
        additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
      )
      
      for (decoyRsId <- decoyRsIds) {
        decoyRsAdder.addResultSet(resultSetProvider(decoyRsId))
      }

      val decoyRs = decoyRsAdder.toResultSet
      
      executeOnProgress() //execute registered action during progress
      
      DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext) { msiEzDBC =>
        /* Store merged decoy result set */
        _storeMergedResultSet(storerContext, msiEzDBC, decoyRs, decoyRsIds)
      } // end of JDBC work

      mergedResultSet.decoyResultSet = Some(decoyRs)

      logger.debug("Merged DECOY ResultSet Id: " + decoyRs.id)
    }
    
    executeOnProgress() //execute registered action during progress
    
    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext) { msiEzDBC =>
      /* Store merged target result set */
      _storeMergedResultSet(storerContext, msiEzDBC, mergedResultSet, targetRsIds)
    } // end of JDBC work

    logger.debug("Merged TARGET ResultSet Id: " + mergedResultSet.id)
  }

  private def _storeMergedResultSet(
    storerContext: StorerContext,
    msiEzDBC: EasyDBC,
    resultSet: ResultSet,
    childRsIds: Seq[Long]
  ) {
    
    require( childRsIds.isEmpty == false, "childRsIds is empty")
    
    val distinctRsIds = childRsIds.distinct

    logger.debug("Storing merged ResultSet ...")

    val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext, useJPA)
    rsStorer.storeResultSet(resultSet, storerContext)

    executeOnProgress() //execute registered action during progress

    // Link parent result set to its child result sets
    val parentRSId = resultSet.id

    logger.debug("Linking child ResultSets to their parent #" + parentRSId)

    // Insert result set relation between parent and its children
    val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()
    msiEzDBC.executeInBatch(rsRelationInsertQuery) { stmt =>
      for (childRsId <- distinctRsIds) stmt.executeWith(parentRSId, childRsId)
    }

  }

}
