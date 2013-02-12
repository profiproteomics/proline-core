package fr.proline.repository.util;

import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

public final class PostgresUtils {

    private PostgresUtils() {
    }

    public static CopyManager getCopyManager(final Connection con) throws SQLException {

	if (con instanceof PGConnection) {
	    return ((PGConnection) con).getCopyAPI();
	} else {
	    throw new IllegalArgumentException("Invalid PGConnection");
	}

    }

}
