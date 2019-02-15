package fr.proline.core.om.provider.msq

import fr.proline.core.algo.msq.config.profilizer.ProfilizerConfig

trait IProfilizerConfigProvider {
  
  def getProfilizerConfig( quantitationId:Long ): Option[ProfilizerConfig]
  
}