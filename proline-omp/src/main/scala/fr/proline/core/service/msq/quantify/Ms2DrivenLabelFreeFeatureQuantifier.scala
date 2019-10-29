package fr.proline.core.service.msq.quantify

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.config.{ILabelFreeQuantConfig, LabelFreeQuantConfig}
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet

import scala.collection.JavaConversions.collectionAsScalaIterable


class Ms2DrivenLabelFreeFeatureQuantifier(
   val executionContext: IExecutionContext,
   val udsMasterQuantChannel: MasterQuantitationChannel,
   val experimentalDesign: ExperimentalDesign,
   override val quantConfig: ILabelFreeQuantConfig
) extends AbstractLabelFreeFeatureQuantifier(quantConfig) {
  
  private val groupSetupNumber = 1
  private val masterQcExpDesign = experimentalDesign.getMasterQuantChannelExpDesign(udsMasterQuantChannel.getNumber, groupSetupNumber)
  
  // Extract the LC-MS map set
  lazy val lcmsMapSet: MapSet = {
    
    val (pepByRunAndScanNbr, psmByRunAndScanNbr) = entityCache.getPepAndPsmByRunIdAndScanNumber(this.mergedResultSummary)
    val mapSetExtractor = new ExtractMapSet(
      this.lcmsDbCtx,
      this.udsMasterQuantChannel.getName,
      this.entityCache.getSortedLcMsRuns(),
      masterQcExpDesign,
      quantConfig,
      Some(pepByRunAndScanNbr),
      Some(psmByRunAndScanNbr)
    )
    mapSetExtractor.run()
    mapSetExtractor.extractedMapSet
  }
  
  
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

}