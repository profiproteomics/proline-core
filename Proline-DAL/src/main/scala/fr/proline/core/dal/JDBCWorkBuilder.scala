package fr.proline.core.dal

import java.sql.Connection

import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.DriverType
import fr.proline.repository.util.JDBCReturningWork
import fr.proline.repository.util.JDBCWork

object BuildJDBCWork {
  
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

object DoJDBCWork {
  
  def withConnection( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: Connection => Unit, flushEM: Boolean = false ): Unit = {
    dbCtx.doWork(BuildJDBCWork.withConnection(jdbcWorkFunction), flushEM)
  }
  
  def withEzDBC( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => Unit, flushEM: Boolean = false ): Unit = {
    dbCtx.doWork(BuildJDBCWork.withEzDBC(dbCtx.getDriverType, jdbcWorkFunction),flushEM)
  }

}

object BuildJDBCReturningWork {
  
  def withConnection[T]( jdbcWorkFunction: Connection => T ): JDBCReturningWork[T] = {
    new JDBCReturningWork[T]() {
      override def execute(con: Connection): T =  {        
        jdbcWorkFunction(con)
      }
    }
  }
  
  def withEzDBC[T]( driverType: DriverType, jdbcWorkFunction: EasyDBC => T ): JDBCReturningWork[T] = {
    new JDBCReturningWork[T]() {
      override def execute(con: Connection): T = {
        jdbcWorkFunction( ProlineEzDBC(con, driverType) )
      }
    }
  }
  
}

object DoJDBCReturningWork {
  
  def withConnection[T]( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: Connection => T, flushEM: Boolean = false ): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withConnection(jdbcWorkFunction),flushEM)
  }
  
  def withEzDBC[T]( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => T, flushEM: Boolean = false ): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withEzDBC(dbCtx.getDriverType, jdbcWorkFunction),flushEM)
  }
}
