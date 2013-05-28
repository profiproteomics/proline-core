package fr.proline.core.service.msq.quantify

import java.io.File
import javax.persistence.EntityManager

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.algo.lcms.FeatureMappingParams
import fr.proline.core.algo.lcms.alignment.AlignmentParams
import fr.proline.core.service.lcms.io.IMsQuantConfig
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.repository.IDataStoreConnectorFactory

class Ms1DrivenLabelFreeFeatureQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val quantConfig: ILabelFreeQuantConfig
) extends AbstractLabelFreeFeatureQuantifier {
  
  /*val identRsIdByLcmsMapId = {
    udsQuantChannels.map { qc => qc.getLcmsMapId.intValue -> identRsIdByRsmId(qc.getIdentResultSummaryId) } toMap
  }*/
  
  /*
  val quantChannelIdByLcmsMapId = {
    udsQuantChannels.map { qc => qc.getLcmsMapId.intValue -> qc.getId.intValue } toMap
  }*/
  
  /*
  val lcmsMapIdByIdentRsId = {
    udsQuantChannels.map { qc => identRsIdByRsmId(qc.getIdentResultSummaryId) -> qc.getLcmsMapId.intValue } toMap
  }*/
  
  val lcmsMapSet = {

    val mapSetId = udsMasterQuantChannel.getLcmsMapSetId()

    require(mapSetId > 0, "a LCMS map set must be created first")
    //require Pairs::Lcms::Module::Loader::MapSet
    //val mapSetLoader = new Pairs::Lcms::Module::Loader::MapSet()
    //mapSet = mapSetLoader.getMapSet( mapSetId )

    this.logger.info("loading LCMS map set...")
    val mapSetLoader = new fr.proline.core.om.provider.lcms.impl.SQLMapSetProvider(lcmsDbCtx)
    mapSetLoader.getMapSet(mapSetId)

  }
  
  /*override protected def quantifyMasterChannel(): Unit = {
    
    // TODO: add processings specific to the MS1 driven strategy here
    
    super.quantifyMasterChannel()
  }*/

}