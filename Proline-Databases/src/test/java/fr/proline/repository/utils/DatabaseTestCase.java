package fr.proline.repository.utils;

import java.sql.Connection;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.Database;

public abstract class DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestCase.class);

    private final Object m_connectorLock = new Object();

    /* @GuardedBy("m_connectorLock") */
    private DatabaseTestConnector m_connector;

    /* @GuardedBy("m_connectorLock") */
    private Connection m_keepaliveConnection;

    /* @GuardedBy("m_connectorLock") */
    private EntityManager m_currentEntityManager;

    /* @GuardedBy("m_connectorLock") */
    private boolean m_toreDown;

    /**
     * @return Database used for Connector creation.
     */
    public abstract Database getDatabase();

    /**
     * @return Full Path and Name of db properties file in classpath
     */
    public String getPropertiesFileName() {
	return DatabaseUtils.DEFAULT_DATABASE_PROPERTIES_FILENAME;
    }

    /**
     * Path to SQL scripts from where DB will be initialized.
     * 
     * @return
     */
    public abstract String getSQLScriptLocation();

    public final DatabaseTestConnector getConnector() {

	synchronized (m_connectorLock) {

	    if (m_toreDown) {
		throw new IllegalStateException("TestCase ALREADY torn down");
	    }

	    if (m_connector == null) {
		m_connector = new DatabaseTestConnector(getDatabase(), getPropertiesFileName());

		try {
		    final DataSource ds = m_connector.getDataSource();

		    m_keepaliveConnection = ds.getConnection();
		} catch (Exception ex) {
		    LOG.error("Error creating keep-alive SQL connection", ex);
		}

	    }

	} // End of synchronized block on m_connectorLock

	return m_connector;
    }

    /**
     * Get the EntityManager for this Database TestCase.
     * 
     * @return
     */
    public final EntityManager getEntityManager() {

	synchronized (m_connectorLock) {

	    if (m_toreDown) {
		throw new IllegalStateException("TestCase ALREADY torn down");
	    }

	    if (m_currentEntityManager == null) {
		final DatabaseTestConnector connector = getConnector();

		final EntityManagerFactory emf = connector.getEntityManagerFactory();

		m_currentEntityManager = emf.createEntityManager();
	    }

	} // End of synchronized block on m_connectorLock

	return m_currentEntityManager;
    }

    public void initDatabase() throws Exception, ClassNotFoundException {
	DatabaseUtils.initDatabase(getConnector(), getSQLScriptLocation());
    }

    public void loadDataSet(final String datasetName) throws Exception {
	final DatabaseTestConnector connector = getConnector();

	DatabaseUtils.loadDataSet(connector, datasetName);
	connector.getDatabaseTester().onSetup();
    }

    public void loadCompositeDataSet(final String[] datasets) throws Exception {
	final DatabaseTestConnector connector = getConnector();

	DatabaseUtils.loadCompositeDataSet(connector, datasets);
	connector.getDatabaseTester().onSetup();
    }

    public void tearDown() {

	synchronized (m_connectorLock) {

	    if (!m_toreDown) { // Close only once
		m_toreDown = true;

		/* Close EntityManager then keep-alive connection and finally Connector */
		if (m_currentEntityManager != null) {
		    LOG.debug("Closing current EntityManager");

		    try {
			m_currentEntityManager.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing current EntityManager", exClose);
		    }

		}

		if (m_keepaliveConnection != null) {

		    LOG.debug("Closing keep-alive SQL connection");

		    try {
			m_keepaliveConnection.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing keep-alive SQL connection", exClose);
		    }

		}

		if (m_connector != null) {
		    LOG.debug("Closing DatabaseTestConnector");

		    try {
			m_connector.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing DatabaseTestConnector", exClose);
		    }

		}

	    }

	} // End of synchronized block on m_connectorLock

    }

    @Override
    protected void finalize() throws Throwable {

	try {
	    tearDown();
	} finally {
	    super.finalize();
	}

    }

}
