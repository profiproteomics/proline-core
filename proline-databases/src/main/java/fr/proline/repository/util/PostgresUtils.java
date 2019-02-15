package fr.proline.repository.util;

import static fr.profi.util.StringUtils.LINE_SEPARATOR;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.engine.jdbc.spi.JdbcWrapper;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PostgresUtils {

	private static final Logger LOG = LoggerFactory.getLogger(PostgresUtils.class);

	private PostgresUtils() {
	}

	/**
	 * Retrieves the PostgreSQL <code>CopyManager</code> of the given <code>Connection</code>.
	 * 
	 * @param connection
	 *            SQL JDBC Connection, must be a <code>PGConnection</code> or a Hibernate wrapped connection.
	 *            All connections returned by PostgreSQL Drivers and DataSources implement
	 *            <code>PGConnection</code>.
	 * @return The <code>CopyManager</code> instance of the given connection.
	 * @throws SQLException
	 *             If an SQL error occured.
	 */
	public static CopyManager getCopyManager(final Object connection) throws SQLException {

		if (connection == null) {
			throw new IllegalArgumentException("Con is null");
		}

		CopyManager result = null;

		/*
		 * WARNING : As connection object can be a Proxy or a Wrapper, type checks must only be done on
		 * interface types (NOT Java class types).
		 */
		if (connection instanceof PGConnection) {
			final PGConnection pgConnection = (PGConnection) connection;

			result = pgConnection.getCopyAPI();
		} else if (connection instanceof JdbcWrapper<?>) {
			/* JdbcWrapper interface is specific to Hibernate ORM */
			final JdbcWrapper<?> jdbcWrapper = (JdbcWrapper<?>) connection;
			final Object wrappedObject = jdbcWrapper.getWrappedObject();

			result = getCopyManager(wrappedObject);
		} else if (connection instanceof Connection) {
			PGConnection pgConnection = ((Connection) connection).unwrap(PGConnection.class);
			result = pgConnection.getCopyAPI();
		} else {
			debugProxy(connection);
			throw new IllegalArgumentException("Invalid PostgreSQL connection");
		}

		return result;
	}

	private static void debugProxy(final Object obj) {
		final Class<?> clazz = obj.getClass();

		final StringBuilder buff = new StringBuilder();
		buff.append("Runtime Class : ").append(clazz.getName());

		if (Proxy.isProxyClass(clazz)) {
			buff.append("  Java Proxy class");
		}

		buff.append(LINE_SEPARATOR);

		final Class<?>[] interfaces = clazz.getInterfaces();

		for (final Class<?> intf : interfaces) {
			buff.append("  implement : ").append(intf.getName());
			buff.append(LINE_SEPARATOR);
		}

		LOG.warn(buff.toString());
	}

}
