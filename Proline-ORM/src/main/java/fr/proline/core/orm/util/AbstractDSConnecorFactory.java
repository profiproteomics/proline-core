package fr.proline.core.orm.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.profi.util.StringUtils;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.IDataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
	
public abstract class AbstractDSConnecorFactory  implements IDataStoreConnectorFactory {
	
	protected static final Logger LOG = LoggerFactory.getLogger(AbstractDSConnecorFactory.class);

	/* Instance variables */
	protected final Object m_managerLock = new Object();
	
	/* All instance variables are @GuardedBy("m_managerLock") */
	protected IDatabaseConnector m_udsDbConnector;
	protected IDatabaseConnector m_pdiDbConnector;
	protected IDatabaseConnector m_psDbConnector;

	protected final Map<Long, IDatabaseConnector> m_msiDbConnectors = new HashMap<Long, IDatabaseConnector>();
	protected final Map<Long, IDatabaseConnector> m_lcMsDbConnectors = new HashMap<Long, IDatabaseConnector>();
	protected final Map<Long, Map<Object, Object>> m_msiDbPropertiesMaps = new HashMap<Long, Map<Object, Object>>();
	protected final Map<Long, Map<Object, Object>> m_lcMsDbPropertiesMaps = new HashMap<Long, Map<Object, Object>>();

	/*** ABSTRATC METHODS ***/
	
	/**
	 * Initializes this <code>IDataStoreConnectorFactory</code> implementation instance from an
	 * existing UDS Db DatabaseConnector using ExternalDb entities.
	 * <p>
	 * initialize() must be called only once.
	 * 
	 * This method is finally called by all other initialize methods
	 * 
	 * @param udsDbConnector
	 *            DatabaseConnector to a valid UDS Db (must not be
	 *            <code>null</code>).
	 *            )
	 * @param applicationName : String use to identify application which open connection
	 */
	public abstract void initialize(final IDatabaseConnector udsDbConnector, String applicationName);	
	
	/***
	 * Create a DatabaseConnector for specified database
	 * 
	 * @param projectId : Project ID to create connector for
	 * @param prolineDbType : ProlineDatabaseType database to connect to (MSI, LCMS). Should be a project specific database
	 * @param propertiesMaps : Connection properties Map
	 * @return created IDatabaseConnector
	 */
	protected abstract IDatabaseConnector createProjectDatabaseConnector(final long projectId, final ProlineDatabaseType prolineDbType, final Map<Long, Map<Object, Object>> propertiesMaps	);

	
	public void initialize(final IDatabaseConnector udsDbConnector) {
		initialize(udsDbConnector, "Proline");
	}
	
	public void initialize(final Map<Object, Object> udsDbProperties) {
		initialize(udsDbProperties, "Proline");
	}
	
	public void initialize(final Map<Object, Object> udsDbProperties, String applicationName) {

		synchronized (m_managerLock) {

			if (isInitialized()) {
				throw new IllegalStateException("This IDataStoreConnectorFactory ALREADY initialized");
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

			initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(ProlineDatabaseType.UDS, udsDbPropertiesFileName), applicationName);
//			initialize(createDatabaseConnector(ProlineDatabaseType.UDS, udsDbPropertiesFileName), applicationName);
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
	public IDatabaseConnector getMsiDbConnector(long projectId) {
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
	public IDatabaseConnector getLcMsDbConnector(long projectId) {
		IDatabaseConnector lcMsDbConnector = null;

		synchronized (m_managerLock) {
			checkInitialization();

			final Long key = Long.valueOf(projectId);

			lcMsDbConnector = m_lcMsDbConnectors.get(key);

			if (lcMsDbConnector == null) {
				lcMsDbConnector = createProjectDatabaseConnector(projectId, ProlineDatabaseType.LCMS,m_lcMsDbPropertiesMaps );

				if (lcMsDbConnector != null) {
					m_lcMsDbConnectors.put(key, lcMsDbConnector);
				}

			}

		} // End of synchronized block on m_managerLock

		return lcMsDbConnector;
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
	
	/* Private methods */
	protected void checkInitialization() {

		if (!isInitialized()) {
			throw new IllegalStateException("DataStoreConnectorFactory NOT yet initialized");
		}

	}
	

}
