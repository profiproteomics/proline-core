package fr.proline.core.service.msq.quantify

import java.io.File
import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.algo.lcms.FeatureMappingParams
import fr.proline.core.algo.lcms.LabelFreeQuantConfig
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.repository.IDataStoreConnectorFactory

class Ms2DrivenLabelFreeFeatureQuantifier(
  val executionContext: IExecutionContext,
  val experimentalDesign: ExperimentalDesign,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val quantConfig: LabelFreeQuantConfig
) extends AbstractLabelFreeFeatureQuantifier {
  
  // Extract the LC-MS map set
  lazy val lcmsMapSet: MapSet = {
    
    // TODO: provide the ident RSM to the ExtractMapSet algo
    //val identResultSummaries = this.identResultSummaries
    
    val mapSetExtractor = new ExtractMapSet(this.lcmsDbCtx,quantConfig)
    mapSetExtractor.run()
    mapSetExtractor.extractedMapSet
  }
  
  override protected def quantifyMasterChannel(): Unit = {   
    
    // Add processings specific to the MS2 driven strategy here
    val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map( lcmsMap => lcmsMap.runId.get -> lcmsMap.id )
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    for( udsQuantChannel <- udsQuantChannels) {
      udsQuantChannel.setLcmsMapId( lcMsMapIdByRunId( udsQuantChannel.getRun().getId ) )
    }
    
    super.quantifyMasterChannel()
  }

}