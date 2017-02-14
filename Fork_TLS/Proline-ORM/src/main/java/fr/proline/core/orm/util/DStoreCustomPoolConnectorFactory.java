package fr.proline.core.orm.util;

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
import fr.proline.repository.AbstractDatabaseConnector;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;

//TODO: create abstract class instead of copy-pasting code from DataStoreConnectorFactory...
public class DStoreCustomPoolConnectorFactory implements IDataStoreConnectorFactory {

	/* Constants */
	private static final Logger LOG = LoggerFactory.getLogger(DStoreCustomPoolConnectorFactory.class);

	private static final DStoreCustomPoolConnectorFactory UNIQUE_INSTANCE = new DStoreCustomPoolConnectorFactory();

	/* Instance variables */
	private final Object m_managerLock = new Object();

	/* All instance variables are @GuardedBy("m_managerLock") */
	private IDatabaseConnector m_udsDbConnector;
	private IDatabaseConnector m_pdiDbConnector;
	private IDatabaseConnector m_psDbConnector;

	private final Map<Long, IDatabaseConnector> m_msiDbConnectors = new HashMap<Long, IDatabaseConnector>();
	private final Map<Long, IDatabaseConnector> m_lcMsDbConnectors = new HashMap<Long, IDatabaseConnector>();

	private String m_applicationName;
	private Integer m_projectMaxPoolConnection;

	/* Constructors */
	protected DStoreCustomPoolConnectorFactory() {
	}

	/* Public class methods */
	public static DStoreCustomPoolConnectorFactory getInstance() {
		return UNIQUE_INSTANCE;
	}

	public void initialize(final IDatabaseConnector udsDbConnector) {
		initialize(udsDbConnector, "Proline");
	}
	
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
	public void initialize(final IDatabaseConnector udsDbConnector, String applicationName) {

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
			try {
				m_projectMaxPoolConnection = Integer.parseInt( (String) m_udsDbConnector.getProperty(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY));
			}catch(NumberFormatException nfe){
				m_projectMaxPoolConnection = null;
			}

			
			EntityManager udsEm = null;

			try {
				udsEm = udsDbConnector.createEntityManager();

				final DriverType udsDriverType = udsDbConnector.getDriverType();

				/* Try to load PDI Db Connector */
				final ExternalDb pdiDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PDI);

				if (pdiDb == null) {
					LOG.warn("No ExternalDb for PDI Db");
				} else {
					Map<Object, Object> propertiesMap = pdiDb.toPropertiesMap(udsDriverType);
				    propertiesMap.put("ApplicationName", m_applicationName);
				    if(m_projectMaxPoolConnection != null){
				    	propertiesMap.put(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY, m_projectMaxPoolConnection*2);
				    	LOG.debug("USE PROLINE_MAX_POOL_CONNECTIONS_KEY  "+m_projectMaxPoolConnection);
				    }
					m_pdiDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PDI, propertiesMap);
				}

				/* Try to load PS Db Connector */
				final ExternalDb psDb = ExternalDbRepository.findExternalByType(udsEm, ProlineDatabaseType.PS);

				if (psDb == null) {
					LOG.warn("No ExternalDb for PS Db");
				} else {
					Map<Object, Object> propertiesMap = psDb.toPropertiesMap(udsDriverType);
				    propertiesMap.put("ApplicationName", m_applicationName);
				    if(m_projectMaxPoolConnection != null)
				    	propertiesMap.put(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY, m_projectMaxPoolConnection*2);				
					m_psDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.PS, propertiesMap);
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
				msiDbConnector = createProjectDatabaseConnector(ProlineDatabaseType.MSI, projectId);

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
				lcMsDbConnector = createProjectDatabaseConnector(ProlineDatabaseType.LCMS, projectId);

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

	protected IDatabaseConnector createProjectDatabaseConnector(final ProlineDatabaseType prolineDbType, final long projectId) {
		IDatabaseConnector connector = null;

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
				Map<Object, Object> propertiesMap = externalDb.toPropertiesMap(udsDbConnector.getDriverType());
				propertiesMap.put("ApplicationName", m_applicationName);
				if(m_projectMaxPoolConnection != null)
					propertiesMap.put(AbstractDatabaseConnector.PROLINE_MAX_POOL_CONNECTIONS_KEY, m_projectMaxPoolConnection);
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
	}
}
