package fr.proline.repository.util;

import static fr.profi.util.StringUtils.LINE_SEPARATOR;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.DatabaseUpgrader;
import fr.proline.repository.ProlineDatabaseType;

public abstract class DatabaseTestCase {

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestCase.class);

	private static final int BUFFER_SIZE = 2048;

	private final RuntimeException m_fakeException = new RuntimeException("_FakeException_ DatabaseTestCase instance creation");

	private final Object m_testCaseLock = new Object();

	/* All mutable fields are @GuardedBy("m_connectorLock") */

	private DatabaseTestConnector m_connector;

	private Connection m_keepaliveConnection;

	private boolean m_toreDown;

	/**
	 * Retrives the list of table names from <code>DatabaseMetaData</code> of
	 * the given SQL JDBC Connection for debugging purpose.
	 * 
	 * @param con
	 *            SQL JDBC Connection, must not be <code>null</code>. If
	 *            obtained from a JPA EntityManager, a valid Transaction must be
	 *            started.
	 * @return List of table names formated as a printable string.
	 */
	public static String formatTableNames(final Connection con) throws SQLException {

		if (con == null) {
			throw new IllegalArgumentException("Con is null");
		}

		final StringBuilder buff = new StringBuilder(BUFFER_SIZE);
		buff.append("Database Tables :");
		buff.append(LINE_SEPARATOR);

		final String[] tableNames = DatabaseUpgrader.extractTableNames(con);

		if ((tableNames != null) && (tableNames.length > 0)) {

			for (final String tableName : tableNames) {
				buff.append(tableName);
				buff.append(LINE_SEPARATOR);
			}

		}

		return buff.toString();
	}

	/**
	 * @return Database used for Connector creation.
	 */
	public abstract ProlineDatabaseType getProlineDatabaseType();

	/**
	 * DatabaseUtils.DEFAULT_DATABASE_PROPERTIES_FILENAME Could be used
	 * @return Full Path and Name of db properties file in classpath
	 * 
	 */
	public abstract String getPropertiesFileName(); 


	/**
	 * Path to SQL scripts from where DB will be initialized.
	 * 
	 * @return
	 */
	public String getMigrationScriptsLocation() {
		return DatabaseUpgrader.buildMigrationScriptsLocation(getProlineDatabaseType(), getConnector().getDriverType());
	}

	public String getMigrationClassLocation() {
		return DatabaseUpgrader.buildMigrationClassLocation(getProlineDatabaseType(), getConnector().getDriverType());
	}

	public final DatabaseTestConnector getConnector() {

		synchronized (m_testCaseLock) {

			if (m_toreDown) {
				throw new IllegalStateException("TestCase ALREADY torn down");
			}

			if (m_connector == null) {
				m_connector = new DatabaseTestConnector(getProlineDatabaseType(), getPropertiesFileName());
				LOG.info(" --> Create "+m_connector.getProlineDatabaseType()+" Connection ");
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

		} // End of synchronized block on m_testCaseLock

		return m_connector;
	}

	public void initDatabase() throws Exception, ClassNotFoundException {
		final DatabaseTestConnector connector = getConnector();

		DatabaseUpgrader.upgradeDatabase(connector, getMigrationScriptsLocation(), getMigrationClassLocation(), false);

		if (LOG.isTraceEnabled()) {
			/* Print Database Tables */
			final EntityManager em = connector.createEntityManager();
			EntityTransaction transac = null;
			boolean transacOk = false;

			try {
				transac = em.getTransaction();
				transac.begin();
				transacOk = false;

				final JDBCWork jdbcWork = new JDBCWork() {

					@Override
					public void execute(final Connection connection) throws SQLException {
						LOG.trace("Post-init EntityManager connection : {}  {}", connection, formatTableNames(connection));
					}

				};

				JPAUtils.doWork(em, jdbcWork);

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

				if (em != null) {
					try {
						em.close();
					} catch (Exception ex) {
						LOG.error("Error closing EntityManager", ex);
					}
				}

			}

		} // End if (LOG is Trace)

	}

	public void loadDataSet(final String datasetName) throws Exception {
		final DatabaseTestConnector connector = getConnector();
		DatabaseUtils.loadDataSet(connector, datasetName);
		connector.getDatabaseTester().onSetup();
	}

	public void loadDataSet(final InputStream datasetStream) throws Exception {
		final DatabaseTestConnector connector = getConnector();
		DatabaseUtils.loadDataSet(connector, datasetStream);
		connector.getDatabaseTester().onSetup();
	}

	public void loadCompositeDataSet(final String[] datasets) throws Exception {
		final DatabaseTestConnector connector = getConnector();
		DatabaseUtils.loadCompositeDataSet(connector, datasets);
		connector.getDatabaseTester().onSetup();
	}

	public void tearDown() {
		doTearDown(false);
	}

	@Override
	protected void finalize() throws Throwable {

		try {
			doTearDown(true);
		} finally {
			super.finalize();
		}

	}

	/* Private methods */
	private void doTearDown(final boolean fromFinalize) {

		synchronized (m_testCaseLock) {

			if (!m_toreDown) { // Close only once
				m_toreDown = true;

				if (fromFinalize) {
					LOG.warn("Tearing down " + getProlineDatabaseTypeString() + " TestCase from finalize !", m_fakeException);
				}

				/* Close the keep-alive connection and finally the Db Connector */
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

		} // End of synchronized block on m_testCaseLock

	}

	private String getProlineDatabaseTypeString() {
		String result = null;

		final ProlineDatabaseType dbType = getProlineDatabaseType();

		if (dbType == null) {
			result = "Unknown Db";
		} else {
			result = dbType + " Db";
		}

		return result;
	}

}
