package fr.proline.core.dal

import java.sql.Connection
import fr.proline.repository.util.JDBCWork
import fr.proline.repository.DriverType
import fr.profi.jdbc.easy.EasyDBC

object JDBCWorkBuilder {
  
  def withConnection( jdbcWorkFunction: Connection => Unit ): JDBCWork = {
    new JDBCWork() {
      override def execute(con: Connection) {        
        jdbcWorkFunction(con)
      }
    }
  }
  
  def withEzDBC( driverType: DriverType, jdbcWorkFunction: EasyDBC => Unit ): JDBCWork = {
    new JDBCWork() {
      override def execute(con: Connection) {
        jdbcWorkFunction( ProlineEzDBC(con, driverType) )
      }
    }
  }

}