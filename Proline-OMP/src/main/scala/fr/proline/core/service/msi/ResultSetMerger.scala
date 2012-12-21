package fr.proline.core.service.msi

import javax.persistence.EntityTransaction
import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.msi.{ ResultSetMerger => ResultSetMergerAlgo }
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.dal._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.{StorerContext,StorerContextBuilder}
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.repository.DatabaseContext

class ResultSetMerger(
  dbManager: DatabaseManager,
  projectId: Int,
  resultSets: Seq[ResultSet]
) extends IService with Logging {

  var mergedResultSet: ResultSet = null

  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    //this.msiDb.closeConnection()

  }

  def runService(): Boolean = {
    var storerContext: StorerContext = null // For JPA use

    var msiTransaction: EntityTransaction = null
    var msiTransacOk: Boolean = false

    try {
      storerContext = StorerContextBuilder( dbManager, projectId )
      
      val msiDb = storerContext.msiDbContext
      val msiDriverType = msiDb.getDriverType

      msiTransaction = msiDb.getEntityManager.getTransaction
      msiTransaction.begin()
      msiTransacOk = false

      val jdbcWork = JDBCWorkBuilder.withEzDBC( msiDriverType, { msiEzDBC =>

        // Retrieve protein ids
        val proteinIdSet = new HashSet[Int]
        for (rs <- resultSets) {
          val proteinMatches = rs.proteinMatches

          for (proteinMatch <- proteinMatches) {
            val proteinId = proteinMatch.getProteinId
            if (proteinId != 0) proteinIdSet += proteinId
          }
        }

        // Retrieve sequence length mapped by the corresponding protein id
        val msiDbHelper = new MsiDbHelper(msiEzDBC)
        val seqLengthByProtId = msiDbHelper.getSeqLengthByBioSeqId(proteinIdSet)
        >>>

        // Merge result sets
        val rsMergerAlgo = new ResultSetMergerAlgo()

        logger.info("merging result sets...")
        val tmpMergedResultSet = rsMergerAlgo.mergeResultSets(resultSets, seqLengthByProtId)
        >>>

        // Map peptide matches and protein matches by their tmp id
        val mergedPepMatchByTmpId = tmpMergedResultSet.peptideMatches.map { p => p.id -> p } toMap
        val protMatchByTmpId = tmpMergedResultSet.proteinMatches.map { p => p.id -> p } toMap

        logger.info("store result set...")
        
        //val rsStorer = new JPARsStorer()
        //storerContext = new StorerContext(udsDb, pdiDb, psDb, msiDb)
        //rsStorer.storeResultSet(tmpMergedResultSet, storerContext)
        
        val rsStorer = RsStorer( msiDriverType )
        rsStorer.storeResultSet( tmpMergedResultSet, storerContext )
        
        msiDb.getEntityManager.flush() // Flush before returning to SQL msiEzDBC

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

        // Commit transaction if it was initiated locally

        mergedResultSet = tmpMergedResultSet
      })

      msiDb.doWork(jdbcWork, false)

      msiTransaction.commit()
      msiTransacOk = true
    } finally {

      if ((msiTransaction != null) && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          msiTransaction.rollback()
        } catch {
          case ex: Exception => logger.error("Error rollbacking MSI Db Transaction", ex)
        }

      }

      if (storerContext != null) {
        storerContext.closeAll()
      }

    }

    this.beforeInterruption()

    true
  }

}