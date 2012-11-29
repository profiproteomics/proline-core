package fr.proline.repository;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;

public class ProlineRepository {

    /* Constants */
    private static final Logger LOG = LoggerFactory.getLogger(ProlineRepository.class);

    private static final String MIGRATION_SCRIPTS_DIR = "/dbscripts/";

    /* @GuardedBy("ProlineRepository.class") */
    private static ProlineRepository instance;

    /* Instance variables */
    private final Map<Database, IDatabaseConnector> m_repository = new EnumMap<Database, IDatabaseConnector>(
	    Database.class);

    /* Constructors */
    /**
     * This constructor search for proline_repository.conf and initialize the database connectors from this
     * properties file.
     */
    private ProlineRepository() {
	// TODO
    }

    /**
     * Creates a set of database connections by using the specified prototype. This constructor creates four
     * database connections : UDS, PS, LCMS and PDI.
     * 
     * @param prototype
     */
    private ProlineRepository(final ConnectionPrototype prototype) {
	m_repository.put(Database.UDS, prototype.toConnector(Database.UDS));
	m_repository.put(Database.PDI, prototype.toConnector(Database.PDI));
	m_repository.put(Database.PS, prototype.toConnector(Database.PS));
	m_repository.put(Database.LCMS, prototype.toConnector(Database.LCMS));
    }

    /* Public class methods */
    public static synchronized void initialize(final ConnectionPrototype prototype) {

	if (instance != null) {
	    throw new IllegalStateException("ProlineRepository ALREADY initialized");
	}

	if (prototype == null) {
	    throw new UnsupportedOperationException("Initialization without prototype not supported");
	}

	instance = new ProlineRepository(prototype);
    }

    public static synchronized ProlineRepository getProlineRepositoryInstance() {

	if (instance == null) {
	    throw new IllegalStateException("ProlineRepository NOT yet initialized");
	}

	return instance;
    }

    /* Public methods */
    /**
     * Check if the repository structure needs to be upgraded and performs upgrade if necessary.
     */
    public void upgradeRepositoryStructure() {
	for (final Database db : m_repository.keySet()) {
	    upgradeDatabase(db);
	}
    }

    public void upgradeDatabase(final Database db) {
	LOG.info("Upgrading {} database", db);
	Flyway flyway = new Flyway();
	StringBuilder dir = new StringBuilder(MIGRATION_SCRIPTS_DIR);
	dir.append(db.name().toLowerCase()).append('/').append(retrieveDriverType(m_repository.get(db)))
		.append('/');
	flyway.setLocations(dir.toString());
	flyway.setDataSource(m_repository.get(db).getDataSource());
	flyway.migrate();
    }

    public IDatabaseConnector getConnector(final Database db) {
	return m_repository.get(db);
    }

    public void closeAll() {
	for (final IDatabaseConnector connector : m_repository.values()) {
	    connector.close();
	}
    }

    /* Private methods */
    /**
     * Creates a new MSIdb instance, register this database within the UDS and returns a DatabaseConnectorto
     * the MSI.
     * 
     * @return a DatabaseConnector to perform connection on the newly created MSI or null if the creation
     *         failed.
     */
    private IDatabaseConnector createMSIdbInstance() {
	// TODO
	return null;
    }

    private static String retrieveDriverType(final IDatabaseConnector connector) {
	String result = null;

	if (connector instanceof H2DatabaseConnector) {
	    result = "h2";
	} else if (connector instanceof PostgresDatabaseConnector) {
	    result = "postgresql";
	} else if (connector instanceof SQLiteDatabaseConnector) {
	    result = "sqlite";
	}

	return result;
    }

}
