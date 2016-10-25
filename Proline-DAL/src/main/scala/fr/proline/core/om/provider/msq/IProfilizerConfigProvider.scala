package fr.proline.core.om.provider.msq

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.msq.config.IQuantConfig
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.model.msq.IQuantMethod
import fr.proline.core.algo.msq.config.ProfilizerConfig

trait IProfilizerConfigProvider {
  
  def getProfilizerConfig( quantitationId:Long ): Option[ProfilizerConfig]
  
}