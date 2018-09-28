package fr.proline.core.dal

import java.sql.SQLException

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.ThreadLogger
import fr.proline.context._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.repository._

object BuildLazyExecutionContext extends LazyLogging {

  /**
   * Creates a LazyExecutionContext instance from given IDataStoreConnectorFactory and project Id.
   *
   * @param dsFactory
   *            Factory of Proline DataStore connectors.
   * @param projectId
   *            Id of project to retrieve project specific Dbs (MSI, LCMS).
   * @param useJPA
   *            If <code>true</code> all returned Db contexts wrap a new <code>EntityManager</code> for
   *            relevant Proline Db. If <code>false</code> all returned Db contexts wrap a new SQL JDBC
   *            <code>Connection</code>.
   * @return A new instance of ExecutionContext
   */
  def apply(
    dsFactory: IDataStoreConnectorFactory,
    projectId: Long,
    useJPA: Boolean,
    onConnectionContextClose: Option[IDatabaseConnector => Unit] = None
  ): IExecutionContext = {
    val currentThread = Thread.currentThread

    if (!currentThread.getUncaughtExceptionHandler.isInstanceOf[ThreadLogger]) {
      currentThread.setUncaughtExceptionHandler(new ThreadLogger(logger.underlying.getName()))
    }

    val udsDbCtxBuilder = { () =>
      val udsDbConnector = dsFactory.getUdsDbConnector
      if (udsDbConnector == null) null
      else BuildUdsDbConnectionContext(udsDbConnector, useJPA, onConnectionContextClose)
    }
    
    /* Project specific Dbs */
    val msiDbCtxBuilder = { () =>
      val msiDbConnector = dsFactory.getMsiDbConnector(projectId)
      if (msiDbConnector == null) null
      else BuildMsiDbConnectionContext(msiDbConnector, useJPA, onConnectionContextClose)
    }
    
    val lcMsDbCtxBuilder = { () =>
      val lcMsDbConnector = dsFactory.getLcMsDbConnector(projectId)
      if (lcMsDbConnector == null) null
      else BuildLcMsDbConnectionContext(lcMsDbConnector, useJPA, onConnectionContextClose)
    }

    val lazyContext = new LazyExecutionContext(
      projectId,
      useJPA,
      udsDbCtxBuilder,
      msiDbCtxBuilder,
      lcMsDbCtxBuilder
    )

    new PeptideCacheExecutionContext(lazyContext)
  }

}


object BuildDbConnectionContext extends LazyLogging {
  
  /**
   * Creates a <code>DatabaseConnectionContext</code> from given DatabaseConnector.
   *
   * @param dbConnector
   *            DatabaseConnector used to access a Proline Db.
   * @param useJPA
   *            If <code>true</code> returned Db context wraps a new <code>EntityManager</code>. If
   *            <code>false</code> returned SQLConnectionContext wraps a new SQL JDBC <code>Connection</code>.
   * @return A new instance of <code>DatabaseConnectionContext</code> or <code>SQLConnectionContext</code> for SQL
   */
  def apply(
    dbConnector: IDatabaseConnector,
    useJPA: Boolean,
    onClose: Option[IDatabaseConnector => Unit] = None
  ): DatabaseConnectionContext = {
    
    if (dbConnector == null) {
      throw new IllegalArgumentException("DbConnector is null")
    }
    
    val dbType = dbConnector.getProlineDatabaseType

    val dbCtx = if (useJPA) {
      dbType match {
        case ProlineDatabaseType.LCMS => new LcMsDbConnectionContext(dbConnector)
        case ProlineDatabaseType.MSI => new MsiDbConnectionContext(dbConnector)
        case ProlineDatabaseType.UDS => new UdsDbConnectionContext(dbConnector)
        case _ => new DatabaseConnectionContext(dbConnector)
      }
    } else {

      try {
        val connection = dbConnector.getDataSource.getConnection
        
        dbType match {
          case ProlineDatabaseType.LCMS => new LcMsDbConnectionContext(connection, dbConnector.getDriverType)
          case ProlineDatabaseType.MSI => new MsiDbConnectionContext(connection, dbConnector.getDriverType)
          case ProlineDatabaseType.UDS => new UdsDbConnectionContext(connection, dbConnector.getDriverType)
          case _ => new DatabaseConnectionContext(connection, dbType, dbConnector.getDriverType)
        }
        
      } catch {

        case sqlEx: SQLException => {
          /* Log and re-throw */
          val message = "Unable to obtain SQL JDBC Connection for " + dbConnector.getProlineDatabaseType
          logger.error(message, sqlEx)

          throw new RuntimeException(message, sqlEx)
        }

      }

    } // End if (SQL mode)

    // Set onCloseCallback if provided
    dbCtx.setOnCloseCallback(new Runnable {
      def run() {
        if (onClose.isDefined) onClose.get.apply(dbConnector)
      }
    })
    
    dbCtx
  }

}

object BuildLcMsDbConnectionContext extends LazyLogging {

  def apply(dbConnector: IDatabaseConnector, useJPA: Boolean, onClose: Option[IDatabaseConnector => Unit] = None): LcMsDbConnectionContext = {
    require( dbConnector.getProlineDatabaseType == ProlineDatabaseType.LCMS, "wrong ProlineDatabaseType")
    
    BuildDbConnectionContext(dbConnector, useJPA, onClose).asInstanceOf[LcMsDbConnectionContext]
  }

}

object BuildMsiDbConnectionContext extends LazyLogging {

  def apply(dbConnector: IDatabaseConnector, useJPA: Boolean, onClose: Option[IDatabaseConnector => Unit] = None): MsiDbConnectionContext = {
    require( dbConnector.getProlineDatabaseType == ProlineDatabaseType.MSI, "wrong ProlineDatabaseType")
    
    BuildDbConnectionContext(dbConnector, useJPA, onClose).asInstanceOf[MsiDbConnectionContext]
  }
}



object BuildUdsDbConnectionContext extends LazyLogging {

  def apply(dbConnector: IDatabaseConnector, useJPA: Boolean, onClose: Option[IDatabaseConnector => Unit] = None): UdsDbConnectionContext = {
    require( dbConnector.getProlineDatabaseType == ProlineDatabaseType.UDS, "wrong ProlineDatabaseType")
    
    BuildDbConnectionContext(dbConnector, useJPA, onClose).asInstanceOf[UdsDbConnectionContext]
  }

}

