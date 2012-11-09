package fr.proline.repository;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;

public class ProlineRepository {

	private static final String MIGRATION_SCRIPTS_DIR = "/dbscripts/";
	private static final Logger logger = LoggerFactory.getLogger(ProlineRepository.class);

	public enum Databases {
		UDS, PS, PDI, MSI, LCMS
	};

	public enum DriverType {
		H2("org.h2.Driver", "org.hibernate.dialect.H2Dialect"), POSTGRESQL("org.postgresql.Driver",
				"org.hibernate.dialect.PostgreSQLDialect"), SQLITE("org.sqlite.JDBC",
				"fr.proline.core.orm.utils.SQLiteDialect");

		String driver;
		String JPADialect;

		DriverType(String driver, String dialect) {
			this.driver = driver;
			this.JPADialect = dialect;
		}
		
		public String getJPADriver(){
			return JPADialect;
		}
		
		public String getDriverClassName(){
			return driver;
		}
		
		
	};

	private static ProlineRepository instance;

	public static ProlineRepository getRepositoryManager(ConnectionPrototype prototype) throws Exception {
		if (instance == null) {
			instance = (prototype == null) ? new ProlineRepository() : new ProlineRepository(prototype);
		} else if (prototype != null) {
			throw new UnsupportedOperationException();
		}
		return instance;
	}

	private Map<Databases, DatabaseConnector> repository = new EnumMap<ProlineRepository.Databases, DatabaseConnector>(
			Databases.class);

	/**
	 * This constructor search for proline_repository.conf and initialize the database connectors from this properties
	 * file.
	 */
	private ProlineRepository() {
		// TODO
	}

	/**
	 * Creates a set of database connections by using the specified prototype. This constructor creates four database
	 * connections : UDS, PS, LCMS and PDI.
	 * 
	 * @param prototype
	 */
	private ProlineRepository(ConnectionPrototype prototype) throws Exception {
		repository.put(Databases.UDS, prototype.toConnector(Databases.UDS));
		repository.put(Databases.PDI, prototype.toConnector(Databases.PDI));
		repository.put(Databases.PS, prototype.toConnector(Databases.PS));
		repository.put(Databases.LCMS, prototype.toConnector(Databases.LCMS));
	}

	/**
	 * Check if the repository structure needs to be upgraded and performs upgrade if necessary.
	 */
	public void upgradeRepositoryStructure() {
		for (Databases db : repository.keySet()) {
			upgradeDatabase(db);
		}
	}

	public void upgradeDatabase(Databases db) {
		logger.info("Upgrading database " + db.toString());
		Flyway flyway = new Flyway();
		StringBuilder dir = new StringBuilder(MIGRATION_SCRIPTS_DIR);
		dir.append(db.name().toLowerCase()).append('/').append(repository.get(db).getDriverType().name().toLowerCase())
				.append('/');
		flyway.setLocations(dir.toString());
		flyway.setDataSource(repository.get(db).getDataSource());
		flyway.migrate();
	}

	public DatabaseConnector getConnector(Databases db) {
		return repository.get(db);
	}

	public void openAll() {
		for (Databases db : repository.keySet()) {
			try {
				repository.get(db).getConnection();
			} catch (Exception e) {
				logger.error("Error while connecting to " + db.name(), e);
			}
		}
	}

	public void closeAll() throws Exception {
		for (DatabaseConnector connector : repository.values()) {
			connector.closeAll();
		}
	}

	/**
	 * Creates a new MSIdb instance, register this database within the UDS and returns a DatabaseConnectorto the MSI.
	 * 
	 * @return a DatabaseConnector to perform connection on the newly created MSI or null if the creation failed.
	 */
	private DatabaseConnector createMSIdbInstance() {
		//TODO
		return null;
	}

}
