package fr.proline.context;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.IDataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;

public final class ContextFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ContextFactory.class);

    /* Private constructor (Utility class) */
    private ContextFactory() {
    }

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
    public static IExecutionContext getExecutionContextInstance(final IDataStoreConnectorFactory dsFactory,
	    final int projectId, final boolean useJPA) {
	DatabaseConnectionContext udsDb = null;

	final IDatabaseConnector udsDbConnector = dsFactory.getUdsDbConnector();

	if (udsDbConnector != null) {
	    udsDb = buildDbConnectionContext(udsDbConnector, useJPA);
	}

	DatabaseConnectionContext pdiDb = null;

	final IDatabaseConnector pdiDbConnector = dsFactory.getPdiDbConnector();

	if (pdiDbConnector != null) {
	    pdiDb = buildDbConnectionContext(pdiDbConnector, useJPA);
	}

	DatabaseConnectionContext psDb = null;

	final IDatabaseConnector psDbConnector = dsFactory.getPsDbConnector();

	if (psDbConnector != null) {
	    psDb = buildDbConnectionContext(psDbConnector, useJPA);
	}

	/* Project specific Dbs */
	DatabaseConnectionContext msiDb = null;

	final IDatabaseConnector msiDbConnector = dsFactory.getMsiDbConnector(projectId);

	if (msiDbConnector != null) {
	    msiDb = buildDbConnectionContext(msiDbConnector, useJPA);
	}

	DatabaseConnectionContext lcMsDb = null;

	final IDatabaseConnector lcMsDbConnector = dsFactory.getLcMsDbConnector(projectId);

	if (lcMsDbConnector != null) {
	    lcMsDb = buildDbConnectionContext(lcMsDbConnector, useJPA);
	}

	return new BasicExecutionContext(udsDb, pdiDb, psDb, msiDb, lcMsDb);
    }

    /**
     * Creates a <code>DatabaseConnectionContext</code> from given DatabaseConnector.
     * 
     * @param dbConnector
     *            DatabaseConnector used to access a Proline Db.
     * @param useJPA
     *            If <code>true</code> returned Db context wraps a new <code>EntityManager</code>. If
     *            <code>false</code> returned Db context wraps a new SQL JDBC <code>Connection</code>.
     * @return A new instance of <code>DatabaseConnectionContext</code>
     */
    public static DatabaseConnectionContext buildDbConnectionContext(final IDatabaseConnector dbConnector,
	    final boolean useJPA) {

	if (dbConnector == null) {
	    throw new IllegalArgumentException("DbConnector is null");
	}

	DatabaseConnectionContext dbConnectionContext = null;

	if (useJPA) {
	    dbConnectionContext = new DatabaseConnectionContext(dbConnector);
	} else {

	    try {
		dbConnectionContext = new DatabaseConnectionContext(dbConnector.getDataSource()
			.getConnection(), dbConnector.getDriverType());
	    } catch (SQLException ex) {
		/* Log and re-throw */
		final String message = "Unable to obtain SQL JDBC Connection for "
			+ dbConnector.getProlineDatabaseType();
		LOG.error(message, ex);

		throw new RuntimeException(message, ex);
	    }

	} // End if (SQL mode)

	return dbConnectionContext;
    }

}
