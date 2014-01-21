package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.Ms2CountQuantifier
import fr.proline.core.algo.msq.SpectralCountConfig
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.msi.{ObjectTree => MsiObjectTree}
import fr.proline.core.orm.uds.MasterQuantitationChannel
import javax.persistence.EntityManager

/**
 * @author David Bouyssie
 *
 */
class SpectralCountQuantifier(
  val executionContext: IExecutionContext,
//  val experimentalDesign: ExperimentalDesign,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val scConfig : SpectralCountConfig
  ) extends AbstractMasterQuantChannelQuantifier with Logging {
  
  def quantifyMasterChannel(): Unit = {
    
    // Begin new ORM transaction
    // TODO: handle transactions at db context level
    msiEm.getTransaction().begin()
    udsEm.getTransaction().begin()
    
    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet( msiIdentResultSets )    
    val quantRsId = msiQuantResultSet.getId()
    
    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary( msiQuantResultSet )
    val quantRsmId = msiQuantRSM.getId
    
    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)
    
    // Store master quant result summary
    this.storeMasterQuantResultSummary( this.mergedResultSummary, msiQuantRSM, msiQuantResultSet )
    
    // Compute master quant peptides
    val mqPeptides = Ms2CountQuantifier.computeMasterQuantPeptides(
                       udsMasterQuantChannel,
                       this.mergedResultSummary,
                       this.identResultSummaries
                       )
    
    this.logger.info( "storing master peptide quant data..." )
    
    // Iterate over master quant peptides to store corresponding spectral counts
    for( mqPeptide <- mqPeptides ) {
      
      //val peptideId = mqPeptide.peptideInstance.get.peptideId
      //val masterPepInst = this.masterPepInstByPepId( peptideId )
      //val msiMasterPepInst = this.msiMasterPepInstById(masterPepInst.id)
      val msiMasterPepInst = this.msiMasterPepInstById(mqPeptide.peptideInstance.get.id)      
      this.storeMasterQuantPeptide( mqPeptide, msiQuantRSM, Some(msiMasterPepInst) )
    }
    
    this.logger.info( "storing master proteins set quant data..." )
    
    // Compute master quant protein sets
    val mqProtSets = Ms2CountQuantifier.computeMasterQuantProteinSets(
                        udsMasterQuantChannel,
                        mqPeptides,
                        this.mergedResultSummary,
                        this.identResultSummaries
                      )
                      
    // Iterate over master quant protein sets to store corresponding spectral counts
    for( mqProtSet <- mqProtSets ) {
      val msiMasterProtSet = this.msiMasterProtSetById(mqProtSet.proteinSet.id)
      this.storeMasterQuantProteinSet( mqProtSet, msiMasterProtSet, msiQuantRSM )
    }
    
    // Commit ORM transaction
    msiEm.getTransaction().commit()
    udsEm.getTransaction().commit()
    
  }
  
  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingPeptidesSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.spectral_counting_peptides")
  }
  
  protected def buildMasterQuantPeptideObjectTree( mqPep: MasterQuantPeptide ): MsiObjectTree = {
    
    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = this.quantChannelIds.map { quantPeptideMap.getOrElse(_,null) }
    
    // Store the object tree
    val msiMQPepObjectTree = new MsiObjectTree()
    msiMQPepObjectTree.setSchema( spectralCountingPeptidesSchema )
    msiMQPepObjectTree.setClobData( ProfiJson.serialize(quantPeptides) )   
    
    msiMQPepObjectTree
  }
  
  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingQuantPepIonsSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.spectral_counting_quant_peptide_ions")
  }
  
  protected def buildMasterQuantPeptideIonObjectTree( mqPepIon: MasterQuantPeptideIon ): MsiObjectTree = {
    
    val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
    val quantPeptideIons = this.quantChannelIds.map { quantPeptideIonMap.getOrElse(_,null) }
    
    // Store the object tree
    val msiMQPepIonObjectTree = new MsiObjectTree()
    msiMQPepIonObjectTree.setSchema( spectralCountingQuantPepIonsSchema )
    msiMQPepIonObjectTree.setClobData( ProfiJson.serialize(quantPeptideIons) )
    
    msiMQPepIonObjectTree
  }
    
   protected def getMergedResultSummary(msiDbCtx : DatabaseConnectionContext) : ResultSummary = {
     if(scConfig.parentRSMId.isEmpty)
		 createMergedResultSummary(msiDbCtx)
	 else {
	   this.logger.debug( "Read Merged RSM with ID "+ scConfig.parentRSMId.get)
	   
	   // Instantiate a RSM provider
	   val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx=psDbCtx,udsDbCtx = null)
	   rsmProvider.getResultSummary(scConfig.parentRSMId.get, true).get
	 }
	    
   }

      def getResultAsJSON(): String = {
     return "Not Yet Implemented"
   }
}