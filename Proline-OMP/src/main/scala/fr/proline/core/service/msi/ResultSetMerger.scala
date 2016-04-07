package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.ResultSetAdder
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.algo.msi.AdditionMode

object ResultSetMerger {

  def _loadResultSet(rsId: Long, execContext: IExecutionContext): ResultSet = {
    val rsProvider = getResultSetProvider(execContext)

    val rs = rsProvider.getResultSet(rsId)
    require(rs.isDefined, "Unknown ResultSet Id: " + rsId)

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
  resultSets: Option[Seq[ResultSet]],  
  aggregationMode: Option[AdditionMode.Value] = None
) extends IService with LazyLogging {

  var mergedResultSet: ResultSet = null

  def mergedResultSetId = if (mergedResultSet == null) 0L else mergedResultSet.id

  override protected def beforeInterruption = {
    // Release database connections
    logger.info("Do NOTHING")
    //this.msiDb.closeConnection()

  }

  def runService(): Boolean = {

    var storerContext: StorerContext = null // For JPA use
    var msiDbCtx: DatabaseConnectionContext = null
    var localMSITransaction: Boolean = false
    var msiTransacOk: Boolean = false

    try {
      storerContext = StorerContext(execCtx) // Use Object factory
      msiDbCtx = storerContext.getMSIDbConnectionContext

      // Check if a transaction is already initiated
      if (!msiDbCtx.isInTransaction) {
        msiDbCtx.beginTransaction()
        localMSITransaction = true
        msiTransacOk = false
      }

      if (resultSets.isDefined) {
        logger.info("Start merge from existing ResultSets")
        _mergeFromResultsSets(resultSets.get, aggregationMode, storerContext)
      } else {
        logger.info("Start merge from ResultSet Ids")
        _mergeFromResultsSetIds(resultSetIds.get, aggregationMode, storerContext)
      }

      // Commit transaction if it was initiated locally
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

    beforeInterruption()

    msiTransacOk
  }

  private def _mergeFromResultsSetIds(resultSetIds: Seq[Long], aggregationMode: Option[AdditionMode.Value], storerContext: StorerContext) {
    val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)

    logger.debug("TARGET ResultSet Ids : " + resultSetIds.mkString(" | "))

    val decoyRSIds = msiDbHelper.getDecoyRsIds(resultSetIds)

    logger.debug("DECOY ResultSet Ids : " + decoyRSIds.mkString(" | "))

    val nTargetRS = resultSetIds.length
    val nDecoyRS = decoyRSIds.length

    if (nDecoyRS != nTargetRS) {
      logger.warn("Inconsistent number of TARGET ResultSets: " + nTargetRS + " number of DECOY ResultSets: " + nDecoyRS)
    }

    val distinctRSIds = scala.collection.mutable.Set.empty[Long]

    var targetMergerAlgo: ResultSetAdder = new ResultSetAdder(
      resultSetId = ResultSet.generateNewId,
      isValidatedContent = false,
      isDecoy = false,
      additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
    )

    for (rsId <- resultSetIds) {
      val resultSet = ResultSetMerger._loadResultSet(rsId, execCtx)

      val rsPK = resultSet.id
      if (rsPK > 0L) {
        distinctRSIds += rsPK
      }

      targetMergerAlgo.addResultSet(resultSet)
      //logger.info("Additioner state : " + targetMergerAlgo.mergedProteinMatches.size + " ProMs, " + targetMergerAlgo.peptideById.size + " Peps," + targetMergerAlgo.mergedProteinMatches.map(_.sequenceMatches).flatten.length + " SeqMs")
    }

    mergedResultSet = targetMergerAlgo.toResultSet
    
    if (nDecoyRS > 0) {
      val distinctRSIds = scala.collection.mutable.Set.empty[Long]

      var decoyMergerAlgo: ResultSetAdder = new ResultSetAdder(
        resultSetId = ResultSet.generateNewId,
        isValidatedContent = false,
        isDecoy = true,
        additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
      )

      for (decoyRSId <- decoyRSIds) {
        val decoyRS = ResultSetMerger._loadResultSet(decoyRSId, execCtx)

        val rsPK = decoyRS.id
        if (rsPK > 0L) {
          distinctRSIds += rsPK
        }

        decoyMergerAlgo.addResultSet(decoyRS)
      }

      var decoyRS: ResultSet = decoyMergerAlgo.toResultSet

      decoyMergerAlgo = null // Eligible for Garbage collection      

      DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>
        /* Store merged decoy result set */
        _storeMergedResultSet(storerContext, msiEzDBC, decoyRS, distinctRSIds.toSet)
      }, true) // end of JDBC work

      mergedResultSet.decoyResultSet = Some(decoyRS)

      logger.debug("Merged DECOY ResultSet Id: " + decoyRS.id)
    }

    targetMergerAlgo = null // Eligible for Garbage collection

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>
      /* Store merged target result set */
      _storeMergedResultSet(storerContext, msiEzDBC, mergedResultSet, distinctRSIds.toSet)
    }, true) // end of JDBC work

    logger.debug("Merged TARGET ResultSet Id: " + mergedResultSet.id)
  }

  private def _mergeFromResultsSets(resultSets: Seq[ResultSet], aggregationMode: Option[AdditionMode.Value], storerContext: StorerContext) {

    val decoyResultSets = for (
      rs <- resultSets if ((rs.decoyResultSet != null) && rs.decoyResultSet.isDefined)
    ) yield rs.decoyResultSet.get

    val allResultSets = resultSets ++ decoyResultSets

    // Merge target result sets
    mergedResultSet = _mergeResultSets(resultSets, false, aggregationMode)

    val decoyRS: Option[ResultSet] = if (decoyResultSets.isEmpty) {
      None      
    } else {
      Some(_mergeResultSets(decoyResultSets, true, aggregationMode))
    }

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext, { msiEzDBC =>

      // Merge decoy result sets if they are defined
      if (!decoyResultSets.isEmpty) {
        _storeMergedResultSet(storerContext, msiEzDBC, decoyRS.get, decoyResultSets.map { _.id } toSet)
        // Then store merged decoy result set
        mergedResultSet.decoyResultSet = Some(decoyRS.get)
      }

      // Store merged target result set
      _storeMergedResultSet(storerContext, msiEzDBC, mergedResultSet, resultSets.map { _.id } toSet)

    }, true) // end of JDBC work

  }

  private def _mergeResultSets(
    resultSets: Seq[ResultSet],
    isDecoy: Boolean,
    aggregationMode: Option[AdditionMode.Value]
  ): ResultSet = {
    
    // Check that resultSets have FULL content
    for( resultSet <- resultSets ) {
      require( resultSet.isValidatedContent == false, "use ResultSummaryMerger if you want to deal with validated result sets")
    }

    logger.info("merging result sets...")
    val tmpMergedResultSet = new ResultSetAdder(
      resultSetId = ResultSet.generateNewId,
      isValidatedContent = false,
      isDecoy = isDecoy,
      additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)
    )
    .addResultSets(resultSets)
    .toResultSet()
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
    childrenRSIds: Set[Long]) {

    logger.debug("Storing merged ResultSet ...")

    val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
    rsStorer.storeResultSet(resultSet, storerContext)

    >>>

    if (!childrenRSIds.isEmpty) {
      // Link parent result set to its child result sets
      val parentRSId = resultSet.id

      logger.debug("Linking children ResultSets to parent #" + parentRSId)

      // Insert result set relation between parent and its children
      val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()
      msiEzDBC.executePrepared(rsRelationInsertQuery) { stmt =>
        for (childRsId <- childrenRSIds) stmt.executeWith(parentRSId, childRsId)
      }

      >>>
    }

  }

}
