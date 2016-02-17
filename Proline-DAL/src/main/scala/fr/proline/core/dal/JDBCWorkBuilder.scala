package fr.proline.core.dal

import java.sql.Connection

import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.context._
import fr.proline.repository.DriverType
import fr.proline.repository.util.JDBCReturningWork
import fr.proline.repository.util.JDBCWork

protected object BuildJDBCWork {
  
  def withConnection( jdbcWorkFunction: Connection => Unit ): JDBCWork = {
    new JDBCWork() {
      override def execute(connection: Connection) {
        jdbcWorkFunction(connection)
      }
    }
  }
  
  def withEzDBC( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => Unit ): JDBCWork = {
    new JDBCWork() {
      override def execute(connection: Connection) {
        
        // Set auto-commit to true if we are not inside a transaction
        if (dbCtx.isInTransaction() == false) {
          connection.setAutoCommit(true)
        }
        
        // Execute the jdbcWorkFunction
        jdbcWorkFunction( ProlineEzDBC(connection, dbCtx.getDriverType) )
      }
    }
  }

}

object DoJDBCWork {
  
  @deprecated("0.6.0","please use other method signature")
  def withConnection( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: Connection => Unit, flushEM: Boolean ): Unit = {
    dbCtx.doWork(BuildJDBCWork.withConnection(jdbcWorkFunction), flushEM)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def withEzDBC( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => Unit, flushEM: Boolean ): Unit = {
    dbCtx.doWork(BuildJDBCWork.withEzDBC(dbCtx, jdbcWorkFunction),flushEM)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def tryTransactionWithEzDBC( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => Unit, flushEM: Boolean ): Unit = {
    dbCtx.tryInTransaction(this.withEzDBC(dbCtx,jdbcWorkFunction,flushEM))
  }
  
  @deprecated("0.6.0","please use other method signature")
  def withConnection( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: Connection => Unit ): Unit = {
    dbCtx.doWork(BuildJDBCWork.withConnection(jdbcWorkFunction), false)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def withEzDBC( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => Unit): Unit = {
    dbCtx.doWork(BuildJDBCWork.withEzDBC(dbCtx, jdbcWorkFunction),false)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def tryTransactionWithEzDBC( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => Unit ): Unit = {
    dbCtx.tryInTransaction(this.withEzDBC(dbCtx,jdbcWorkFunction,false))
  }
  
  def withConnection( dbCtx: DatabaseConnectionContext, flushEM: Boolean = false )(jdbcWorkFunction: Connection => Unit) : Unit = {
    dbCtx.doWork(BuildJDBCWork.withConnection(jdbcWorkFunction), flushEM)
  }
  
  def withEzDBC( dbCtx: DatabaseConnectionContext, flushEM: Boolean = false )(jdbcWorkFunction: EasyDBC => Unit): Unit = {
    dbCtx.doWork(BuildJDBCWork.withEzDBC(dbCtx, jdbcWorkFunction),flushEM)
  }
  
  def tryTransactionWithEzDBC( dbCtx: DatabaseConnectionContext, flushEM: Boolean = false )(jdbcWorkFunction: EasyDBC => Unit): Unit = {
    dbCtx.tryInTransaction(this.withEzDBC(dbCtx,jdbcWorkFunction,flushEM))
  }

}

protected object BuildJDBCReturningWork {
  
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
  
  @deprecated("0.6.0","please use other method signature")
  def withConnection[T]( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: Connection => T, flushEM: Boolean ): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withConnection(jdbcWorkFunction),flushEM)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def withEzDBC[T]( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => T, flushEM: Boolean ): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withEzDBC(dbCtx.getDriverType, jdbcWorkFunction),flushEM)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def withConnection[T]( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: Connection => T ): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withConnection(jdbcWorkFunction), false)
  }
  
  @deprecated("0.6.0","please use other method signature")
  def withEzDBC[T]( dbCtx: DatabaseConnectionContext, jdbcWorkFunction: EasyDBC => T ): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withEzDBC(dbCtx.getDriverType, jdbcWorkFunction), false)
  }
  
  def withConnection[T]( dbCtx: DatabaseConnectionContext, flushEM: Boolean = false )(jdbcWorkFunction: Connection => T): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withConnection(jdbcWorkFunction),flushEM)
  }
  
  def withEzDBC[T]( dbCtx: DatabaseConnectionContext, flushEM: Boolean = false )(jdbcWorkFunction: EasyDBC => T): T = {
    dbCtx.doReturningWork(BuildJDBCReturningWork.withEzDBC(dbCtx.getDriverType, jdbcWorkFunction),flushEM)
  }
}
