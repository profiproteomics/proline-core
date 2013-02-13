package fr.proline.repository.util;

import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

public final class PostgresUtils {

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

	if (con instanceof PGConnection) {
	    return ((PGConnection) con).getCopyAPI();
	} else {
	    throw new IllegalArgumentException("Invalid PGConnection");
	}

    }

}
