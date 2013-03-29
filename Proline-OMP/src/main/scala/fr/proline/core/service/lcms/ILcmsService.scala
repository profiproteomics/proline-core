package fr.proline.core.service.lcms

import fr.proline.api.service.IService
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.repository.IDatabaseConnector

trait ILcmsService extends IService {

  val lcmsDbConnector: IDatabaseConnector
  val lcmsQueryHelper = new SQLQueryHelper(lcmsDbConnector)
  val ezDBC = lcmsQueryHelper.ezDBC
  val inExprLimit = ezDBC.getInExpressionCountLimit
  
}