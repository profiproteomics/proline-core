package fr.proline.repository.util;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.engine.jdbc.spi.JdbcWrapper;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.util.StringUtils;

public final class PostgresUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresUtils.class);

    private PostgresUtils() {
    }

    /**
     * Retrieves the PostgreSQL <code>CopyManager</code> of the given <code>Connection</code>.
     * 
     * @param con
     *            SQL JDBC Connection, must be a <code>PGConnection</code>. All connections returned by
     *            PostgreSQL Drivers and DataSources implement <code>PGConnection</code>.
     * @return The <code>CopyManager</code> instance of the given connection.
     * @throws SQLException
     *             If an SQL error occured.
     */
    public static CopyManager getCopyManager(final Connection con) throws SQLException {

	if (con == null) {
	    throw new IllegalArgumentException("Con is null");
	}

	CopyManager result = null;

	if (con instanceof PGConnection) {
	    final PGConnection pgConnection = (PGConnection) con;

	    result = pgConnection.getCopyAPI();
	} else if (con instanceof JdbcWrapper<?>) {
	    /* This code is specific to Hibernate ORM */
	    final JdbcWrapper<?> jdbcWrapper = (JdbcWrapper<?>) con;
	    final Object wrappedObject = jdbcWrapper.getWrappedObject();

	    if (wrappedObject instanceof Connection) {
		final Connection rawConnection = (Connection) wrappedObject;

		result = getCopyManager(rawConnection);
	    } else {
		debugProxy(wrappedObject);
	    }

	} else {
	    debugProxy(con);

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

	buff.append(StringUtils.LINE_SEPARATOR);

	final Class<?>[] interfaces = clazz.getInterfaces();

	for (final Class<?> intf : interfaces) {
	    buff.append("  implement : ").append(intf);
	    buff.append(StringUtils.LINE_SEPARATOR);
	}

	LOG.warn(buff.toString());
    }

}
