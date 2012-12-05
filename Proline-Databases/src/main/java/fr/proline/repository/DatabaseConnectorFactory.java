package fr.proline.repository;

import static fr.proline.repository.AbstractDatabaseConnector.JDBC_SCHEME;
import static fr.proline.repository.AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY;

import java.util.Map;

import fr.proline.util.PropertiesUtils;
import fr.proline.util.StringUtils;

public final class DatabaseConnectorFactory {

    /* Private constructor (Utility class) */
    private DatabaseConnectorFactory() {
    }

    /* Public class methods */
    public static IDatabaseConnector createDatabaseConnectorInstance(final Database database,
	    final Map<Object, Object> properties) {

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

	/* Parametric factory based on normalizedDatabaseURL : add new supported Database protocols here */
	if (normalizedDatabaseURL.contains(JDBC_SCHEME + ':' + DriverType.POSTGRESQL.getJdbcURLProtocol())) {
	    result = new PostgresDatabaseConnector(database, properties);
	} else if (normalizedDatabaseURL.contains(JDBC_SCHEME + ':' + DriverType.SQLITE.getJdbcURLProtocol())) {
	    result = new SQLiteDatabaseConnector(database, properties);
	} else if (normalizedDatabaseURL.contains(JDBC_SCHEME + ':' + DriverType.H2.getJdbcURLProtocol())) {
	    result = new H2DatabaseConnector(database, properties);
	} else {
	    throw new IllegalArgumentException("Unknown database protocol");
	}

	return result;
    }

    public static IDatabaseConnector createDatabaseConnectorInstance(final Database database,
	    final String propertiesFileName) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (StringUtils.isEmpty(propertiesFileName)) {
	    throw new IllegalArgumentException("Invalid propertiesFileName");
	}

	return createDatabaseConnectorInstance(database, PropertiesUtils.loadProperties(propertiesFileName));
    }

}
