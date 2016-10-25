package fr.proline.core.om.provider.msq

import fr.proline.core.algo.msq.config.IQuantConfig
import fr.proline.core.om.model.msq.IQuantMethod

trait IQuantConfigProvider {
  
  def getQuantConfigAndMethod( quantitationId:Long ): Option[(IQuantConfig,IQuantMethod)]
  
}