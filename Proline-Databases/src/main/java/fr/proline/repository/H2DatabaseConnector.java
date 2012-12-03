package fr.proline.repository;

import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.util.PropertiesUtils;

public class H2DatabaseConnector extends AbstractDatabaseConnector {

    public static final String PERSISTENCE_JDBC_URL_PROTOCOL = "h2";

    public static final String PERSISTENCE_JDBC_DRIVER_CLASS_NAME = "org.h2.Driver";

    private static final Logger LOG = LoggerFactory.getLogger(H2DatabaseConnector.class);

    public H2DatabaseConnector(final Database database, final Map<Object, Object> properties) {
	super(database, properties);
    }

    @Override
    protected DataSource createDataSource(final Database database, final Map<Object, Object> properties) {

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	final JdbcConnectionPool source = JdbcConnectionPool.create(
		PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY),
		PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_USER_KEY),
		PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_PASSWORD_KEY));

	source.setMaxConnections(DEFAULT_MAX_POOL_CONNECTIONS);

	return source;
    }

    @Override
    protected EntityManagerFactory createEntityManagerFactory(final Database database,
	    final Map<Object, Object> properties, final boolean ormOptimizations) {

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	/* Force JDBC Driver, default Hibernate dialect and default ORM optimizations */
	if (properties.get(PERSISTENCE_JDBC_DRIVER_KEY) == null) {
	    properties.put(PERSISTENCE_JDBC_DRIVER_KEY, PERSISTENCE_JDBC_DRIVER_CLASS_NAME);
	}

	return super.createEntityManagerFactory(database, properties, ormOptimizations);
    }

    @Override
    protected void doClose(final Database database, final DataSource source) {

	if (source instanceof JdbcConnectionPool) {
	    LOG.debug("Disposing H2 JdbcConnectionPool for {}", database);

	    final JdbcConnectionPool h2Source = (JdbcConnectionPool) source;

	    try {
		h2Source.dispose();
	    } catch (Exception exClose) {
		LOG.error("Error disposing H2 JdbcConnectionPool for " + database, exClose);
	    }

	    LOG.debug("Remaining H2 Connections : {}", h2Source.getActiveConnections());
	}

    }

}
