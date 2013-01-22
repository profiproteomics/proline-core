package fr.proline.core.dal

import java.sql.Connection
import fr.proline.repository.util.{JDBCWork,JDBCReturningWork}
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

class JDBCReturningWorkBuilder[T] {
  
  def withConnection( jdbcWorkFunction: Connection => T ): JDBCReturningWork[T] = {
    new JDBCReturningWork[T]() {
      override def execute(con: Connection): T =  {        
        jdbcWorkFunction(con)
      }
    }
  }
  
  def withEzDBC( driverType: DriverType, jdbcWorkFunction: EasyDBC => T ): JDBCReturningWork[T] = {
    new JDBCReturningWork[T]() {
      override def execute(con: Connection): T = {
        jdbcWorkFunction( ProlineEzDBC(con, driverType) )
      }
    }
  }
  
}
