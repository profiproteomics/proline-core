package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.MapSet

trait IMapSetProvider {
  
  def getMapSet( mapSetId: Int ): MapSet

}