package fr.proline.core.om.provider.msq

import fr.proline.core.om.model.msq.ExperimentalDesign

trait IExperimentalDesignProvider {
  
  def getExperimentalDesign( quantitationId:Long ): Option[ExperimentalDesign]
  
}