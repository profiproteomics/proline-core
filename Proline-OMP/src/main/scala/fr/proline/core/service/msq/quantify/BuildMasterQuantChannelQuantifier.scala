package fr.proline.core.service.msq.quantify

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.config._
import fr.proline.core.om.model.msq._
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.msq.quantify._

object BuildMasterQuantChannelQuantifier {
  
  def apply(
    executionContext: IExecutionContext,
    udsMasterQuantChannel: MasterQuantitationChannel,
    experimentalDesign: ExperimentalDesign,
    quantMethod: IQuantMethod,
    quantConfig: IQuantConfig
  ): AbstractMasterQuantChannelQuantifier = {
    
    quantConfig match {
      case isobaricQuantConfig: IsobaricTaggingQuantConfig => {
        new IsobaricTaggingQuantifier(
          executionContext = executionContext,
          udsMasterQuantChannel = udsMasterQuantChannel,
          experimentalDesign = experimentalDesign,
          quantMethod = quantMethod.asInstanceOf[IsobaricTaggingQuantMethod],
          quantConfig = isobaricQuantConfig
        )
      }
      case lfQuantConfig: LabelFreeQuantConfig => {
        new Ms2DrivenLabelFreeFeatureQuantifier(
          executionContext = executionContext,
          experimentalDesign = experimentalDesign,
          udsMasterQuantChannel = udsMasterQuantChannel,
          quantConfig = lfQuantConfig
        )
      }
      case scConfig: SpectralCountConfig => {
        new WeightedSpectralCountQuantifier(
          executionContext = executionContext,
          udsMasterQuantChannel = udsMasterQuantChannel,
          quantConfig = scConfig
        )
      }
      case _ => throw new Exception("The needed quantifier is not yet implemented")
    }

  }

}