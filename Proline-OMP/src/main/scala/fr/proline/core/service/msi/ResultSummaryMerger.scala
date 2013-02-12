package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context._
import fr.proline.core.algo.msi.{ResultSummaryMerger => RsmMergerAlgo}
import fr.proline.core.dal._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.{RsStorer,RsmStorer}
import fr.proline.repository.DriverType

class ResultSummaryMerger(
  execCtx: IExecutionContext,
  resultSummaries: Seq[ResultSummary]
) extends IService with Logging {

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

      msiDbCtx.beginTransaction()
      msiTransacOk = false

      DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>

        // Retrieve protein ids
        val proteinIdSet = new HashSet[Int]
        for (rsm <- resultSummaries) {

          val resultSetAsOpt = rsm.resultSet
          require(resultSetAsOpt != None, "the result summary must contain a result set")

          for (proteinMatch <- resultSetAsOpt.get.proteinMatches) {
            val proteinId = proteinMatch.getProteinId
            if (proteinId != 0) proteinIdSet += proteinId
          }
        }

        // Retrieve sequence length mapped by the corresponding protein id
        val seqLengthByProtId = new MsiDbHelper(msiEzDBC).getSeqLengthByBioSeqId(proteinIdSet)
        >>>

        // Merge result summaries
        val rsmMerger = new RsmMergerAlgo()

        logger.info("merging result summaries...")
        val tmpMergedResultSummary = rsmMerger.mergeResultSummaries(resultSummaries, seqLengthByProtId)
        >>>

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
        val rsStorer = RsStorer( msiDbCtx.getDriverType )
        rsStorer.storeResultSet( mergedResultSet, storerContext )

        //msiDbCtx.getEntityManager.flush() // Flush before returning to SQL msiEzDBC

        >>>

        // Link parent result set to its child result sets
        val parentRsId = mergedResultSet.id
        val rsIds = resultSummaries.map { _.getResultSetId } distinct

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
          val newPepMatchIds = new ArrayBuffer[Int](pepInstance.getPeptideMatchIds.length)
          val newPepMatchPropsById = new HashMap[Int, PeptideMatchResultSummaryProperties]

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
        RsmStorer(execCtx.getMSIDbConnectionContext).storeResultSummary(tmpMergedResultSummary,execCtx)
        >>>

        mergedResultSummary = tmpMergedResultSummary
      }, true) // End of JDBC work

      msiDbCtx.commitTransaction()
      msiTransacOk = true
      
    } finally {

      if (msiDbCtx.isInTransaction() && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          // Rollback is not useful for SQLite and has locking issue
          // http://www.sqlite.org/lang_transaction.html
          if( msiDbCtx.getDriverType() != DriverType.SQLITE )
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

}