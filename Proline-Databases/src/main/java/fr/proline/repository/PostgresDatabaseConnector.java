package fr.proline.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.postgresql.ds.PGPoolingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.dialect.TableNameSequencePostgresDialect;
import fr.proline.util.PropertiesUtils;
import fr.proline.util.StringUtils;

public class PostgresDatabaseConnector extends AbstractDatabaseConnector {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresDatabaseConnector.class);

    private static final String HIBERNATE_CONNECTION_KEEPALIVE_KEY = "hibernate.connection.tcpKeepAlive";

    private static final String POSTGRESQL_SCHEME = JDBC_SCHEME + ':'
	    + DriverType.POSTGRESQL.getJdbcURLProtocol() + ':';

    private static final AtomicLong NAME_SEQUENCE = new AtomicLong(0L);

    public PostgresDatabaseConnector(final ProlineDatabaseType database, final Map<Object, Object> properties) {
	super(database, properties);
    }

    @Override
    public DriverType getDriverType() {
	return DriverType.POSTGRESQL;
    }

    @Override
    protected DataSource createDataSource(final String ident, final Map<Object, Object> properties) {

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	/* Parse PostrgeSQL Database URI */
	final String rawDatabaseURL = PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY);

	if (StringUtils.isEmpty(rawDatabaseURL)) {
	    throw new IllegalArgumentException("Invalid database URL");
	}

	/* TODO LMN : Implement a real JDBC URL Parser instead of fake HTTP URI ? */
	final URI fakeURI = buildFakeURI(rawDatabaseURL);

	if (fakeURI == null) {
	    throw new IllegalArgumentException("Invalid PostgreSQL database URI");
	}

	final PGPoolingDataSource source = new PGPoolingDataSource();

	final String datasourceName = ident + '_' + NAME_SEQUENCE.getAndIncrement();
	source.setDataSourceName(datasourceName);

	final String serverName = fakeURI.getHost();
	if (serverName != null) {
	    source.setServerName(serverName);
	}

	final int serverPort = fakeURI.getPort();
	if (serverPort != -1) {
	    source.setPortNumber(serverPort);
	}

	final String databasePath = extractDatabaseName(fakeURI.getPath());
	if (databasePath != null) {
	    source.setDatabaseName(databasePath);
	}

	source.setUser(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_USER_KEY));
	source.setPassword(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_PASSWORD_KEY));

	source.setMaxConnections(DEFAULT_MAX_POOL_CONNECTIONS);

	/* Force TCP keepalive on raw SQL JDBC connections */
	source.setTcpKeepAlive(true);

	return source;
    }

    @Override
    protected EntityManagerFactory createEntityManagerFactory(final ProlineDatabaseType database,
	    final Map<Object, Object> properties, final boolean ormOptimizations) {

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	/* Force TableNameSequencePostgresDialect custom Hibernate dialect and default ORM optimizations. */
	if (properties.get(HIBERNATE_DIALECT_KEY) == null) {
	    properties.put(HIBERNATE_DIALECT_KEY, TableNameSequencePostgresDialect.class.getName());
	}

	/* Force TCP keepalive on EntityManager connections */
	if (properties.get(HIBERNATE_CONNECTION_KEEPALIVE_KEY) == null) {
	    properties.put(HIBERNATE_CONNECTION_KEEPALIVE_KEY, "true");
	}

	return super.createEntityManagerFactory(database, properties, ormOptimizations);
    }

    @Override
    protected void doClose(final String ident, final DataSource source) {

	if (source instanceof PGPoolingDataSource) {
	    LOG.debug("Closing PGPoolingDataSource for [{}]", ident);

	    try {
		((PGPoolingDataSource) source).close();
	    } catch (Exception exClose) {
		LOG.error("Error closing PGPoolingDataSource for [" + ident + ']', exClose);
	    }

	}

    }

    /**
     * Tries to build a <em>fake</em> HTTP URI from the given raw PostgreSQL database URL to parse host, port,
     * database (path) name...
     * 
     * @param rawDatabaseURL
     *            PostgreSQL database URL as retrieved from "javax.persistence.jdbc.url" property. Must be a
     *            valid database URL.
     * @return a <em>fake</em> HTTP URI or <code>null</code> if <code>rawDatabaseURL</code> cannot be parsed.
     */
    private static URI buildFakeURI(final String rawDatabaseURL) {
	assert (!StringUtils.isEmpty(rawDatabaseURL)) : "buildFakeURI() invalid databaseURL";

	URI result = null;

	/* URL should be in lower case */
	final String normalizedDatabaseURL = rawDatabaseURL.toLowerCase();

	final int index = normalizedDatabaseURL.indexOf(POSTGRESQL_SCHEME);

	if (index != -1) {
	    final int start = index + POSTGRESQL_SCHEME.length();
	    final int length = rawDatabaseURL.length();

	    if (start < length) {
		final String fakeURI = "http:" + rawDatabaseURL.substring(start, length);

		try {
		    result = new URI(fakeURI);
		} catch (URISyntaxException ex) {
		    LOG.error("Unable to parse [" + fakeURI + "] as URI", ex);
		}

	    }

	}

	return result;
    }

    private static String extractDatabaseName(final String rawDatabasePath) {
	String result = null;

	if (!StringUtils.isEmpty(rawDatabasePath)) {
	    /* URI path parts are separated by one or more '/' */
	    final String[] parts = rawDatabasePath.split("/");

	    for (final String part : parts) {

		if (!StringUtils.isEmpty(part)) {
		    result = part; // First non empty path part

		    break;
		}

	    }

	}

	return result;
    }

}
