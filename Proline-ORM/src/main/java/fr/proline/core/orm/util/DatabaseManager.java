package fr.proline.core.orm.util;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;

import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.repository.ExternalDbRepository;
import fr.proline.repository.Database;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.util.StringUtils;

public class DatabaseManager {

    /* Constants */
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String MIGRATION_SCRIPTS_DIR = "dbscripts/";

    private static final DatabaseManager UNIQUE_INSTANCE = new DatabaseManager();

    /* Instance variables */
    private IDatabaseConnector m_udsDbConnector;
    private IDatabaseConnector m_pdiDbConnector;
    private IDatabaseConnector m_psDbConnector;

    private final Map<Integer, IDatabaseConnector> m_msiDbConnectors = new HashMap<Integer, IDatabaseConnector>();
    private final Map<Integer, IDatabaseConnector> m_lcMsDbConnectors = new HashMap<Integer, IDatabaseConnector>();

    /* Private constructor */
    private DatabaseManager() {
    }

    /* Public class methods */
    public static DatabaseManager getInstance() {
	return UNIQUE_INSTANCE;
    }

    /* Public methods */
    public synchronized void initialize(final IDatabaseConnector udsDbConnector) {

	if (isInitialized()) {
	    throw new IllegalStateException("DatabaseManager ALREADY initialized");
	}

	if (udsDbConnector == null) {
	    throw new UnsupportedOperationException("UdsDbConnector is null");
	}

	m_udsDbConnector = udsDbConnector;

	upgradeDatabase(udsDbConnector);

	final EntityManagerFactory udsEMF = udsDbConnector.getEntityManagerFactory();

	EntityManager udsEm = udsEMF.createEntityManager();

	try {
	    final ExternalDbRepository externalDbRepo = new ExternalDbRepository(udsEm);

	    final DriverType udsDriverType = udsDbConnector.getDriverType();

	    /* Try to load PDI Db Connector */
	    final ExternalDb pdiDb = externalDbRepo.findExternalByType(Database.PDI);

	    if (pdiDb == null) {
		LOG.warn("No ExternalDb for PDI Db");
	    } else {
		m_pdiDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.PDI,
			pdiDb.toPropertiesMap(udsDriverType));

		upgradeDatabase(m_pdiDbConnector);
	    }

	    /* Try to load PS Db Connector */
	    final ExternalDb psDb = externalDbRepo.findExternalByType(Database.PS);

	    if (psDb == null) {
		LOG.warn("No ExternalDb for PS Db");
	    } else {
		m_psDbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.PS,
			psDb.toPropertiesMap(udsDriverType));

		upgradeDatabase(m_psDbConnector);
	    }

	} finally {

	    try {
		udsEm.close();
	    } catch (Exception exClose) {
		LOG.error("Error closing UDS Db EntityManager", exClose);
	    }

	}

    }

    public synchronized void initialize(final Map<Object, Object> udsDbProperties) {

	if (isInitialized()) {
	    throw new IllegalStateException("DatabaseManager ALREADY initialized");
	}

	if (udsDbProperties == null) {
	    throw new IllegalArgumentException("UdsDbProperties Map is null");
	}

	initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.UDS, udsDbProperties));
    }

    public synchronized void initialize(final String udsDbPropertiesFileName) {

	if (isInitialized()) {
	    throw new IllegalStateException("DatabaseManager ALREADY initialized");
	}

	if (StringUtils.isEmpty(udsDbPropertiesFileName)) {
	    throw new IllegalArgumentException("Invalid udsDbPropertiesFileName");
	}

	initialize(DatabaseConnectorFactory.createDatabaseConnectorInstance(Database.UDS,
		udsDbPropertiesFileName));
    }

    public synchronized boolean isInitialized() {
	return (m_udsDbConnector != null);
    }

    public synchronized IDatabaseConnector getUdsDbConnector() {
	checkInitialization();

	return m_udsDbConnector;
    }

    public synchronized IDatabaseConnector getPdiDbConnector() {
	checkInitialization();

	return m_pdiDbConnector;
    }

    public synchronized IDatabaseConnector getPsDbConnector() {
	checkInitialization();

	return m_psDbConnector;
    }

    public synchronized IDatabaseConnector getMsiDbConnector(final int projectId) {
	checkInitialization();

	final Integer key = Integer.valueOf(projectId);

	IDatabaseConnector msiDbConnector = m_msiDbConnectors.get(key);

	if (msiDbConnector == null) {
	    msiDbConnector = createProjectDatabaseConnector(Database.MSI, projectId);

	    if (msiDbConnector != null) {
		m_msiDbConnectors.put(key, msiDbConnector);
	    }

	}

	return msiDbConnector;
    }

    public synchronized IDatabaseConnector getLcMsDbConnector(final int projectId) {
	checkInitialization();

	final Integer key = Integer.valueOf(projectId);

	IDatabaseConnector lcMsDbConnector = m_lcMsDbConnectors.get(key);

	if (lcMsDbConnector == null) {
	    lcMsDbConnector = createProjectDatabaseConnector(Database.LCMS, projectId);

	    if (lcMsDbConnector != null) {
		m_lcMsDbConnectors.put(key, lcMsDbConnector);
	    }

	}

	return lcMsDbConnector;
    }

    public synchronized void closeAll() {

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

	    final ExternalDbRepository externalDbRepo = new ExternalDbRepository(udsEm);

	    final ExternalDb externalDb = externalDbRepo.findExternalByTypeAndProject(database, project);

	    if (externalDb == null) {
		LOG.warn("No ExternalDb for {} Db of project #{}", database, projectId);
	    } else {
		connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(database,
			externalDb.toPropertiesMap(udsDbConnector.getDriverType()));

		upgradeDatabase(connector);
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

    private static void upgradeDatabase(final IDatabaseConnector dbConnector) {
	assert (dbConnector != null) : "upgradeDatabase() dbConnector is null";

	final Database db = dbConnector.getDatabase();

	final StringBuilder migrationScriptsLocation = new StringBuilder(MIGRATION_SCRIPTS_DIR);
	migrationScriptsLocation.append(db.name().toLowerCase()).append('/');
	migrationScriptsLocation.append(dbConnector.getDriverType().name().toLowerCase()).append('/');

	LOG.debug("Upgrading {} Db with Flyway, migrationScriptsLocation [{}]", db, migrationScriptsLocation);

	final Flyway flyway = new Flyway();

	flyway.setLocations(migrationScriptsLocation.toString());
	flyway.setDataSource(dbConnector.getDataSource());

	final int migrationsCount = flyway.migrate();

	LOG.info("Flyway applies {} migrations", migrationsCount);
    }

}
