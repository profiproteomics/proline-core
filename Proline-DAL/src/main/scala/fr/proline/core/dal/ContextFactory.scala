package fr.proline.core.dal

import java.sql.SQLException

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.ThreadLogger
import fr.proline.context._
import fr.proline.repository._

object ContextFactory extends LazyLogging {

  /**
   * Creates a BasicExecutionContext instance from given IDataStoreConnectorFactory and project Id.
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
  @deprecated("0.6.0","Use BuildLazyExecutionContext factory instead")
  def buildExecutionContext(dsFactory: IDataStoreConnectorFactory, projectId: Long, useJPA: Boolean): IExecutionContext = {
    val currentThread = Thread.currentThread

    if (!currentThread.getUncaughtExceptionHandler.isInstanceOf[ThreadLogger]) {
      currentThread.setUncaughtExceptionHandler(new ThreadLogger(logger.underlying.getName()))
    }

    val udsDbConnector = dsFactory.getUdsDbConnector
    val udsDbCtx = if (udsDbConnector == null) null
    else buildUdsDbConnectionContext(udsDbConnector, useJPA)

    val pdiDbConnector = dsFactory.getPdiDbConnector
    val pdiDbCtx = if (pdiDbConnector == null) null
    else buildDbConnectionContext(pdiDbConnector, useJPA)
    
    val psDbConnector = dsFactory.getPsDbConnector
    val psDbCtx = if (psDbConnector == null) null
    else buildDbConnectionContext(psDbConnector, useJPA)

    /* Project specific Dbs */
    val msiDbConnector = dsFactory.getMsiDbConnector(projectId)
    val msiDbCtx = if (msiDbConnector == null) null
    else buildMsiDbConnectionContext(msiDbConnector, useJPA)
    
    val lcMsDbConnector = dsFactory.getLcMsDbConnector(projectId)
    val lcMsDbCtx = if (lcMsDbConnector == null) null
    else buildLcMsDbConnectionContext(lcMsDbConnector, useJPA)

    new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, lcMsDbCtx)
  }
  
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
  protected[dal] def buildLazyExecutionContext(dsFactory: IDataStoreConnectorFactory, projectId: Long, useJPA: Boolean): IExecutionContext = {
    val currentThread = Thread.currentThread

    if (!currentThread.getUncaughtExceptionHandler.isInstanceOf[ThreadLogger]) {
      currentThread.setUncaughtExceptionHandler(new ThreadLogger(logger.underlying.getName()))
    }

    val udsDbCtxBuilder = { () =>
      val udsDbConnector = dsFactory.getUdsDbConnector
      if (udsDbConnector == null) null
      else buildUdsDbConnectionContext(udsDbConnector, useJPA)
    }
    
    val pdiDbCtxBuilder = { () =>
      val pdiDbConnector = dsFactory.getPdiDbConnector
      if (pdiDbConnector == null) null
      else buildDbConnectionContext(pdiDbConnector, useJPA)
    }
    
    val psDbCtxBuilder = { () =>
      val psDbConnector = dsFactory.getPsDbConnector
      if (psDbConnector == null) null
      else buildDbConnectionContext(psDbConnector, useJPA)
    }
    
    /* Project specific Dbs */
    val msiDbCtxBuilder = { () =>
      val msiDbConnector = dsFactory.getMsiDbConnector(projectId)
      if (msiDbConnector == null) null
      else buildMsiDbConnectionContext(msiDbConnector, useJPA)
    }
    
    val lcMsDbCtxBuilder = { () =>
      val lcMsDbConnector = dsFactory.getLcMsDbConnector(projectId)
      if (lcMsDbConnector == null) null
      else buildLcMsDbConnectionContext(lcMsDbConnector, useJPA)
    }

    new LazyExecutionContext(
      useJPA,
      udsDbCtxBuilder,
      pdiDbCtxBuilder,
      psDbCtxBuilder,
      msiDbCtxBuilder,
      lcMsDbCtxBuilder
    )
  }
  
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
  def buildDbConnectionContext(dbConnector: IDatabaseConnector, useJPA: Boolean): DatabaseConnectionContext = {
    if (dbConnector == null) {
      throw new IllegalArgumentException("DbConnector is null")
    }
    
    val dbType = dbConnector.getProlineDatabaseType

    if (useJPA) {
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
  }
  
  def buildLcMsDbConnectionContext(dbConnector: IDatabaseConnector, useJPA: Boolean): LcMsDbConnectionContext = {
    require( dbConnector.getProlineDatabaseType == ProlineDatabaseType.LCMS, "wrong ProlineDatabaseType")
    
    buildDbConnectionContext(dbConnector, useJPA).asInstanceOf[LcMsDbConnectionContext]
  }
  
  def buildMsiDbConnectionContext(dbConnector: IDatabaseConnector, useJPA: Boolean): MsiDbConnectionContext = {
    require( dbConnector.getProlineDatabaseType == ProlineDatabaseType.MSI, "wrong ProlineDatabaseType")
    
    buildDbConnectionContext(dbConnector, useJPA).asInstanceOf[MsiDbConnectionContext]
  }
  
  def buildUdsDbConnectionContext(dbConnector: IDatabaseConnector, useJPA: Boolean): UdsDbConnectionContext = {
    require( dbConnector.getProlineDatabaseType == ProlineDatabaseType.UDS, "wrong ProlineDatabaseType")
    
    buildDbConnectionContext(dbConnector, useJPA).asInstanceOf[UdsDbConnectionContext]
  }

}

object BuildExecutionContext extends LazyLogging {

  /**
   * Creates an ExecutionContext instance from given DataStoreConnectorFactory and project Id.
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
  def apply(dsFactory: IDataStoreConnectorFactory, projectId: Long, useJPA: Boolean): IExecutionContext = {
    ContextFactory.buildExecutionContext(dsFactory, projectId, useJPA)
  }

}

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
  def apply(dsFactory: IDataStoreConnectorFactory, projectId: Long, useJPA: Boolean): IExecutionContext = {
    ContextFactory.buildLazyExecutionContext(dsFactory, projectId, useJPA)
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
  def apply(dbConnector: IDatabaseConnector, useJPA: Boolean): DatabaseConnectionContext = {
    ContextFactory.buildDbConnectionContext(dbConnector, useJPA)
  }

}
