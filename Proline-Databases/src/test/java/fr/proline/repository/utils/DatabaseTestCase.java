package fr.proline.repository.utils;

import static fr.proline.repository.utils.DatabaseUtils.MIGRATION_SCRIPTS_DIR;
import static fr.proline.util.StringUtils.LINE_SEPARATOR;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.Database;
import fr.proline.repository.util.JDBCWork;
import fr.proline.repository.util.JPAUtils;

public abstract class DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestCase.class);

    private static final int BUFFER_SIZE = 2048;

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
     * Retrives the list of table names from <code>DatabaseMetaData</code> of the given SQL JDBC Connection
     * for debugging purpose.
     * 
     * @param con
     *            SQL JDBC Connection, must not be <code>null</code>. If obtained from a JPA EntityManager, a
     *            valid Transaction must be started.
     * @return List of table names formated as a printable string.
     */
    public static String getTables(final Connection con) throws SQLException {

	if (con == null) {
	    throw new IllegalArgumentException("Con is null");
	}

	final DatabaseMetaData meta = con.getMetaData();

	final StringBuilder buff = new StringBuilder(BUFFER_SIZE);
	buff.append("Database Tables :");
	buff.append(LINE_SEPARATOR);

	final ResultSet rs = meta.getTables(null, null, "%", new String[] { "TABLE" });

	try {

	    while (rs.next()) {

		final Object tableName = rs.getObject("TABLE_NAME");
		if (tableName != null) {
		    buff.append(tableName);
		    buff.append(LINE_SEPARATOR);
		}

	    }

	} finally {
	    rs.close();
	}

	return buff.toString();
    }

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
    public String getMigrationScriptsLocation() {
	final StringBuilder buffer = new StringBuilder(MIGRATION_SCRIPTS_DIR);
	buffer.append(getDatabase().name().toLowerCase()).append('/');
	buffer.append(getConnector().getDriverType().name().toLowerCase()).append('/');

	return buffer.toString();
    }

    public final DatabaseTestConnector getConnector() {

	synchronized (m_connectorLock) {

	    if (m_toreDown) {
		throw new IllegalStateException("TestCase ALREADY torn down");
	    }

	    if (m_connector == null) {
		m_connector = new DatabaseTestConnector(getDatabase(), getPropertiesFileName());

		if (m_connector.isMemory()) {

		    try {
			final DataSource ds = m_connector.getDataSource();

			m_keepaliveConnection = ds.getConnection();

			LOG.info("Started keep-alive connection : {}", m_keepaliveConnection);
		    } catch (Exception ex) {
			LOG.error("Error creating keep-alive SQL connection", ex);
		    }

		} // End if (connector is memory)

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
		final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

		m_currentEntityManager = emf.createEntityManager();
	    }

	} // End of synchronized block on m_connectorLock

	return m_currentEntityManager;
    }

    public void initDatabase() throws Exception, ClassNotFoundException {
	DatabaseUtils.initDatabase(getConnector(), getMigrationScriptsLocation());

	if (LOG.isDebugEnabled()) {
	    /* Print Database Tables */
	    final EntityManager currentEm = getEntityManager();
	    final EntityTransaction transac = currentEm.getTransaction();
	    boolean transacOk = false;

	    try {
		transac.begin();
		transacOk = false;

		final JDBCWork jdbcWork = new JDBCWork() {

		    @Override
		    public void execute(final Connection connection) throws SQLException {
			LOG.debug("Post-init EntityManager connection : {}  {}", connection,
				getTables(connection));
		    }

		};

		JPAUtils.doWork(currentEm, jdbcWork);

		transac.commit();
		transacOk = true;
	    } finally {

		if ((transac != null) && !transacOk) {
		    LOG.info("Rollbacking EntityManager transaction");

		    try {
			transac.rollback();
		    } catch (Exception ex) {
			LOG.error("Error rollbacking EntityManager transaction", ex);
		    }

		}

	    }

	} // End if (LOG is Debug)

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
		    } catch (SQLException exClose) {
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
