package fr.proline.core.service.msq.quantify

import fr.proline.context.{DatabaseConnectionContext, IExecutionContext}
import fr.proline.core.algo.msq.config.{AbundanceComputationMethod, AggregationQuantConfig}
import fr.proline.core.algo.msq.summarizing.AggregationEntitiesSummarizer
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msi.{PeptideMatch, ResultSummary}
import fr.proline.core.om.model.msq.{ExperimentalDesign, QuantChannel, QuantResultSummary}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.om.util.PepMatchPropertiesUtil
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{ResultSummary => MsiResultSummary}
import fr.proline.core.orm.uds.{MasterQuantitationChannel, QuantitationMethod, Dataset => UdsDataset}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class AggregationQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val quantConfig: AggregationQuantConfig
) extends AbstractMasterQuantChannelQuantifier {

  var identRsms = Array.empty[ResultSummary]
  private var childQuantMethodType: Option[QuantitationMethod.Type] = None
  private var childMasterQuantitationChannels: Array[MasterQuantitationChannel] = Array.empty[MasterQuantitationChannel]

  private def initChildMQuantChannels(): Unit = {
    var isSameChildMethodType: Boolean = true
    val masterQuantitationChannels = new ArrayBuffer[MasterQuantitationChannel](quantConfig.quantitationIds.length)
    for (childQuantitationId <- quantConfig.quantitationIds) {
      val childQuantitation = udsEm.find(classOf[UdsDataset], childQuantitationId)
      masterQuantitationChannels += childQuantitation.getMasterQuantitationChannels.get(0)
    }
    childMasterQuantitationChannels = masterQuantitationChannels.toArray
    val qMethodTypes =  childMasterQuantitationChannels.map(mqCh=> mqCh.getDataset.getMethod.getType).distinct.map(mType => QuantitationMethod.Type.findType(mType) )
    isSameChildMethodType = qMethodTypes.size == 1
    childQuantMethodType = if(isSameChildMethodType) Some(qMethodTypes(0)) else None
    logger.debug("  --TEST Agg define childQMethodType:  isSameChildMethodType ? "+isSameChildMethodType+" childQuantMethodType exist "+childQuantMethodType.isDefined)
    if(childQuantMethodType.isDefined)
      logger.debug("  --TEST Agg define childQMethodType: childQuantMethodType = " + childQuantMethodType.get.name())
//    for (childQuantitation <- childMasterQuantitationChannels) {
//      if (isSameChildMethodType && childQuantMethodType.isEmpty) {
//        val qType : QuantitationMethod.Type = QuantitationMethod.Type.valueOf(childQuantitation.getDataset.getMethod.getType)
//        childQuantMethodType = Some(qType )//init method with first child
//      } else if(isSameChildMethodType) {
//        val currentMethod = QuantitationMethod.Type.valueOf(childQuantitation.getDataset.getMethod.getType)
//        if (!currentMethod.equals(childQuantMethodType.get)) {
//          isSameChildMethodType = false
//          childQuantMethodType = None
//        }
//      }
//    }
  }

  protected def quantifyMasterChannel(): Unit = {

    require(udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require(msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")
    initChildMQuantChannels()

    // Store the master quant result set and create corresponding master quant result summary
    // Child RS are RS associated with aggregated dataset if identResultSummary is not provided
    // Child RSM are RSM associated with aggregated dataset if identResultSummary is not provided
    mergedResultSummary.id // Called to init using createMergedResultSummary
    val (childrenRsIds,childrenRsmIds)  = {
      if (masterQc.identResultSummaryId.isEmpty) {
        (identRsms.map(_.getResultSetId), identRsms.map(_.id))
      } else {
        val identRSM = msiEm.find(classOf[MsiResultSummary], masterQc.identResultSummaryId.get)
        (identRSM.getResultSet.getChildren.asScala.map(_.getId).toArray, identRSM.getChildren.asScala.map(_.getId).toArray)
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
    val aggregateQuantRSM = rsmDuplicator.cloneAndStoreRSM(mergedResultSummary, msiQuantRSM, msiQuantResultSet, masterQc.identResultSummaryId.isEmpty, msiEm)

    val udsDbHelper = new UdsDbHelper(udsDbCtx)
    val childQuantRSMbychildMQCId = new mutable.HashMap[Long, QuantResultSummary]
    val isIsobaric = childQuantMethodType.isDefined && childQuantMethodType.get.equals(QuantitationMethod.Type.ISOBARIC_TAGGING)
    logger.info("  --TEST Agg : isIsobaric ? "+isIsobaric)
    for (udsMasterQuantChannel <- childMasterQuantitationChannels) {
      val quantRsmId = udsMasterQuantChannel.getQuantResultSummaryId
      val qcIds = udsDbHelper.getQuantChannelIds(udsMasterQuantChannel.getId)
      logger.debug("Loading the quantitative result summary to aggregate #" + quantRsmId)
      val quantRsmProvider = new SQLQuantResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
      childQuantRSMbychildMQCId += (udsMasterQuantChannel.getId -> quantRsmProvider.getQuantResultSummary(quantRsmId, qcIds, loadResultSet = true,loadReporterIons = Some(isIsobaric) ).get)
    }

    //Test if need & can Read PIF values
    val start = System.currentTimeMillis()
    if(isIsobaric) { //Read PIF only for  Isobaric tag quantitation
      val peptdeMatchBySpecId: mutable.LongMap[PeptideMatch] = mutable.LongMap[PeptideMatch]()
      aggregateQuantRSM.resultSet.get.peptideMatches.foreach(pepM => {
        peptdeMatchBySpecId += (pepM.getMs2Query().spectrumId -> pepM)
      })

      val nbPepMatchModified = PepMatchPropertiesUtil.readPIFValuesForResultSummary(peptdeMatchBySpecId, aggregateQuantRSM, udsDbCtx, msiDbCtx)
      val end = System.currentTimeMillis()
      logger.info(" ------ READ PIF for " + nbPepMatchModified+ " from"+  peptdeMatchBySpecId.size + " pepMatches in " + (end - start) + "ms")
    }

    val childQChIdToparentQCh = new mutable.HashMap[Long, QuantChannel]()
    val quantChannelByNumber = masterQc.quantChannels.map(qc => (qc.number, qc)).toMap
    for (qcMapping <- quantConfig.quantChannelsMapping) {
      qcMapping.quantChannelsMatching.foreach{case (childMqcId, childQcId) =>
        childQChIdToparentQCh += (childQcId -> quantChannelByNumber(qcMapping.quantChannelNumber))
      }
    }

    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRSM, aggregateQuantRSM, childQuantRSMbychildMQCId.toMap, childQChIdToparentQCh.toMap, quantConfig.intensityComputationMethodName, isIsobaric)

    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()

    ()
  }


  protected def computeAndStoreQuantEntities(msiQuantRSM: MsiResultSummary,
                                             aggregateQuantRSM: ResultSummary,
                                             childQuantRSMbychildMQCId: Map[Long, QuantResultSummary],
                                             childQChIdToParentQCh: Map[Long, QuantChannel],
                                             intensityComputation: AbundanceComputationMethod.Value,
                                             isIsobaric : Boolean): Unit = {

    val entitiesSummarizer = new AggregationEntitiesSummarizer(childQuantRSMbychildMQCId, childQChIdToParentQCh, intensityComputation, isIsobaric)
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