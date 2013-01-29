package fr.proline.core.dal

import fr.proline.context.DatabaseConnectionContext
import fr.profi.jdbc.easy.EasyDBC

class SQLContext( val dbContext: DatabaseConnectionContext, val ezDBC: EasyDBC ) {
  def close() = dbContext.close()
}