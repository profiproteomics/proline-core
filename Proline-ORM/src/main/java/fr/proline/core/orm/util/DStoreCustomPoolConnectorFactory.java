package fr.proline.core.orm.util;

import java.util.Map;

import javax.persistence.EntityManager;

import fr.profi.util.StringUtils;
import fr.profi.util.ThreadLogger;
import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.repository.ExternalDbRepository;
import fr.proline.repository.AbstractDatabaseConnector;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.IDatabaseConnector.ConnectionPoolType;
import fr.proline.repository.ProlineDatabaseType;

public class DStoreCustomPoolConnectorFactory extends AbstractDSConnecorFactory {

	
	private static final DStoreCustomPoolConnectorFactory UNIQUE_INSTANCE = new DStoreCustomPoolConnectorFactory();

	protected String m_applicationName;
	protected Integer m_projectMaxPoolConnection;
	protected IDatabaseConnector.ConnectionPoolType m_currentPoolType;

	/* Constructors */
	protected DStoreCustomPoolConnectorFactory() {
	}

	/* Public class methods */
	public static DStoreCustomPoolConnectorFactory getInstance() {
		return UNIQUE_INSTANCE;
	}
	
	
	public void initialize(final IDatabaseConnector udsDbConnector, String applicationName, ConnectionPoolType poolType ) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("DataStoreConnectorFactory ALREADY initialized");
			}

			if (udsDbConnector == null) {
				throw new UnsupportedOperationException("UdsDbConnector is null");
			}

			final Thread currentThread = Thread.currentThread();

			if (!(currentThread.getUncaughtExceptionHandler() instanceof ThreadLogger)) {
				currentThread.setUncaughtExceptionHandler(new ThreadLogger(LOG));
			}

			m_udsDbConnector = udsDbConnector;
			m_applicationName = applicationName;
			m_currentPoolType = poolType;
			try {
				m_projectMaxPoolConnection = Integer.parseInt(  m_udsDbConnector.getProperty(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY).toString());
			}catch(NumberFormatException nfe){
				m_projectMaxPoolConnection = null;
			}

			
			EntityManager udsEm = null;

			try {
				udsEm = udsDbConnector.createEntityManager();
				LOG.info(" ---- createEntityManager FOR UDS Db");
				final DriverType udsDriverType = udsDbConnector.getDriverType();

				/* Try to load PDI Db Connector */
				final ExternalDb pdiDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PDI);

				if (pdiDb == null) {
					LOG.warn("No ExternalDb for PDI Db");
				} else {
					Map<Object, Object> propertiesMap = pdiDb.toPropertiesMap(udsDriverType);
				    propertiesMap.put("ApplicationName", m_applicationName);
				    if(m_projectMaxPoolConnection != null){
				    	propertiesMap.put(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY, m_projectMaxPoolConnection);
				    	LOG.trace("USE PROLINE_MAX_POOL_CONNECTIONS_KEY for PDI "+m_projectMaxPoolConnection);
				    }
					m_pdiDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PDI, propertiesMap, poolType);
				}

				/* Try to load PS Db Connector */
				final ExternalDb psDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PS);

				if (psDb == null) {
					LOG.warn("No ExternalDb for PS Db");
				} else {
					Map<Object, Object> propertiesMap = psDb.toPropertiesMap(udsDriverType);
				    propertiesMap.put("ApplicationName", m_applicationName);
				    if(m_projectMaxPoolConnection != null){
				    	propertiesMap.put(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY, m_projectMaxPoolConnection*2);
				    	LOG.trace("USE PROLINE_MAX_POOL_CONNECTIONS_KEY for PS "+m_projectMaxPoolConnection*2);
				    }
					m_psDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PS, propertiesMap, poolType);
				}

			} catch (Exception ex) {
				/* Log and re-throw */
				final String message = "Error initializing DataStoreConnectorFactory";
				LOG.error(message, ex);

				/*
				 * Close and set to null partially created connectors, so we can
				 * retry to initialize DataStoreConnectorFactory with a valid
				 * UDS connector.
				 */
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
	
	public void initialize(final Map<Object, Object> udsDbProperties, String applicationName, ConnectionPoolType poolType) {
		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("This IDataStoreConnectorFactory ALREADY initialized");
			}

			if (udsDbProperties == null) {
				throw new IllegalArgumentException("UdsDbProperties Map is null");
			}
			
			m_currentPoolType =  poolType;

			initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.UDS, udsDbProperties, poolType), applicationName);

		} // End of synchronized block on m_managerLock

	}
	
	public void initialize(final String udsDbPropertiesFileName, String applicationName, ConnectionPoolType poolType) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("IDataStoreConnectorFactory ALREADY initialized");
			}

			if (StringUtils.isEmpty(udsDbPropertiesFileName)) {
				throw new IllegalArgumentException("Invalid udsDbPropertiesFileName");
			}
			m_currentPoolType =  poolType;
			
			initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.UDS, udsDbPropertiesFileName, poolType), applicationName);
		} // End of synchronized block on m_managerLock

	}
	
	/* Abstract implementation methods */

	/**
	 * Initializes this <code>DataStoreConnectorFactory</code> instance from an
	 * existing UDS Db DatabaseConnector using ExternalDb entities.
	 * <p>
	 * initialize() must be called only once.
	 * 
	 * @param udsDbConnector
	 *            DatabaseConnector to a valid UDS Db (must not be
	 *            <code>null</code>).
	 *            )
	 * @param applicationName : String use to identify  application which open connection
	 */
	@Override
	public void initialize(final IDatabaseConnector udsDbConnector, String applicationName) {
		initialize(udsDbConnector, applicationName, IDatabaseConnector.DEFAULT_POOL_TYPE); //ConnectionPoolType.HIGH_PERF_POOL_MANAGEMENT);
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
			
			//Use same projectMaxPoolConnection for all dbs 
			if(m_projectMaxPoolConnection != null){
		    	propertiesMap.put(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY, m_projectMaxPoolConnection);
		    }
			if(m_currentPoolType !=null)
				dbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(prolineDbType, propertiesMap,m_currentPoolType);
			else 
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
	
}
