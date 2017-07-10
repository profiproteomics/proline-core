package fr.proline.core.orm.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.persistence.EntityManager;

import fr.profi.util.ThreadLogger;
import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.repository.ExternalDbRepository;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;

public class DataStoreConnectorFactory extends AbstractDSConnecorFactory {

	private static final DataStoreConnectorFactory UNIQUE_INSTANCE = new DataStoreConnectorFactory();


	protected String m_applicationName;

	/* Constructors */
	protected DataStoreConnectorFactory() {
	}

	/* Public class methods */
	public static DataStoreConnectorFactory getInstance() {
		return UNIQUE_INSTANCE;
	}

	/* Abstract implementation methods */

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
	 * Initializes this <code>DataStoreConnectorFactory</code> instance from an SQL Connection.
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
	
	protected IDatabaseConnector createProjectDatabaseConnector(
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

}
