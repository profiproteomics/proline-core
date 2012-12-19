package fr.proline.core.orm.util;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.repository.ExternalDbRepository;
import fr.proline.repository.Database;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DatabaseUpgrader;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.util.StringUtils;

public class DatabaseManager {

    /* Constants */
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private static final DatabaseManager UNIQUE_INSTANCE = new DatabaseManager();

    /* Instance variables */
    private final Object m_managerLock = new Object();

    /* All instance variables are @GuardedBy("m_managerLock") */
    private IDatabaseConnector m_udsDbConnector;
    private IDatabaseConnector m_pdiDbConnector;
    private IDatabaseConnector m_psDbConnector;

    private final Map<Integer, IDatabaseConnector> m_msiDbConnectors = new HashMap<Integer, IDatabaseConnector>();
    private final Map<Integer, IDatabaseConnector> m_lcMsDbConnectors = new HashMap<Integer, IDatabaseConnector>();

    /* Constructors */
    protected DatabaseManager() {
    }

    /* Public class methods */
    public static DatabaseManager getInstance() {
	return UNIQUE_INSTANCE;
    }

    /* Public methods */
    public void initialize(final IDatabaseConnector udsDbConnector) {

	synchronized (m_managerLock) {

	    if (isInitialized()) {
		throw new IllegalStateException("DatabaseManager ALREADY initialized");
	    }

	    if (udsDbConnector == null) {
		throw new UnsupportedOperationException("UdsDbConnector is null");
	    }

	    m_udsDbConnector = udsDbConnector;

	    DatabaseUpgrader.upgradeDatabase(udsDbConnector);

	    final EntityManagerFactory udsEMF = udsDbConnector.getEntityManagerFactory();

	    EntityManager udsEm = udsEMF.createEntityManager();

	    try {
		final DriverType udsDriverType = udsDbConnector.getDriverType();

		/* Try to load PDI Db Connector */
		final ExternalDb pdiDb = ExternalDbRepository.findExternalByType(udsEm, Database.PDI);

		if (pdiDb == null) {
		    LOG.warn("No ExternalDb for PDI Db");
		} else {
		    m_pdiDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.PDI,
			    pdiDb.toPropertiesMap(udsDriverType));

		    DatabaseUpgrader.upgradeDatabase(m_pdiDbConnector);
		}

		/* Try to load PS Db Connector */
		final ExternalDb psDb = ExternalDbRepository.findExternalByType(udsEm, Database.PS);

		if (psDb == null) {
		    LOG.warn("No ExternalDb for PS Db");
		} else {
		    m_psDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.PS,
			    psDb.toPropertiesMap(udsDriverType));

		    DatabaseUpgrader.upgradeDatabase(m_psDbConnector);
		}
		
	    } catch( Throwable e ) {
	      LOG.error( e.getMessage() );
	    } finally {

		try {
		    udsEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing UDS Db EntityManager", exClose);
		}

	    }

	} // End of synchronized block on m_managerLock

    }

    public void initialize(final Map<Object, Object> udsDbProperties) {

	synchronized (m_managerLock) {

	    if (isInitialized()) {
		throw new IllegalStateException("DatabaseManager ALREADY initialized");
	    }

	    if (udsDbProperties == null) {
		throw new IllegalArgumentException("UdsDbProperties Map is null");
	    }

	    initialize(DatabaseConnectorFactory
		    .createDatabaseConnectorInstance(Database.UDS, udsDbProperties));
	} // End of synchronized block on m_managerLock

    }

    public void initialize(final String udsDbPropertiesFileName) {

	synchronized (m_managerLock) {

	    if (isInitialized()) {
		throw new IllegalStateException("DatabaseManager ALREADY initialized");
	    }

	    if (StringUtils.isEmpty(udsDbPropertiesFileName)) {
		throw new IllegalArgumentException("Invalid udsDbPropertiesFileName");
	    }

	    initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.UDS,
		    udsDbPropertiesFileName));

	} // End of synchronized block on m_managerLock

    }

    public boolean isInitialized() {
	boolean result;

	synchronized (m_managerLock) {
	    result = (m_udsDbConnector != null);
	} // End of synchronized block on m_managerLock

	return result;
    }

    public IDatabaseConnector getUdsDbConnector() {
	IDatabaseConnector udsDbConnector;

	synchronized (m_managerLock) {
	    checkInitialization();

	    udsDbConnector = m_udsDbConnector;
	} // End of synchronized block on m_managerLock

	return udsDbConnector;
    }

    public IDatabaseConnector getPdiDbConnector() {
	IDatabaseConnector pdiDbConnector;

	synchronized (m_managerLock) {
	    checkInitialization();

	    pdiDbConnector = m_pdiDbConnector;
	} // End of synchronized block on m_managerLock

	return pdiDbConnector;
    }

    public IDatabaseConnector getPsDbConnector() {
	IDatabaseConnector psDbConnector;

	synchronized (m_managerLock) {
	    checkInitialization();

	    psDbConnector = m_psDbConnector;
	} // End of synchronized block on m_managerLock

	return psDbConnector;
    }

    public IDatabaseConnector getMsiDbConnector(final int projectId) {
	IDatabaseConnector msiDbConnector = null;

	synchronized (m_managerLock) {
	    checkInitialization();

	    final Integer key = Integer.valueOf(projectId);

	    msiDbConnector = m_msiDbConnectors.get(key);

	    if (msiDbConnector == null) {
		msiDbConnector = createProjectDatabaseConnector(Database.MSI, projectId);

		if (msiDbConnector != null) {
		    m_msiDbConnectors.put(key, msiDbConnector);
		}

	    }

	} // End of synchronized block on m_managerLock

	return msiDbConnector;
    }

    public IDatabaseConnector getLcMsDbConnector(final int projectId) {
	IDatabaseConnector lcMsDbConnector = null;

	synchronized (m_managerLock) {
	    checkInitialization();

	    final Integer key = Integer.valueOf(projectId);

	    lcMsDbConnector = m_lcMsDbConnectors.get(key);

	    if (lcMsDbConnector == null) {
		lcMsDbConnector = createProjectDatabaseConnector(Database.LCMS, projectId);

		if (lcMsDbConnector != null) {
		    m_lcMsDbConnectors.put(key, lcMsDbConnector);
		}

	    }

	} // End of synchronized block on m_managerLock

	return lcMsDbConnector;
    }

    public void closeAll() {

	synchronized (m_managerLock) {

	    if (m_udsDbConnector != null) {
		try {
		    m_udsDbConnector.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing UDS Db Connector", exClose);
		}
	    }

	    if (m_pdiDbConnector != null) {
		try {
		    m_pdiDbConnector.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PDI Db Connector", exClose);
		}
	    }

	    if (m_psDbConnector != null) {
		try {
		    m_psDbConnector.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS Db Connector", exClose);
		}
	    }

	    for (final IDatabaseConnector msiDbConnector : m_msiDbConnectors.values()) {
		try {
		    msiDbConnector.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI Db Connector", exClose);
		}
	    }

	    for (final IDatabaseConnector lcMsDbConnector : m_lcMsDbConnectors.values()) {
		try {
		    lcMsDbConnector.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing LCMS Db Connector", exClose);
		}
	    }

	} // End of synchronized block on m_managerLock

    }

    private void checkInitialization() {

	if (!isInitialized()) {
	    throw new IllegalStateException("DatabaseManager NOT yet initialized");
	}

    }

    private IDatabaseConnector createProjectDatabaseConnector(final Database database, final int projectId) {
	IDatabaseConnector connector = null;

	final IDatabaseConnector udsDbConnector = getUdsDbConnector();

	final EntityManagerFactory udsEMF = udsDbConnector.getEntityManagerFactory();

	EntityManager udsEm = udsEMF.createEntityManager();

	try {
	    final Project project = udsEm.find(Project.class, projectId);

	    if (project == null) {
		throw new IllegalArgumentException("Project #" + projectId + " NOT found in UDS Db");
	    }

	    final ExternalDb externalDb = ExternalDbRepository.findExternalByTypeAndProject(udsEm, database,
		    project);

	    if (externalDb == null) {
		LOG.warn("No ExternalDb for {} Db of project #{}", database, projectId);
	    } else {
		connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(database,
			externalDb.toPropertiesMap(udsDbConnector.getDriverType()));

		DatabaseUpgrader.upgradeDatabase(connector);
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
