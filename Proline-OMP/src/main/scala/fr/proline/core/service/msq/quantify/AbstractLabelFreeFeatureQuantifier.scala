package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.algo.msq.summarizing.LabelFreeEntitiesSummarizer
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.{ ResultSummary => MsiResultSummary }
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.RsmDuplicator

abstract class AbstractLabelFreeFeatureQuantifier extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  val experimentalDesign: ExperimentalDesign
  val lcmsMapSet: MapSet
  
  protected val lcmsDbCtx = executionContext.getLCMSDbConnectionContext
  
  protected val masterQc = experimentalDesign.masterQuantChannels.find(_.id == udsMasterQuantChannel.getId).get
    
  protected def quantifyMasterChannel(): Unit = {

    require(udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require(msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet(entityCache.msiIdentResultSets)
    val quantRsId = msiQuantResultSet.getId()

    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)
    
    // get cloned and stored master quant result summary
    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)
    val rsmDuplicator =  new RsmDuplicator(rsmProvider)  
    val quantRSM = if(!isMergedRsmProvided) {        
    	rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet, true, msiEm) 
      } else {       
        rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet, false, msiEm) 
      }
    
    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRSM, quantRSM)
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()

    ()
  }
    
  protected def computeAndStoreQuantEntities(msiQuantRSM: MsiResultSummary, quantRSM: ResultSummary) {
    
    val ms2ScanNumbersByFtId = {
      val rawMapIds = lcmsMapSet.getRawMapIds()
      val lcMsScans = entityCache.getLcMsScans(rawMapIds)
      entityCache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)
    }
    
    val entitiesSummarizer = new LabelFreeEntitiesSummarizer(
      lcmsMapSet = lcmsMapSet,
      spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber,
      ms2ScanNumbersByFtId = ms2ScanNumbersByFtId
    )
    
    // Compute master quant peptides and master quant protein sets
    val (mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      masterQc,
      quantRSM,
      entityCache.identResultSummaries
    )
    
    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRSM,mqPeptides,mqProtSets)
  }
  
  protected lazy val quantPeptidesObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDES.toString())
  }

  protected lazy val quantPeptideIonsObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDE_IONS.toString())
  }
  
  protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
    isMergedRsmProvided = false
    createMergedResultSummary(msiDbCtx)
  }
}