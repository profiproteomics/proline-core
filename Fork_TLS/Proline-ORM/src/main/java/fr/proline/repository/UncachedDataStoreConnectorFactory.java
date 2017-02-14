package fr.proline.repository;

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

public class UncachedDataStoreConnectorFactory implements IDataStoreConnectorFactory {
	
	private static final String APP_NAME = "Proline";

	/* Constants */
	private static final Logger LOG = LoggerFactory.getLogger(UncachedDataStoreConnectorFactory.class);

	private static final UncachedDataStoreConnectorFactory UNIQUE_INSTANCE = new UncachedDataStoreConnectorFactory();

	/* Instance variables */
	private final Object m_managerLock = new Object();

	/* All instance variables are @GuardedBy("m_managerLock") */
	private IDatabaseConnector m_udsDbConnector;
	private Map<Object, Object> pdiDbPropertiesMap;
	private Map<Object, Object> psDbPropertiesMap;
	private final Map<Long, Map<Object, Object>> m_msiDbPropertiesMaps = new HashMap<Long, Map<Object, Object>>();
	private final Map<Long, Map<Object, Object>> m_lcMsDbPropertiesMaps = new HashMap<Long, Map<Object, Object>>();

	private String m_applicationName;

	/* Constructors */
	protected UncachedDataStoreConnectorFactory() {
	}

	/* Public class methods */
	public static UncachedDataStoreConnectorFactory getInstance() {
		return UNIQUE_INSTANCE;
	}

	/* Public methods */

	public void initialize(final IDatabaseConnector udsDbConnector) {
		initialize(udsDbConnector, APP_NAME);
	}

	/**
	 * Initializes this <code>UncachedDataStoreConnectorFactory</code> instance from an
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
	public void initialize(final IDatabaseConnector udsDbConnector, String applicationName) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("UncachedDataStoreConnectorFactory ALREADY initialized");
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
			
			EntityManager udsEm = null;

			try {
				udsEm = udsDbConnector.createEntityManager();

				final DriverType udsDriverType = udsDbConnector.getDriverType();

				/* Try to load PDI Db Connector */
				final ExternalDb pdiDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PDI);

				if (pdiDb == null) {
					LOG.warn("No ExternalDb for PDI Db");
				} else {
					this.pdiDbPropertiesMap = pdiDb.toPropertiesMap(udsDriverType);
					this.pdiDbPropertiesMap.put("ApplicationName", m_applicationName);
				}

				/* Try to load PS Db Connector */
				final ExternalDb psDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PS);

				if (psDb == null) {
					LOG.warn("No ExternalDb for PS Db");
				} else {
					this.psDbPropertiesMap = psDb.toPropertiesMap(udsDriverType);
					this.psDbPropertiesMap.put("ApplicationName", m_applicationName);
				}

			} catch (Exception ex) {
				/* Log and re-throw */
				final String message = "Error initializing UncachedDataStoreConnectorFactory";
				LOG.error(message, ex);

				/*
				 * Close and set to null partially created connectors, so we can
				 * retry to initialize UncachedDataStoreConnectorFactory with a valid
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

	public void initialize(final Map<Object, Object> udsDbProperties) {
		initialize(udsDbProperties, "Proline");
	}
	
	public void initialize(final Map<Object, Object> udsDbProperties, String applicationName) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("UncachedDataStoreConnectorFactory ALREADY initialized");
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
				throw new IllegalStateException("UncachedDataStoreConnectorFactory ALREADY initialized");
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
		checkInitialization();

		return m_udsDbConnector;
	}

	@Override
	public IDatabaseConnector getPdiDbConnector() {
		checkInitialization();
		
		return DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PDI, this.pdiDbPropertiesMap);
	}

	@Override
	public IDatabaseConnector getPsDbConnector() {
		checkInitialization();
		
		return DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PS, this.psDbPropertiesMap);
	}

	@Override
	public IDatabaseConnector getMsiDbConnector(final long projectId) {
		return getProjectDbConnector(projectId, ProlineDatabaseType.MSI, this.m_msiDbPropertiesMaps);
	}
	
	@Override
	public IDatabaseConnector getLcMsDbConnector(final long projectId) {
		return getProjectDbConnector(projectId, ProlineDatabaseType.LCMS, this.m_lcMsDbPropertiesMaps);
	}
	
	private IDatabaseConnector getProjectDbConnector(
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

	/**
	 * Closes all DatabaseConnectors.
	 * <p>
	 * Only call this method on application exiting.
	 */
	@Override
	public void closeAll() {

		synchronized (m_managerLock) {

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
				Map<Object, Object> lcMsDbPropertiesMap = m_lcMsDbPropertiesMaps.get(projectId);
				if (lcMsDbPropertiesMap != null) {
					m_lcMsDbPropertiesMaps.remove(projectId);
				}
			} catch (Exception exClose) {
				LOG.error("Error while clearing LCMS Db connection properties", exClose);
			}
		}
	}
	
	@Override
	public void closeMsiDbConnector(long projectId) {

		synchronized (m_managerLock) {
			try {
				Map<Object, Object> msiDbPropertiesMap = m_msiDbPropertiesMaps.get(projectId);
				if (msiDbPropertiesMap != null) {
					m_msiDbPropertiesMaps.remove(projectId);
				}
			} catch (Exception exClose) {
				LOG.error("Error while clearing MSI Db connection properties", exClose);
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
			throw new IllegalStateException("UncachedDataStoreConnectorFactory NOT yet initialized");
		}

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
