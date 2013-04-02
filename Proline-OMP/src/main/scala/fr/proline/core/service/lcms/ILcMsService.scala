package fr.proline.core.service.lcms

import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext

trait ILcMsService extends IService {

  val lcmsDbCtx: DatabaseConnectionContext
  
}