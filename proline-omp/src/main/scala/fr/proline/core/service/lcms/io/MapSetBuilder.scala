package fr.proline.core.service.lcms.io

import fr.proline.core.om.model.lcms.MapSet
import fr.proline.context.LcMsDbConnectionContext

trait IMapSetBuilder {
  
  def buildMapSet(lcmsDbCtx: LcMsDbConnectionContext, name: String, runIdByRsName: Map[String, Long]): MapSet
  
}