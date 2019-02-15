package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.context._
import fr.proline.core.algo.msq.config._
import fr.proline.core.algo.msq.summarizing.ResidueLabelingEntitiesSummarizer
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{ResultSummary => MsiResultSummary}
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet

import scala.collection.JavaConversions.collectionAsScalaIterable

class ResidueLabelingQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val quantMethod: ResidueLabelingQuantMethod,
  val quantConfig: ResidueLabelingQuantConfig
) extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  private val groupSetupNumber = 1
  private val masterQcExpDesign = experimentalDesign.getMasterQuantChannelExpDesign(udsMasterQuantChannel.getNumber, groupSetupNumber)
  
  private val tagById = quantMethod.tagById

  private val qcByRSMIdAndTagId = {
    val qcsByRsmId = masterQc.quantChannels.groupBy(_.identResultSummaryId)
    for ((rsmId, qcs) <- qcsByRsmId;
         qc <- qcs) yield (rsmId, qc.quantLabelId.get) -> qc
  }

  private val tagByPtmId = {
    val tmptagByPtmId = { for (tag <- quantConfig.tags;
         ptmId <- tag.ptmSpecificityIds) yield (ptmId -> tag.tagId) }
    val mappedTagIds = tmptagByPtmId.map(_._2).toList
    val unmappedTagIds = tagById.keys.filter(!mappedTagIds.contains(_)).toList
    require(unmappedTagIds.length <= 1, "There cannot be more than one tag corresponding to unlabeled peptides")
    (tmptagByPtmId ++ unmappedTagIds.map((-1L,_))).toLongMap()
  }


  protected def quantifyMasterChannel(): Unit = {

    // --- TODO: merge following code with AbstractLabelFreeFeatureQuantifier ---
    // DBO: put in AbstractMasterQuantChannelQuantifier ??? possible conflict with WeightedSpectralCountQuantifier ???

    require( udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require( msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet()
    val quantRsId = msiQuantResultSet.getId

    // Create corresponding master quant result summary
    val msiQuantRsm = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRsm.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)

    // Store master quant result summary

    // Build or clone master quant result summary, then store it
    // TODO: remove code redundancy with the AbstractLabelFreeFeatureQuantifier (maybe add a dedicated method in AbstractMasterQuantChannelQuantifier)
    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val rsmDuplicator =  new RsmDuplicator(rsmProvider)
    val quantRsm = rsmDuplicator.cloneAndStoreRSM(mergedResultSummary, msiQuantRsm, msiQuantResultSet, !masterQc.identResultSummaryId.isDefined, msiEm)
    
    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRsm, quantRsm)
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()
    
    // --- TODO: END OF merge following code with AbstractLabelFreeFeatureQuantifier ---

    ()
  }
  

  
  protected def computeAndStoreQuantEntities(msiQuantRsm: MsiResultSummary, quantRsm : ResultSummary) {
    
    logger.info("computing residue labeling quant entities...")
    
    // Quantify peptide ions if a label free quant config is provided
    val lfqConfig = quantConfig.labelFreeQuantConfig

    logger.info("computing label-free quant entities...")

    // Extract features from mzDB files
    val (pepByRunAndScanNbr, psmByRunAndScanNbr) = entityCache.getPepAndPsmByRunIdAndScanNumber(this.mergedResultSummary)
    val mapSetExtractor = new ExtractMapSet(
      executionContext.getLCMSDbConnectionContext,
      udsMasterQuantChannel.getName,
      entityCache.getSortedLcMsRuns(),
      masterQcExpDesign,
      lfqConfig,
      Some(pepByRunAndScanNbr),
      Some(psmByRunAndScanNbr)
    )
    mapSetExtractor.run()

    // Retrieve some values
    val lcmsMapSet = mapSetExtractor.extractedMapSet
    val rawMapIds = lcmsMapSet.getRawMapIds()
    val lcMsScans = entityCache.getLcMsScans(rawMapIds)
    val spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber
    val ms2ScanNumbersByFtId = entityCache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)


    val entitiesSummarizer = new ResidueLabelingEntitiesSummarizer(
                                            this.qcByRSMIdAndTagId,
                                            this.tagByPtmId,
                                            lcmsMapSet,
                                            spectrumIdByRsIdAndScanNumber,
                                            ms2ScanNumbersByFtId
                                )

    logger.info("summarizing quant entities...")

    val(mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      this.masterQc,
      quantRsm,
      this.entityCache.quantChannelResultSummaries
    )


    val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map( lcmsMap => lcmsMap.runId.get -> lcmsMap.id )
    // Update the LC-MS map id of each master quant channel
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    for( (udsQc,qc) <- udsQuantChannels.zip(this.masterQc.quantChannels) ) {
      val lcMsMapIdOpt = lcMsMapIdByRunId.get( qc.runId.get )
      require( lcMsMapIdOpt.isDefined, "Can't retrieve the LC-MS map id for the run #"+ qc.runId.get)
      qc.lcmsMapId = lcMsMapIdOpt
      udsQc.setLcmsMapId( lcMsMapIdOpt.get )
      udsEm.merge(udsQc)
    }

    // Update the map set id of the master quant channel
    udsMasterQuantChannel.setLcmsMapSetId(lcmsMapSet.id)
    udsEm.merge(udsMasterQuantChannel)

    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRsm,mqPeptides,mqProtSets)
  }
  
  protected lazy val quantPeptidesObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.RESIDUE_LABELING_QUANT_PEPTIDES.toString)
  }

  protected lazy val quantPeptideIonsObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.RESIDUE_LABELING_QUANT_PEPTIDE_IONS.toString)
  }
  
  override protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
    require(masterQc.identResultSummaryId.isDefined, "mergedIdentRsmIdOpt is not defined")
    super.getMergedResultSummary(msiDbCtx)
  }
  
}


