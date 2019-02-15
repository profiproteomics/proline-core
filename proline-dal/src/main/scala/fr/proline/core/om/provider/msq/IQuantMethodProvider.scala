package fr.proline.core.om.provider.msq

import fr.proline.core.om.model.msq.IQuantMethod

trait IQuantMethodProvider {
  
  def getQuantMethod( quantMethodId:Long ): Option[IQuantMethod]
  
  def getQuantitationQuantMethod( quantitationId:Long ): Option[IQuantMethod]
  
}