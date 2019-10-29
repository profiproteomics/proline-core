package fr.proline.core.service.msq.quantify

import fr.proline.context.{DatabaseConnectionContext, IExecutionContext}
import fr.proline.core.algo.msq.config.{AbundanceComputationMethod, AggregationQuantConfig}
import fr.proline.core.algo.msq.summarizing.AggregationEntitiesSummarizer
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq.{ExperimentalDesign, QuantChannel, QuantResultSummary}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{ResultSummary => MsiResultSummary}
import fr.proline.core.orm.uds.{MasterQuantitationChannel, Dataset => UdsDataset}

import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class AggregationQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val quantConfig: AggregationQuantConfig
) extends AbstractMasterQuantChannelQuantifier {

  var identRsms = Array.empty[ResultSummary]
  var childMasterQuantitationChannels = {
    val masterQuantitationChannels = new ArrayBuffer[MasterQuantitationChannel](quantConfig.quantitationIds.length)
    for (childQuantitationId <- quantConfig.quantitationIds) {
      val childQuantitation = udsEm.find(classOf[UdsDataset], childQuantitationId)
      masterQuantitationChannels += childQuantitation.getMasterQuantitationChannels.get(0)
    }
    masterQuantitationChannels.toArray
  }

  protected def quantifyMasterChannel(): Unit = {

    require(udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require(msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set and create corresponding master quant result summary
    // Child RS are RS associated with aggregated dataset if identResultSummary is not provided
    // Child RSM are RSM associated with aggregated dataset if identResultSummary is not provided
    mergedResultSummary.id // Called to init using createMergedResultSummary
    val (childrenRsIds,childrenRsmIds)  = {
      if (masterQc.identResultSummaryId.isEmpty) {
        (identRsms.map(_.getResultSetId), identRsms.map(_.id))
      } else {
        val identRSM = msiEm.find(classOf[MsiResultSummary], masterQc.identResultSummaryId.get)
        (identRSM.getResultSet.getChildren().map(_.getId).toArray, identRSM.getChildren().map(_.getId).toArray)
      }
    }

    val msiQuantResultSet = this.storeMsiQuantResultSet(childrenRsIds.toList)
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet, childrenRsmIds)

    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)

    // Build or clone master quant result summary, then store it
    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val rsmDuplicator =  new RsmDuplicator(rsmProvider)
    val aggregateQuantRSM = rsmDuplicator.cloneAndStoreRSM(mergedResultSummary, msiQuantRSM, msiQuantResultSet, !masterQc.identResultSummaryId.isDefined, msiEm)

    val udsDbHelper = new UdsDbHelper(udsDbCtx)
    val childQuantRSMbychildMQCId = new mutable.HashMap[Long, QuantResultSummary]

    for (udsMasterQuantChannel <- childMasterQuantitationChannels) {
      val quantRsmId = udsMasterQuantChannel.getQuantResultSummaryId
      val qcIds = udsDbHelper.getQuantChannelIds(udsMasterQuantChannel.getId)
      logger.info("Loading the quantitative result summary #" + quantRsmId)
      val quantRsmProvider = new SQLQuantResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
      childQuantRSMbychildMQCId += (udsMasterQuantChannel.getId -> quantRsmProvider.getQuantResultSummary(quantRsmId, qcIds, loadResultSet = true).get)
    }

    val quantChannelsMapping = new mutable.HashMap[Long, QuantChannel]()
    val quantChannelByNumber = masterQc.quantChannels.map(qc => (qc.number, qc)).toMap
    for (qcMapping <- quantConfig.quantChannelsMapping) {
      qcMapping.quantChannelsMatching.foreach{case (childMqcId, childQcId) =>
        quantChannelsMapping += (childQcId -> quantChannelByNumber(qcMapping.quantChannelNumber))
      }
    }

    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRSM, aggregateQuantRSM, childQuantRSMbychildMQCId.toMap, quantChannelsMapping.toMap, quantConfig.intensityComputationMethodName)

    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()

    ()
  }


  protected def computeAndStoreQuantEntities(msiQuantRSM: MsiResultSummary,
                                             aggregateQuantRSM: ResultSummary,
                                             childQuantRSMbychildMQCId: Map[Long, QuantResultSummary],
                                             quantChannelsMapping: Map[Long, QuantChannel],
                                             intensityComputation: AbundanceComputationMethod.Value) {

    val entitiesSummarizer = new AggregationEntitiesSummarizer(childQuantRSMbychildMQCId, quantChannelsMapping, intensityComputation)
    val (mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      masterQc,
      aggregateQuantRSM,
      Array.empty[ResultSummary]
    )

    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRSM,mqPeptides,mqProtSets)
  }

  override protected def createMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    this.logger.info("loading result summaries...")
    val rsmIds =  childMasterQuantitationChannels.map(_.getQuantResultSummaryId.longValue()).toSeq
    identRsms = rsmProvider.getResultSummaries( rsmIds , loadResultSet = true)
    createMergedResultSummary(msiDbCtx, identRsms)
  }

  protected lazy val quantPeptidesObjectTreeSchema = {
    //TODO: retrieve this information from aggregated datasets
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDES.toString)
  }

  protected lazy val quantPeptideIonsObjectTreeSchema = {
    //TODO: retrieve this information from aggregated datasets
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDE_IONS.toString)
  }


}