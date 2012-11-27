package fr.proline.core.service.msi

import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.{DatabaseManagement,MsiDb,MsiDbResultSetRelationTable}
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.api.service.IService
import fr.proline.core.algo.msi.{ ResultSetMerger => ResultSetMergerAlgo }
import fr.proline.core.dal.helper.MsiDbHelper

class ResultSetMerger( dbManager: DatabaseManagement,
                       projectId: Int,
                       resultSets: Seq[ResultSet] ) extends IService with Logging {

  private val msiDbConnector = dbManager.getMSIDatabaseConnector(projectId, false)
  private val msiDb = new MsiDb( MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) )
  var mergedResultSet: ResultSet = null
  
  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    this.msiDb.closeConnection()
  }
  
  def runService(): Boolean = {
    
    // Retrieve protein ids
    val proteinIdSet = new HashSet[Int]
    for( rs <- resultSets ) {
     val proteinMatches = rs.proteinMatches
      
      for( proteinMatch <- proteinMatches ) {
        val proteinId = proteinMatch.getProteinId
        if( proteinId != 0 ) proteinIdSet += proteinId
      }
    }
    
    // Retrieve sequence length mapped by the corresponding protein id
    val msiDbHelper = new MsiDbHelper( msiDb )
    val seqLengthByProtId = msiDbHelper.getSeqLengthByBioSeqId(proteinIdSet)
    >>>
    
    // Merge result sets
    val rsMergerAlgo = new ResultSetMergerAlgo()
    
    this.logger.info( "merging result sets..." )
    val tmpMergedResultSet = rsMergerAlgo.mergeResultSets( resultSets, seqLengthByProtId )
    >>>
    
    // Map peptide matches and protein matches by their tmp id
    val mergedPepMatchByTmpId = tmpMergedResultSet.peptideMatches.map { p => p.id -> p } toMap
    val protMatchByTmpId = tmpMergedResultSet.proteinMatches.map { p => p.id -> p } toMap
        
    this.logger.info( "store result set..." )    
    val rsStorer = RsStorer( dbManager, msiDb )
    rsStorer.storeResultSet( tmpMergedResultSet )
    >>>
    
    // Link parent result set to its child result sets
    val parentRsId = tmpMergedResultSet.id    
    val rsIds = resultSets.map { _.id } distinct
    
    // Insert result set relation between parent and its children
    val rsRelationInsertQuery = MsiDbResultSetRelationTable.makeInsertQuery()
    this.msiDb.getOrCreateTransaction.executeBatch( rsRelationInsertQuery ) { stmt =>
      for( childRsId <- rsIds ) stmt.executeWith( parentRsId, childRsId )
    }
    >>>
    
    this.msiDb.commitTransaction()
    
    this.mergedResultSet = tmpMergedResultSet
    
    this.beforeInterruption()
    
    true
  }
  
}