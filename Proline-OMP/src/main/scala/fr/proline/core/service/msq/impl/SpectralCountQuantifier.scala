package fr.proline.core.service.msq.impl

import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import javax.persistence.EntityManager
import collection.JavaConversions.{collectionAsScalaIterable}
import collection.mutable.{ArrayBuffer,HashMap}
import fr.proline.core.algo.msi.ResultSummaryMerger
import fr.proline.core.algo.msq.Ms2CountQuantifier
import fr.proline.core.om.model.msi.{PeptideInstance,PeptideMatch,ResultSummary}
import fr.proline.core.om.model.msq.{MasterQuantPeptide,MasterQuantProteinSet,QuantPeptide,QuantProteinSet}
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.msi.{MasterQuantPeptideIon => MsiMasterQuantPepIon,
                                MasterQuantComponent => MsiMasterQuantComponent,
                                ObjectTree => MsiObjectTree,
                                PeptideInstance => MsiPeptideInstance,
                                ProteinSet => MsiProteinSet,
                                ResultSummary => MsiResultSummary
                                }
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.core.service.msq.IQuantifier
import fr.proline.repository.IDatabaseConnector
import fr.proline.core.om.model.msq.MasterQuantPeptideIon
import fr.proline.core.om.model.msq.QuantPeptideIon

/**
 * @author David Bouyssie
 *
 */
class SpectralCountQuantifier(
        val dbManager: IDataStoreConnectorFactory,
        val udsEm: EntityManager,
        val udsMasterQuantChannel: MasterQuantitationChannel
        ) extends IQuantifier with Logging {
  
  def quantifyMasterChannel(): Unit = {
    
    // Begin new ORM transaction
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
    this.loadObjectTreeSchema("object_tree.spectral_counting_peptides")
  }
  
  protected def buildMasterQuantPeptideObjectTree( mqPep: MasterQuantPeptide ): MsiObjectTree = {
    
    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = this.quantChannelIds.map { quantPeptideMap.getOrElse(_,null) }
    
    // Store the object tree
    val msiMQPepObjectTree = new MsiObjectTree()
    msiMQPepObjectTree.setSchema( spectralCountingPeptidesSchema )
    msiMQPepObjectTree.setSerializedData( generate[Array[QuantPeptide]](quantPeptides) )   
    
    msiMQPepObjectTree
  }
  
  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingQuantPepIonsSchema = {
    this.loadObjectTreeSchema("object_tree.spectral_counting_quant_peptide_ions")
  }
  
  protected def buildMasterQuantPeptideIonObjectTree( mqPepIon: MasterQuantPeptideIon ): MsiObjectTree = {
    
    val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
    val quantPeptideIons = this.quantChannelIds.map { quantPeptideIonMap.getOrElse(_,null) }
    
    // Store the object tree
    val msiMQPepIonObjectTree = new MsiObjectTree()
    msiMQPepIonObjectTree.setSchema( spectralCountingQuantPepIonsSchema )
    msiMQPepIonObjectTree.setSerializedData( generate[Array[QuantPeptideIon]](quantPeptideIons) )          
    
    msiMQPepIonObjectTree
  }
  
}