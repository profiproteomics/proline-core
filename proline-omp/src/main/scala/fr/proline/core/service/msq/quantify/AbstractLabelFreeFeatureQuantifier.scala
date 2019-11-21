package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.algo.msq.config.ILabelFreeQuantConfig
import fr.proline.core.algo.msq.summarizing.LabelFreeEntitiesSummarizer
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{ObjectTreeSchema, ResultSummary => MsiResultSummary}

abstract class AbstractLabelFreeFeatureQuantifier(val quantConfig: ILabelFreeQuantConfig) extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  val lcmsMapSet: MapSet
  
  protected val lcmsDbCtx: LcMsDbConnectionContext = executionContext.getLCMSDbConnectionContext

  protected def quantifyMasterChannel(): Unit = {

    require(udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require(msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet()

    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)
    
    // Build or clone master quant result summary, then store it
    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val rsmDuplicator =  new RsmDuplicator(rsmProvider)
    val quantRSM = rsmDuplicator.cloneAndStoreRSM(mergedResultSummary, msiQuantRSM, msiQuantResultSet, masterQc.identResultSummaryId.isEmpty, msiEm)
    
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
    
    val entitiesSummarizer = if (quantConfig!=null && quantConfig.pepIonSummarizingMethdd.isDefined) {
      new LabelFreeEntitiesSummarizer(
        lcmsMapSet = lcmsMapSet,
        spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber,
        ms2ScanNumbersByFtId = ms2ScanNumbersByFtId,
        quantConfig.pepIonSummarizingMethdd.get)
    } else {
      new LabelFreeEntitiesSummarizer(
        lcmsMapSet = lcmsMapSet,
        spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber,
        ms2ScanNumbersByFtId = ms2ScanNumbersByFtId)
    }
    
    // Compute master quant peptides and master quant protein sets
    val (mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      masterQc,
      quantRSM,
      entityCache.quantChannelResultSummaries
    )
    
    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRSM,mqPeptides,mqProtSets)
  }
  
  protected lazy val quantPeptidesObjectTreeSchema: ObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDES.toString)
  }

  protected lazy val quantPeptideIonsObjectTreeSchema: ObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDE_IONS.toString)
  }
  
}