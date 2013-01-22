package fr.proline.core.dal

import fr.proline.repository.DatabaseContext
import fr.profi.jdbc.easy.EasyDBC

class SQLContext( val dbContext: DatabaseContext, val ezDBC: EasyDBC ) {
  def close() = dbContext.close()
}