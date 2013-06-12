package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.MapAlignmentSet

trait IMapAlignmentSetProvider {
  
  def getMapAlignmentSets( mapSetId: Long ): Array[MapAlignmentSet]

}