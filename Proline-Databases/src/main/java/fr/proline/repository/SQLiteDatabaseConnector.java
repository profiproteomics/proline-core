package fr.proline.repository;

import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.sqlite.SQLiteDataSource;

import fr.proline.repository.dialect.SQLiteDialect;
import fr.proline.util.PropertiesUtils;

public class SQLiteDatabaseConnector extends AbstractDatabaseConnector {

    public static final String PERSISTENCE_JDBC_URL_PROTOCOL = "sqlite";

    public static final String PERSISTENCE_JDBC_DRIVER_CLASS_NAME = "org.sqlite.JDBC";

    public SQLiteDatabaseConnector(final Database database, final Map<Object, Object> properties) {
	super(database, properties);
    }

    @Override
    protected DataSource createDataSource(final Database database, final Map<Object, Object> properties) {

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	final SQLiteDataSource source = new SQLiteDataSource();
	source.setUrl(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY));

	return source;
    }

    @Override
    protected EntityManagerFactory createEntityManagerFactory(final Database database,
	    final Map<Object, Object> properties, final boolean ormOptimizations) {

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	/* Force JDBC Driver, SQLiteDialect custom Hibernate dialect and NO ORM optimizations */
	if (properties.get(PERSISTENCE_JDBC_DRIVER_KEY) == null) {
	    properties.put(PERSISTENCE_JDBC_DRIVER_KEY, PERSISTENCE_JDBC_DRIVER_CLASS_NAME);
	}

	if (properties.get(HIBERNATE_DIALECT_KEY) == null) {
	    properties.put(HIBERNATE_DIALECT_KEY, SQLiteDialect.class.getName());
	}

	return super.createEntityManagerFactory(database, properties, false);
    }

}
