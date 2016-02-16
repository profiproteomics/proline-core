package fr.proline.repository;

import java.util.Map;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.profi.util.PropertiesUtils;

public class H2DatabaseConnector extends AbstractDatabaseConnector {

	private static final Logger LOG = LoggerFactory.getLogger(H2DatabaseConnector.class);

	private static final String MEMORY_URL_PROTOCOL = ":mem:";

	public H2DatabaseConnector(final ProlineDatabaseType database, final Map<Object, Object> properties) {
		super(database, properties);
	}

	@Override
	public DriverType getDriverType() {
		return DriverType.H2;
	}

	@Override
	protected boolean isMemory(final Map<Object, Object> properties) {

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		boolean result = false;

		final String databaseURL = PropertiesUtils.getProperty(properties, PERSISTENCE_JDBC_URL_KEY);
		if (databaseURL != null) {
			result = databaseURL.toLowerCase().contains(MEMORY_URL_PROTOCOL);
		}

		return result;
	}

	@Override
	protected DataSource createDataSource(final String ident, final Map<Object, Object> properties) {

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
	protected void doClose(final String ident, final DataSource source) {

		if (source instanceof JdbcConnectionPool) {
			LOG.debug("Disposing H2 JdbcConnectionPool for [{}]", ident);

			final JdbcConnectionPool h2Source = (JdbcConnectionPool) source;

			try {
				h2Source.dispose();
			} catch (Exception exClose) {
				LOG.error("Error disposing H2 JdbcConnectionPool for [" + ident + ']', exClose);
			}

			final int remainingH2ConnectionsCount = h2Source.getActiveConnections();

			if (remainingH2ConnectionsCount > 0) {
				LOG.debug("Remaining H2 Connections for [{}]: {}", ident, remainingH2ConnectionsCount);
			}

		}

	}

}
