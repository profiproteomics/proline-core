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
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.core.service.msq.IQuantifier
import fr.proline.repository.IDatabaseConnector

/**
 * @author David Bouyssie
 *
 */
class SpectralCountQuantifier(
        val dbManager: DatabaseManager,
        val udsEm: EntityManager,
        val udsMasterQuantChannel: MasterQuantitationChannel
        ) extends IQuantifier with Logging {
  
  def quantifyFraction(): Unit = {
    
    // Begin new ORM transaction
    msiEm.getTransaction().begin()
    udsEm.getTransaction().begin()
    
    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet( msiIdentResultSets )    
    val quantRsId = msiQuantResultSet.getId()
    
    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary( msiQuantResultSet )
    val quantRsmId = msiQuantRSM.getId
    
    // Update quant result summary id of the quantitation fraction
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
      this._storeMasterQuantPeptide( mqPeptide, msiMasterPepInst, msiQuantRSM )
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
      this._storeMasterQuantProteinSet( mqProtSet, msiMasterProtSet, msiQuantRSM )
    }
    
    // Commit ORM transaction
    msiEm.getTransaction().commit()
    udsEm.getTransaction().commit()
    
  }
  
  protected def _storeMasterQuantPeptide( mqPep: MasterQuantPeptide,
                                          msiMasterPepInst: MsiPeptideInstance,
                                          msiRSM: MsiResultSummary ) = {
    
    val schemaName = "object_tree.spectral_counting_peptides"
    // TODO: load the schema
    val schemaFake = new fr.proline.core.orm.msi.ObjectTreeSchema()
    schemaFake.setName( schemaName )
    schemaFake.setType( "JSON" )
    schemaFake.setVersion( "0.1" )
    schemaFake.setSchema( "" )
    
    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = this.quantChannelIds.map { quantPeptideMap.getOrElse(_,null) }
    
    // Store the object tree
    val msiMQCObjectTree = new MsiObjectTree()
    msiMQCObjectTree.setSchema( schemaFake )
    msiMQCObjectTree.setSerializedData( generate[Array[QuantPeptide]](quantPeptides) )      
    this.msiEm.persist(msiMQCObjectTree)
  
    // Store master quant component
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqPep.selectionLevel)
    if( mqPep.properties != None ) msiMQC.setSerializedProperties( generate(mqPep.properties) )
    msiMQC.setObjectTreeId(msiMQCObjectTree.getId)
    msiMQC.setSchemaName(schemaName)
    msiMQC.setResultSummary(msiRSM)
    
    this.msiEm.persist(msiMQC)
    
    // Link master quant peptide to the corresponding master quant component
    msiMasterPepInst.setMasterQuantComponentId( msiMQC.getId )
    this.msiEm.persist(msiMasterPepInst)
    
  }
  
  protected def _storeMasterQuantProteinSet( mqProtSet: MasterQuantProteinSet,
                                             msiMasterProtSet: MsiProteinSet,
                                             msiRSM: MsiResultSummary ) = {
    
    val schemaName = "object_tree.quant_protein_sets"
    // TODO: load the schema
    val schemaFake = new fr.proline.core.orm.msi.ObjectTreeSchema()
    schemaFake.setName( schemaName )
    schemaFake.setType( "JSON" )
    schemaFake.setVersion( "0.1" )
    schemaFake.setSchema( "" )
    
    val quantProtSetMap = mqProtSet.quantProteinSetMap
    val quantProtSets = this.quantChannelIds.map { quantProtSetMap.getOrElse(_,null) }
    
    // Store the object tree
    val msiMQCObjectTree = new MsiObjectTree()
    msiMQCObjectTree.setSchema( schemaFake )
    msiMQCObjectTree.setSerializedData( generate[Array[QuantProteinSet]](quantProtSets) )      
    this.msiEm.persist(msiMQCObjectTree)
    
    // Store master quant component
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqProtSet.selectionLevel)
    if( mqProtSet.properties != None ) msiMQC.setSerializedProperties( generate(mqProtSet.properties) )
    msiMQC.setObjectTreeId(msiMQCObjectTree.getId)
    msiMQC.setSchemaName(schemaName)
    msiMQC.setResultSummary(msiRSM)    
    this.msiEm.persist(msiMQC)
    
    // Link master quant protein set to the corresponding master quant component
    msiMasterProtSet.setMasterQuantComponentId( msiMQC.getId )
    
  }
  
}