package fr.proline.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.postgresql.ds.PGPoolingDataSource;
import org.postgresql.ds.PGSimpleDataSource;
//import org.postgresql.ds.PGPoolingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import fr.profi.util.PropertiesUtils;
import fr.profi.util.StringUtils;
import fr.proline.repository.dialect.TableNameSequencePostgresDialect;

public class PostgresDatabaseConnector extends AbstractDatabaseConnector {

	private static final Logger LOG = LoggerFactory.getLogger(PostgresDatabaseConnector.class);

	private static final String HIBERNATE_CONNECTION_KEEPALIVE_KEY = "hibernate.connection.tcpKeepAlive";

	private static final String POSTGRESQL_SCHEME = JDBC_SCHEME + ':' + DriverType.POSTGRESQL.getJdbcURLProtocol() + ':';

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
		long start = System.currentTimeMillis();
		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		/* Parse PostrgeSQL Database URI */
		final String rawDatabaseURL = PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY);

		if (StringUtils.isEmpty(rawDatabaseURL)) {
			throw new IllegalArgumentException("Invalid database URL");
		}

		/*
		 * TODO LMN : Implement a real JDBC URL Parser instead of fake HTTP URI
		 * ?
		 */
		final URI fakeURI = buildFakeURI(rawDatabaseURL);

		if (fakeURI == null) {
			throw new IllegalArgumentException("Invalid PostgreSQL database URI");
		}

		// WARN : if you change the line below, you must change the doClose implementation !! 
		Integer maxConnection = DEFAULT_MAX_POOL_CONNECTIONS;
		if (properties.containsKey(PROLINE_MAX_POOL_CONNECTIONS_KEY)) {
			if (Integer.class.isInstance(properties.get(PROLINE_MAX_POOL_CONNECTIONS_KEY)))
				maxConnection = (Integer) properties.get(PROLINE_MAX_POOL_CONNECTIONS_KEY);
			else {
				try {
					maxConnection = Integer.parseInt((String) properties.get(PROLINE_MAX_POOL_CONNECTIONS_KEY));
				} catch (NumberFormatException nfe) {
					maxConnection = DEFAULT_MAX_POOL_CONNECTIONS;
				}
			}
		}

		final DataSource source = (maxConnection > 1) ? buildC3P0DataSource(ident, properties, fakeURI, maxConnection)
			: buildSimpleDataSource(ident, properties, fakeURI);

		LOG.info("Pool creation duration = "+(System.currentTimeMillis() - start)+" ms for "+getProlineDatabaseType());
		return source;
	}

	private DataSource buildPGPoolingDataSource(final String ident, final Map<Object, Object> properties, URI fakeURI, Integer maxConnectionPerProject) {

		PGPoolingDataSource source = new PGPoolingDataSource();

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
		source.setApplicationName(PropertiesUtils.getProperty(properties, JDBC_APPNAME_KEY));
		/* Force TCP keepalive on raw SQL JDBC connections */
		source.setTcpKeepAlive(true);
		return source;
	}

	private DataSource buildSimpleDataSource(final String ident, final Map<Object, Object> properties, URI fakeURI) {

		PGSimpleDataSource source = new PGSimpleDataSource();
		Properties props = new Properties();
		Object appName = PropertiesUtils.getProperty(properties, JDBC_APPNAME_KEY);
		if (appName != null) {
			props.put(JDBC_APPNAME_KEY, appName);
		}

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

		source.setApplicationName(PropertiesUtils.getProperty(properties, JDBC_APPNAME_KEY));
		source.setUser(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_USER_KEY));
		source.setPassword(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_PASSWORD_KEY));

		return new DataSourceWrapper(source, this);
	}

	private DataSource buildC3P0DataSource(final String ident, final Map<Object, Object> properties, URI fakeURI, Integer maxConnection) {
		final String datasourceName = ident + '_' + NAME_SEQUENCE.getAndIncrement();
		ComboPooledDataSource source = new ComboPooledDataSource();
		Properties props = new Properties();
		Object appName = PropertiesUtils.getProperty(properties, JDBC_APPNAME_KEY);
		if (appName != null) {
			props.put(JDBC_APPNAME_KEY, appName);
		}
		source.setProperties(props);
		source.setJdbcUrl(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY));
		source.setUser(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_USER_KEY));
		source.setPassword(PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_PASSWORD_KEY));
		source.setMaxPoolSize(maxConnection);
		source.setDataSourceName(datasourceName);
		return source;
	}

	@Override
	protected EntityManagerFactory createEntityManagerFactory(
		final ProlineDatabaseType database,
		final Map<Object, Object> properties,
		final boolean ormOptimizations) {

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		/*
		 * Force TableNameSequencePostgresDialect custom Hibernate dialect and
		 * default ORM optimizations.
		 */
		if (properties.get(HIBERNATE_DIALECT_KEY) == null) {
			properties.put(HIBERNATE_DIALECT_KEY, TableNameSequencePostgresDialect.class.getName());
		}

		/* Configure c3p0 pool for production environnement */
		enableC3P0Pool(properties);

		/*
		 * PostgreSQL JDBC driver version 9.1-901-1.jdbc4 does NOT support
		 * Connection.isValid() : java.sql.SQLFeatureNotSupportedException: La
		 * fonction org.postgresql.jdbc4.Jdbc4Connection.isValid(int) n'est pas
		 * encore implémentée.
		 * 
		 * TODO Remove preferredTestQuery "SELECT 1" trick when PostgreSQL
		 * driver support isValid()
		 */
		//		if (properties.get(HIBERNATE_POOL_PREFERRED_TEST_QUERY_KEY) == null) {
		//			properties.put(HIBERNATE_POOL_PREFERRED_TEST_QUERY_KEY, "SELECT 1");
		//		}

		/* Force TCP keepalive on EntityManager connections */
		if (properties.get(HIBERNATE_CONNECTION_KEEPALIVE_KEY) == null) {
			properties.put(HIBERNATE_CONNECTION_KEEPALIVE_KEY, "true");
		}

		return super.createEntityManagerFactory(database, properties, ormOptimizations);
	}

	@Override
	public int getOpenConnectionCount() {
		if (m_dataSource == null)
			return 0;
		
		if (m_dataSource instanceof ComboPooledDataSource) {
			try {
				ComboPooledDataSource poolDS = ((ComboPooledDataSource) m_dataSource);
				return poolDS.getNumBusyConnections();
			} catch (Exception exClose) {
				LOG.error("Error counting open connection from DataSource", exClose);
				return 0;
			}

		} else {
			return super.getOpenConnectionCount();
		}
		
	}
	
	@Override
	protected void doClose(final String ident, final DataSource source) {

		if (source instanceof ComboPooledDataSource) {
			LOG.debug("Closing DataSource for [{}]", ident);

			try {
				ComboPooledDataSource poolDS = ((ComboPooledDataSource) source);
				LOG.warn("Number of busy connections = "+poolDS.getNumBusyConnections());
				poolDS.close();
			} catch (Exception exClose) {
				LOG.error("Error closing DataSource for [" + ident + ']', exClose);
			}

		}

	}

	/**
	 * Tries to build a <em>fake</em> HTTP URI from the given raw PostgreSQL
	 * database URL to parse host, port, database (path) name...
	 * 
	 * @param rawDatabaseURL
	 *            PostgreSQL database URL as retrieved from
	 *            "javax.persistence.jdbc.url" property. Must be a valid
	 *            database URL.
	 * @return a <em>fake</em> HTTP URI or <code>null</code> if
	 *         <code>rawDatabaseURL</code> cannot be parsed.
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
