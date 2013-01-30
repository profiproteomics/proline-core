package fr.proline.core.dal

import java.sql.SQLException

import com.weiglewilczek.slf4s.Logging

import fr.proline.context.BasicExecutionContext
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.repository.IDatabaseConnector

object ContextFactory extends Logging {

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
  def buildExecutionContext(dsFactory: IDataStoreConnectorFactory,
    projectId: Int, useJPA: Boolean): IExecutionContext = {
    var udsDb: DatabaseConnectionContext = null

    val udsDbConnector = dsFactory.getUdsDbConnector

    if (udsDbConnector != null) {
      udsDb = buildDbConnectionContext(udsDbConnector, useJPA)
    }

    var pdiDb: DatabaseConnectionContext = null

    val pdiDbConnector = dsFactory.getPdiDbConnector

    if (pdiDbConnector != null) {
      pdiDb = buildDbConnectionContext(pdiDbConnector, useJPA)
    }

    var psDb: DatabaseConnectionContext = null

    val psDbConnector = dsFactory.getPsDbConnector

    if (psDbConnector != null) {
      psDb = buildDbConnectionContext(psDbConnector, useJPA)
    }

    /* Project specific Dbs */
    var msiDb: DatabaseConnectionContext = null;

    val msiDbConnector = dsFactory.getMsiDbConnector(projectId)

    if (msiDbConnector != null) {
      msiDb = buildDbConnectionContext(msiDbConnector, useJPA)
    }

    var lcMsDb: DatabaseConnectionContext = null;

    val lcMsDbConnector = dsFactory.getLcMsDbConnector(projectId)

    if (lcMsDbConnector != null) {
      lcMsDb = buildDbConnectionContext(lcMsDbConnector, useJPA)
    }

    new BasicExecutionContext(udsDb, pdiDb, psDb, msiDb, lcMsDb)
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
  def buildDbConnectionContext(dbConnector: IDatabaseConnector,
    useJPA: Boolean): DatabaseConnectionContext = {

    if (dbConnector == null) {
      throw new IllegalArgumentException("DbConnector is null")
    }

    if (useJPA) {
      new DatabaseConnectionContext(dbConnector)
    } else {

      try {
        new SQLConnectionContext(dbConnector.getDataSource
          .getConnection, dbConnector.getDriverType)
      } catch {

        case sqlEx: SQLException => {
          /* Log and re-throw */
          val message = "Unable to obtain SQL JDBC Connection for " +
            dbConnector.getProlineDatabaseType
          logger.error(message, sqlEx)

          throw new RuntimeException(message, sqlEx)
        }

      }

    } // End if (SQL mode)

  }

}