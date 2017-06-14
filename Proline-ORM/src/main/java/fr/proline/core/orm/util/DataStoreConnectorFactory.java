package fr.proline.core.orm.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.profi.util.StringUtils;
import fr.profi.util.ThreadLogger;
import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.repository.ExternalDbRepository;
import fr.proline.repository.*;

public class DataStoreConnectorFactory implements IDataStoreConnectorFactory {

	/* Constants */
	private static final Logger LOG = LoggerFactory.getLogger(DataStoreConnectorFactory.class);

	private static final DataStoreConnectorFactory UNIQUE_INSTANCE = new DataStoreConnectorFactory();

	/* Instance variables */
	private final Object m_managerLock = new Object();

	/* All instance variables are @GuardedBy("m_managerLock") */
	private IDatabaseConnector m_udsDbConnector;
	private IDatabaseConnector m_pdiDbConnector;
	private IDatabaseConnector m_psDbConnector;

	private final Map<Long, IDatabaseConnector> m_msiDbConnectors = new HashMap<Long, IDatabaseConnector>();
	private final Map<Long, IDatabaseConnector> m_lcMsDbConnectors = new HashMap<Long, IDatabaseConnector>();
	private final Map<Long, Map<Object, Object>> m_msiDbPropertiesMaps = new HashMap<Long, Map<Object, Object>>();
	private final Map<Long, Map<Object, Object>> m_lcMsDbPropertiesMaps = new HashMap<Long, Map<Object, Object>>();

	private String m_applicationName;

	/* Constructors */
	protected DataStoreConnectorFactory() {
	}

	/* Public class methods */
	public static DataStoreConnectorFactory getInstance() {
		return UNIQUE_INSTANCE;
	}

	/* Public methods */

	public void initialize(final IDatabaseConnector udsDbConnector) {
		initialize(udsDbConnector, "Proline");
	}

	/**
	 * Initializes this <code>DataStoreConnectorFactory</code> instance from an
	 * existing UDS Db DatabaseConnector using ExternalDb entities.
	 * <p>
	 * initialize() must be called only once.
	 * 
	 * @param udsDbConnector DatabaseConnector to a valid UDS Db (must not be <code>null</code>).
	 * @param applicationName String used to identify the application that opened the connection.
	 */
	public void initialize(final IDatabaseConnector udsDbConnector, final String applicationName) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("DataStoreConnectorFactory ALREADY initialized");
			}

			if (udsDbConnector == null) {
				throw new IllegalArgumentException("udsDbConnector is null");
			}

			final Thread currentThread = Thread.currentThread();

			if (!(currentThread.getUncaughtExceptionHandler() instanceof ThreadLogger)) {
				currentThread.setUncaughtExceptionHandler(new ThreadLogger(LOG));
			}

			m_udsDbConnector = udsDbConnector;
			m_applicationName = applicationName;
			
			EntityManager udsEm = null;

			try {
				udsEm = udsDbConnector.createEntityManager();

				final DriverType udsDriverType = udsDbConnector.getDriverType();

				// Try to load PDI Db Connector //
				final ExternalDb pdiDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PDI);

				if (pdiDb == null) {
					LOG.warn("No ExternalDb for PDI Db");
				} else {
					Map<Object, Object> propertiesMap = pdiDb.toPropertiesMap(udsDriverType);
					propertiesMap.put("ApplicationName", m_applicationName);
					m_pdiDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PDI, propertiesMap);
				}

				// Try to load PS Db Connector //
				final ExternalDb psDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PS);

				if (psDb == null) {
					LOG.warn("No ExternalDb for PS Db");
				} else {
					Map<Object, Object> propertiesMap = psDb.toPropertiesMap(udsDriverType);
					propertiesMap.put("ApplicationName", m_applicationName);
					m_psDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PS, propertiesMap);
				}

			} catch (Exception ex) {
				// Log and re-throw //
				final String message = "Error initializing DataStoreConnectorFactory";
				LOG.error(message, ex);

				// Close and set to null partially created connectors,
				// so we can retry to initialize DataStoreConnectorFactory with a valid UDS connector.
				closeAll();

				throw new RuntimeException(message, ex);
			} finally {

				if (udsEm != null) {
					try {
						udsEm.close();
					} catch (Exception exClose) {
						LOG.error("Error closing UDS Db EntityManager", exClose);
					}
				}

			}

		} // End of synchronized block on m_managerLock

	}
	
	/**
	 * Initializes this <code>DataStoreConnectorFactory</code> instance from an SQL Connetion.
	 * <p>
	 * initialize() must be called only once.
	 * 
	 * @param udsDbConnector DatabaseConnector to a valid UDS Db (must not be <code>null</code>).
	 * @param applicationName String used to identify the application that opened the connection.
	 * @param useManagedConnection Boolean used to indicate if we should use or not a managed connection for the DataStore initialization.
	 */
	public void initialize(final IDatabaseConnector udsDbConnector, final String applicationName, final boolean useManagedConnection) {
	  if (useManagedConnection == false) {
	    this.initialize(udsDbConnector, applicationName);
	    return;
	  }
	  
		synchronized (m_managerLock) {
	
			if (isInitialized()) {
				throw new IllegalStateException("DataStoreConnectorFactory ALREADY initialized");
			}
			if (udsDbConnector == null) {
				throw new IllegalArgumentException("udsDbConnector is null");
			}
	
			final Thread currentThread = Thread.currentThread();
	
			if (!(currentThread.getUncaughtExceptionHandler() instanceof ThreadLogger)) {
				currentThread.setUncaughtExceptionHandler(new ThreadLogger(LOG));
			}
	
			m_udsDbConnector = udsDbConnector;
			m_applicationName = applicationName;
			
			Connection udsDbConnection = null;
			
			try {
				udsDbConnection = udsDbConnector.createUnmanagedConnection();
				final DriverType udsDriverType = udsDbConnector.getDriverType();
				
				// Try to load PDI Db Connector //
				final ExternalDb pdiDb = this.loadExternalDbByType(udsDbConnection, ProlineDatabaseType.PDI);
	
				if (pdiDb == null) {
					LOG.warn("No ExternalDb for PDI Db");
				} else {
					Map<Object, Object> propertiesMap = pdiDb.toPropertiesMap(udsDriverType);
					propertiesMap.put("ApplicationName", m_applicationName);
					m_pdiDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PDI, propertiesMap);
				}
	
				// Try to load PS Db Connector //
				final ExternalDb psDb = this.loadExternalDbByType(udsDbConnection, ProlineDatabaseType.PS);
	
				if (psDb == null) {
					LOG.warn("No ExternalDb for PS Db");
				} else {
					Map<Object, Object> propertiesMap = psDb.toPropertiesMap(udsDriverType);
					propertiesMap.put("ApplicationName", m_applicationName);
					m_psDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PS, propertiesMap);
				}
	
			} catch (Exception ex) {
				// Log and re-throw //
				final String message = "Error initializing DataStoreConnectorFactory";
				LOG.error(message, ex);
	
				// Close and set to null partially created connectors,
				// so we can retry to initialize DataStoreConnectorFactory with a valid UDS connector.
				closeAll();
	
				throw new RuntimeException(message, ex);
			} finally {
				// Close the unmanaged connection that has been created locally
				if (udsDbConnection != null) {
					try {
						udsDbConnection.close();
					} catch (SQLException e) {
						throw new RuntimeException("Error closing managed UDSdb connection", e);
					}
				}
			}
	
		} // End of synchronized block on m_managerLock

	}

	public void initialize(final Map<Object, Object> udsDbProperties) {
		initialize(udsDbProperties, "Proline");
	}
	
	public void initialize(final Map<Object, Object> udsDbProperties, String applicationName) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("DataStoreConnectorFactory ALREADY initialized");
			}

			if (udsDbProperties == null) {
				throw new IllegalArgumentException("UdsDbProperties Map is null");
			}

			initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.UDS, udsDbProperties), applicationName);
		} // End of synchronized block on m_managerLock

	}


	public void initialize(final String udsDbPropertiesFileName) {
		initialize(udsDbPropertiesFileName, "Proline");
	}
	
	public void initialize(final String udsDbPropertiesFileName, String applicationName) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("DataStoreConnectorFactory ALREADY initialized");
			}

			if (StringUtils.isEmpty(udsDbPropertiesFileName)) {
				throw new IllegalArgumentException("Invalid udsDbPropertiesFileName");
			}

			initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.UDS, udsDbPropertiesFileName));

		} // End of synchronized block on m_managerLock

	}

	@Override
	public boolean isInitialized() {
		boolean result;

		synchronized (m_managerLock) {
			result = (m_udsDbConnector != null);
		} // End of synchronized block on m_managerLock

		return result;
	}

	@Override
	public IDatabaseConnector getUdsDbConnector() {
		IDatabaseConnector udsDbConnector;

		synchronized (m_managerLock) {
			checkInitialization();

			udsDbConnector = m_udsDbConnector;
		} // End of synchronized block on m_managerLock

		return udsDbConnector;
	}

	@Override
	public IDatabaseConnector getPdiDbConnector() {
		IDatabaseConnector pdiDbConnector;

		synchronized (m_managerLock) {
			checkInitialization();

			pdiDbConnector = m_pdiDbConnector;
		} // End of synchronized block on m_managerLock

		return pdiDbConnector;
	}

	@Override
	public IDatabaseConnector getPsDbConnector() {
		IDatabaseConnector psDbConnector;

		synchronized (m_managerLock) {
			checkInitialization();

			psDbConnector = m_psDbConnector;
		} // End of synchronized block on m_managerLock

		return psDbConnector;
	}

	@Override
	public IDatabaseConnector getMsiDbConnector(final long projectId) {
		IDatabaseConnector msiDbConnector = null;

		synchronized (m_managerLock) {
			checkInitialization();

			final Long key = Long.valueOf(projectId);

			msiDbConnector = m_msiDbConnectors.get(key);

			if (msiDbConnector == null) {
				msiDbConnector = createProjectDatabaseConnector(projectId, ProlineDatabaseType.MSI, this.m_msiDbPropertiesMaps);

				if (msiDbConnector != null) {
					m_msiDbConnectors.put(key, msiDbConnector);
				}

			}

		} // End of synchronized block on m_managerLock

		return msiDbConnector;
	}

	@Override
	public IDatabaseConnector getLcMsDbConnector(final long projectId) {
		IDatabaseConnector lcMsDbConnector = null;

		synchronized (m_managerLock) {
			checkInitialization();

			final Long key = Long.valueOf(projectId);

			lcMsDbConnector = m_lcMsDbConnectors.get(key);

			if (lcMsDbConnector == null) {
				lcMsDbConnector = createProjectDatabaseConnector(projectId, ProlineDatabaseType.LCMS, this.m_lcMsDbPropertiesMaps);

				if (lcMsDbConnector != null) {
					m_lcMsDbConnectors.put(key, lcMsDbConnector);
				}

			}

		} // End of synchronized block on m_managerLock

		return lcMsDbConnector;
	}

	/**
	 * Closes all DatabaseConnectors.
	 * <p>
	 * Only call this method on application exiting.
	 */
	@Override
	public void closeAll() {

		synchronized (m_managerLock) {

			/* Reverse order of Dbs creation : LCMS, MSI, PS, PDI, UDS */
			for (final IDatabaseConnector lcMsDbConnector : m_lcMsDbConnectors.values()) {
				try {
					lcMsDbConnector.close();
				} catch (Exception exClose) {
					LOG.error("Error closing LCMS Db Connector", exClose);
				}
			}

			m_lcMsDbConnectors.clear(); // Remove all LCMS Db connectors

			for (final IDatabaseConnector msiDbConnector : m_msiDbConnectors.values()) {
				try {
					msiDbConnector.close();
				} catch (Exception exClose) {
					LOG.error("Error closing MSI Db Connector", exClose);
				}
			}

			m_msiDbConnectors.clear(); // Remove all MSI Db connectors

			if (m_psDbConnector != null) {

				try {
					m_psDbConnector.close();
				} catch (Exception exClose) {
					LOG.error("Error closing PS Db Connector", exClose);
				}

				m_psDbConnector = null;
			}

			if (m_pdiDbConnector != null) {

				try {
					m_pdiDbConnector.close();
				} catch (Exception exClose) {
					LOG.error("Error closing PDI Db Connector", exClose);
				}

				m_pdiDbConnector = null;
			}

			if (m_udsDbConnector != null) {

				try {
					m_udsDbConnector.close();
				} catch (Exception exClose) {
					LOG.error("Error closing UDS Db Connector", exClose);
				}

				m_udsDbConnector = null;
			}

		} // End of synchronized block on m_managerLock

	}
	
	@Override
	public void closeLcMsDbConnector(long projectId) {

		synchronized (m_managerLock) {
			try {
				IDatabaseConnector lcMsDbConnector = m_lcMsDbConnectors.get(projectId);
				if (lcMsDbConnector != null) {
					lcMsDbConnector.close();
					m_lcMsDbConnectors.remove(projectId);
				}
			} catch (Exception exClose) {
				LOG.error("Error closing LCMS Db Connector", exClose);
			}
		}
	}
	
	@Override
	public void closeMsiDbConnector(long projectId) {

		synchronized (m_managerLock) {
			try {
				IDatabaseConnector msiDbConnector = m_msiDbConnectors.get(projectId);
				if (msiDbConnector != null) {
					msiDbConnector.close();
					m_msiDbConnectors.remove(projectId);
				}
			} catch (Exception exClose) {
				LOG.error("Error closing MSI Db Connector", exClose);
			}
		}
	}
	
	@Override
	public void closeProjectConnectors(long projectId) {
		this.closeLcMsDbConnector(projectId);
		this.closeMsiDbConnector(projectId);
	}

	/* Private methods */
	private void checkInitialization() {

		if (!isInitialized()) {
			throw new IllegalStateException("DataStoreConnectorFactory NOT yet initialized");
		}

	}
	
	private IDatabaseConnector createProjectDatabaseConnector(
		final long projectId,
		final ProlineDatabaseType prolineDbType,
		final Map<Long, Map<Object, Object>> propertiesMaps
	) {
		IDatabaseConnector dbConnector = null;

		synchronized (m_managerLock) {
			checkInitialization();

			final Long key = Long.valueOf(projectId);

			Map<Object, Object> propertiesMap = propertiesMaps.get(key);
			
			if (propertiesMap == null) {
				propertiesMap = retrieveExternalDbProperties(prolineDbType, projectId);
				propertiesMaps.put(key, propertiesMap);
			}
			
			dbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(prolineDbType, propertiesMap);

		} // End of synchronized block on m_managerLock

		return dbConnector;
	}
	
	protected Map<Object, Object> retrieveExternalDbProperties(final ProlineDatabaseType prolineDbType, final long projectId) {
		Map<Object, Object> propertiesMap = null;

		final IDatabaseConnector udsDbConnector = getUdsDbConnector();

		EntityManager udsEm = udsDbConnector.createEntityManager();

		try {
			final Project project = udsEm.find(Project.class, Long.valueOf(projectId));

			if (project == null) {
				throw new IllegalArgumentException("Project #" + projectId + " NOT found in UDS Db");
			}

			final ExternalDb externalDb = ExternalDbRepository.findExternalByTypeAndProject(udsEm, prolineDbType, project);

			if (externalDb == null) {
				LOG.warn("No ExternalDb for {} Db of project #{}", prolineDbType, projectId);
			} else {
				propertiesMap = externalDb.toPropertiesMap(udsDbConnector.getDriverType());
				propertiesMap.put("ApplicationName", m_applicationName);
			}

		} finally {

			try {
				udsEm.close();
			} catch (Exception exClose) {
				LOG.error("Error closing UDS Db EntityManager", exClose);
			}

		}

		return propertiesMap;
	}
	
	protected ExternalDb loadExternalDbByType(Connection connection, ProlineDatabaseType dbType) throws SQLException {
		return this.loadExternalDb(connection, "SELECT * FROM external_db WHERE type = '" + dbType.toString() + "'", dbType);
	}
	
	protected ExternalDb loadExternalDbByTypeAndProjectId(Connection connection, ProlineDatabaseType dbType, long projectId) throws SQLException {
		String sqlQuery = "SELECT external_db.* FROM external_db, project_db_map "+
		"WHERE project_db_map.external_db_id = external_db.id "+
		"AND external_db.type = '" + dbType.toString() + "' " +
		"AND project_db_map.project_id = " + projectId;
		return this.loadExternalDb(connection, sqlQuery, dbType);
	}
	
	private ExternalDb loadExternalDb(Connection connection, String sqlQuery, ProlineDatabaseType dbType) throws SQLException {

		final ExternalDb udsExtDb = new ExternalDb();
		final Statement stmt = connection.createStatement();

		try {

			final ResultSet rs = stmt.executeQuery(sqlQuery);
			if (rs.next()) {

				udsExtDb.setId(rs.getLong("id"));
				udsExtDb.setDbName(rs.getString("name"));
				udsExtDb.setConnectionMode(fr.proline.repository.ConnectionMode.valueOf(rs.getString("connection_mode")));
				udsExtDb.setDbUser(rs.getString("username"));
				udsExtDb.setDbPassword(rs.getString("password"));
				udsExtDb.setHost(rs.getString("host"));
				udsExtDb.setPort(rs.getInt("port"));
				udsExtDb.setType(dbType);
				udsExtDb.setDbVersion(rs.getString("version"));
				udsExtDb.setIsBusy(false);
				udsExtDb.setSerializedProperties(rs.getString("serialized_properties"));
			}

			rs.close();

		} finally {
			stmt.close();
		}

		return udsExtDb;
	}
	
	/*
	protected IDatabaseConnector createProjectDatabaseConnector(final ProlineDatabaseType prolineDbType, final long projectId) {
		IDatabaseConnector connector = null;

		final IDatabaseConnector udsDbConnector = getUdsDbConnector();

		final EntityManagerFactory udsEMF = udsDbConnector.getEntityManagerFactory();

		EntityManager udsEm = udsEMF.createEntityManager();

		try {
			final Project project = udsEm.find(Project.class, Long.valueOf(projectId));

			if (project == null) {
				throw new IllegalArgumentException("Project #" + projectId + " NOT found in UDS Db");
			}

			final ExternalDb externalDb = ExternalDbRepository.findExternalByTypeAndProject(udsEm, prolineDbType, project);

			if (externalDb == null) {
				LOG.warn("No ExternalDb for {} Db of project #{}", prolineDbType, projectId);
			} else {
				Map<Object, Object> propertiesMap = externalDb.toPropertiesMap(udsDbConnector.getDriverType());
				propertiesMap.put("ApplicationName", m_applicationName);
				connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(prolineDbType, propertiesMap);
			}

		} finally {

			try {
				udsEm.close();
			} catch (Exception exClose) {
				LOG.error("Error closing UDS Db EntityManager", exClose);
			}

		}

		return connector;
	}*/

}
