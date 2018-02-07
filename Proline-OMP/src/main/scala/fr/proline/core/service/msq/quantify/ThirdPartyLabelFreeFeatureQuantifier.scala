package fr.proline.core.service.msq.quantify

import scala.collection.JavaConversions.collectionAsScalaIterable

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.IExecutionContext
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.algo.msq.config.LabelFreeQuantConfig
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.model.msq.MasterQuantChannelProperties
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.ReadBackRsmDuplicator
import fr.proline.core.om.storer.msi.impl.ResetIdsRsmDuplicator
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.orm.msi.{ ResultSet => MsiResultSet }
import fr.proline.core.orm.msi.{ ResultSummary => MsiResultSummary }
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.core.service.lcms.io.IMapSetBuilder



class ThirdPartyLabelFreeFeatureQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val mapSetBuilder: IMapSetBuilder
) extends AbstractLabelFreeFeatureQuantifier {
  
  private val groupSetupNumber = 1
  private val masterQcExpDesign = experimentalDesign.getMasterQuantChannelExpDesign(udsMasterQuantChannel.getNumber, groupSetupNumber)
  
  val lcmsMapSet = mapSetBuilder.buildMapSet(executionContext.getLCMSDbConnectionContext, udsMasterQuantChannel.getName, masterQc.quantChannels.map(qc => qc.name -> qc.runId.get).toMap)
  
    
  // Add processings specific to the MS2 driven strategy here
  override protected def quantifyMasterChannel(): Unit = {
        
    // Retrieve LC-MS maps ids mapped by the run id
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
    
    super.quantifyMasterChannel()
  }
  
  override protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
    if (masterQc.identResultSummaryId.isEmpty) {
      isMergedRsmProvided = false
      createMergedResultSummary(msiDbCtx)
    } else {
      isMergedRsmProvided = true
      
      val pRsmId = masterQc.identResultSummaryId.get
          
      this.logger.debug("Read Merged RSM with ID " + pRsmId)

      // Instantiate a Lazy RSM provider
      val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx = psDbCtx, udsDbCtx = udsDbCtx)
      val identRsmOpt = rsmProvider.getResultSummary(pRsmId, true)
        
      require( identRsmOpt.isDefined, "can't load the result summary with id=" + pRsmId)
      
      // Update Master Quant Channel properties
      val mqchProperties = new MasterQuantChannelProperties(
        identResultSummaryId = masterQc.identResultSummaryId,
        identDatasetId = masterQc.identDatasetId,
        spectralCountProperties = None
      )
      udsMasterQuantChannel.setSerializedProperties(ProfiJson.serialize(mqchProperties))
      
      identRsmOpt.get
    }
  }

}