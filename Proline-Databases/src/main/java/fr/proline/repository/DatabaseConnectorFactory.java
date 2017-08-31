package fr.proline.repository;

import static fr.proline.repository.AbstractDatabaseConnector.JDBC_SCHEME;
import static fr.proline.repository.AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.profi.util.PropertiesUtils;
import fr.profi.util.StringUtils;

public final class DatabaseConnectorFactory {

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnectorFactory.class);

	/* Private constructor (Utility class) */
	private DatabaseConnectorFactory() {
	}

	/* Public class methods */
	public static IDatabaseConnector createDatabaseConnectorInstance(
		final ProlineDatabaseType database,
		final Map<Object, Object> properties, 
		IDatabaseConnector.ConnectionPoolType poolType) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		final String databaseURL = PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY);

		if (databaseURL == null) {
			throw new IllegalArgumentException("Invalid database URL");
		}

		IDatabaseConnector result = null;

		/* URL should be in lower case */
		final String normalizedDatabaseURL = databaseURL.toLowerCase();

		// uncomment to see all sql statements
		//properties.put("hibernate.show_sql", "true");

		/* Parametric factory based on normalizedDatabaseURL : add new supported Database protocols here */
		if (normalizedDatabaseURL.contains(JDBC_SCHEME + ':' + DriverType.POSTGRESQL.getJdbcURLProtocol())) {
			result = new PostgresDatabaseConnector(database, properties, poolType );
		} else if (normalizedDatabaseURL.contains(JDBC_SCHEME + ':' + DriverType.SQLITE.getJdbcURLProtocol())) {
			result = new SQLiteDatabaseConnector(database, properties);
		} else if (normalizedDatabaseURL.contains(JDBC_SCHEME + ':' + DriverType.H2.getJdbcURLProtocol())) {
			result = new H2DatabaseConnector(database, properties);
		} else {
			throw new IllegalArgumentException("Unknown database protocol");
		}

		return result;
	}

	public static IDatabaseConnector createDatabaseConnectorInstance(
		final ProlineDatabaseType database,
		final String propertiesFileName) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (StringUtils.isEmpty(propertiesFileName)) {
			throw new IllegalArgumentException("Invalid propertiesFileName");
		}

		return createDatabaseConnectorInstance(database, PropertiesUtils.loadProperties(propertiesFileName), IDatabaseConnector.DEFAULT_POOL_TYPE);//IDatabaseConnector.ConnectionPoolType.HIGH_PERF_POOL_MANAGEMENT);
	}
	
	public static IDatabaseConnector createDatabaseConnectorInstance(
		final ProlineDatabaseType database,
		final String propertiesFileName, 
		IDatabaseConnector.ConnectionPoolType poolType) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (StringUtils.isEmpty(propertiesFileName)) {
			throw new IllegalArgumentException("Invalid propertiesFileName");
		}

		return createDatabaseConnectorInstance(database, PropertiesUtils.loadProperties(propertiesFileName), poolType);
	}

	public static IDatabaseConnector createDatabaseConnectorInstance(
		final ProlineDatabaseType database,
		final Map<Object, Object> properties) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		return createDatabaseConnectorInstance(database, properties, IDatabaseConnector.DEFAULT_POOL_TYPE);//IDatabaseConnector.ConnectionPoolType.HIGH_PERF_POOL_MANAGEMENT);
	}
}
