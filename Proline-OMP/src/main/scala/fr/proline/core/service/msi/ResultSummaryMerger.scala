package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.weiglewilczek.slf4s.Logging
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.api.service.IService
import fr.proline.core.dal.{DatabaseManagement,MsiDb,MsiDbResultSetRelationTable,MsiDbResultSummaryRelationTable}
import fr.proline.core.om.model.msi._
import fr.proline.core.algo.msi.{ ResultSummaryMerger => RsmMergerAlgo }
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.storer.msi.{RsStorer,RsmStorer}

class ResultSummaryMerger( dbManager: DatabaseManagement,
                           projectId: Int,
                           resultSummaries: Seq[ResultSummary] ) extends IService with Logging {
  
  private val msiDbConnector = dbManager.getMSIDatabaseConnector(projectId, false)
  private val msiDb = new MsiDb( MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) )
  var mergedResultSummary: ResultSummary = null
  
  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    this.msiDb.closeConnection()
  }
  
  def runService(): Boolean = {
    
    // Retrieve protein ids
    val proteinIdSet = new HashSet[Int]
    for( rsm <- resultSummaries ) {
      
      val resultSetAsOpt = rsm.resultSet
      require( resultSetAsOpt != None, "the result summary must contain a result set" )
      
      for( proteinMatch <- resultSetAsOpt.get.proteinMatches ) {
        val proteinId = proteinMatch.getProteinId
        if( proteinId != 0 ) proteinIdSet += proteinId
      }
    }
    
    // Retrieve sequence length mapped by the corresponding protein id
    val seqLengthByProtId = new MsiDbHelper( msiDb ).getSeqLengthByBioSeqId(proteinIdSet)
    >>>
    
    // Merge result summaries
    val rsmMerger = new RsmMergerAlgo()
    
    this.logger.info( "merging result summaries..." )
    val tmpMergedResultSummary = rsmMerger.mergeResultSummaries( resultSummaries, seqLengthByProtId )
    >>>
    
    val proteinSets = tmpMergedResultSummary.proteinSets
    this.logger.info( "nb protein sets:" + tmpMergedResultSummary.proteinSets.length )
    
    // Validate all protein sets
    proteinSets.foreach { _.isValidated = true }
    
    // Retrieve the merged result set
    val mergedResultSet = tmpMergedResultSummary.resultSet.get
    val peptideInstances = tmpMergedResultSummary.peptideInstances
    val pepInstanceByPepId = peptideInstances.map { pepInst => pepInst.peptide.id -> pepInst } toMap
    
    // Map peptide matches and proptein matches by their tmp id
    val mergedPepMatchByTmpId = mergedResultSet.peptideMatches.map { p => p.id -> p } toMap
    val protMatchByTmpId = mergedResultSet.proteinMatches.map { p => p.id -> p } toMap
    
    this.logger.info( "store result set..." )
    val rsStorer = RsStorer( dbManager, msiDb )
    rsStorer.storeResultSet( mergedResultSet )
    >>>
    
    // Link parent result set to its child result sets
    val parentRsId = mergedResultSet.id
    val rsIds = resultSummaries.map { _.getResultSetId } distinct
    
    // Insert result set relation between parent and its children
    val rsRelationInsertQuery = MsiDbResultSetRelationTable.makeInsertQuery()
    this.msiDb.getOrCreateTransaction.executeBatch( rsRelationInsertQuery ) { stmt =>
      for( childRsId <- rsIds ) stmt.executeWith( parentRsId, childRsId )
    }
    >>>
    
    // Update peptide match ids referenced in peptide instances
    for( pepInstance <- peptideInstances ) {
      val oldPepMatchIds = pepInstance.peptideMatchIds
      
      val oldPepMatchPropsById = pepInstance.peptideMatchPropertiesById
      
      // Retrieve new pep match ids and re-map peptide match RSM properties with the new ids
      val newPepMatchIds = new ArrayBuffer[Int](pepInstance.getPeptideMatchIds.length)
      val newPepMatchPropsById = new HashMap[Int,PeptideMatchValidationProperties]
      
      for( oldPepMatchId <- oldPepMatchIds ) {
        val newPepMatchId = mergedPepMatchByTmpId(oldPepMatchId).id
        newPepMatchIds += newPepMatchId
        
        if( oldPepMatchPropsById != null ) {
          newPepMatchPropsById += newPepMatchId -> oldPepMatchPropsById(oldPepMatchId)
        }
      }
      
      pepInstance.peptideMatchIds = newPepMatchIds.toArray
      
      if( oldPepMatchPropsById != null )
        pepInstance.peptideMatchPropertiesById = newPepMatchPropsById.toMap
      
    }
    
    // Update protein match ids referenced in peptide sets
    val peptideSets = tmpMergedResultSummary.peptideSets
    for( peptideSet <- peptideSets ) {
      val newProtMatchIds = peptideSet.proteinMatchIds.map { protMatchByTmpId(_).id }
      peptideSet.proteinMatchIds = newProtMatchIds
    }
    
    // Update protein match ids referenced in protein sets
    for( proteinSet <- proteinSets ) {
      val newProtMatchIds = proteinSet.proteinMatchIds.map { protMatchByTmpId(_).id }
      proteinSet.proteinMatchIds = newProtMatchIds
    }
    
    // Store result summary
    this.logger.info( "store result summary..." )    
    RsmStorer( msiDb ).storeResultSummary( tmpMergedResultSummary )
    >>>
    
    // Commit transaction
    this.msiDb.commitTransaction()
    
    this.mergedResultSummary = tmpMergedResultSummary
    
    true
  }
  
}