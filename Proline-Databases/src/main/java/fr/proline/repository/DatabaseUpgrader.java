package fr.proline.repository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;

import fr.proline.util.SQLUtils;
import fr.proline.util.StringUtils;

/**
 * This class contains utilities to upgrade a Database schema with Flyway (H2, POSTGRESQL) or to initialize a
 * SQLite Db.
 * 
 * @author LMN
 * 
 */
public final class DatabaseUpgrader {

    /* Constants */
    public static final String MIGRATION_SCRIPTS_DIR = "dbscripts/";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseUpgrader.class);

    private static final int BUFFER_SIZE = 256;

    /**
     * Script name for each database type TODO: remove these definitions when Flyway supports SQLite.
     */
    private static final String UDS_DB_SCRIPT_NAME = "V0_1__init_udsdb.sql";
    private static final String PDI_DB_SCRIPT_NAME = "V0_1__init_pdidb.sql";
    private static final String PS_DB_SCRIPT_NAME = "V0_1__init_psdb.sql";
    private static final String MSI_DB_SCRIPT_NAME = "V0_1__init_msidb.sql";
    private static final String LCMS_DB_SCRIPT_NAME = "V0_1__init_lcmsdb.sql";

    /* Private constructor (Utility class) */
    private DatabaseUpgrader() {
    }

    /* Public class methods */
    public static String buildMigrationScriptsLocation(final Database database, final DriverType driverType) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (driverType == null) {
	    throw new IllegalArgumentException("DriverType is null");
	}

	final StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
	buffer.append(MIGRATION_SCRIPTS_DIR);
	buffer.append(database.name().toLowerCase()).append('/');
	buffer.append(driverType.name().toLowerCase());

	return buffer.toString();
    }

    public static void upgradeDatabase(final IDatabaseConnector connector,
	    final String migrationScriptsLocation) {

	if (connector == null) {
	    throw new IllegalArgumentException("Connector is null");
	}

	if (StringUtils.isEmpty(migrationScriptsLocation)) {
	    throw new IllegalArgumentException("Invalid migrationScriptsLocation");
	}

	final Database database = connector.getDatabase();

	LOG.debug("Upgrading {} Db, migrationScriptsLocation [{}]", database, migrationScriptsLocation);

	if (connector.getDriverType() == DriverType.SQLITE) {
	    upgradeSQLiteDb(connector, migrationScriptsLocation);
	} else {
	    final Flyway flyway = new Flyway();

	    flyway.setLocations(migrationScriptsLocation);
	    flyway.setDataSource(connector.getDataSource());

	    final int migrationsCount = flyway.migrate();

	    LOG.info("Flyway applies {} migration(s)", migrationsCount);
	} // End if (driverType is not SQLITE)

    }

    public static void upgradeDatabase(final IDatabaseConnector connector) {

	if (connector == null) {
	    throw new IllegalArgumentException("Connector is null");
	}

	upgradeDatabase(connector,
		buildMigrationScriptsLocation(connector.getDatabase(), connector.getDriverType()));
    }

    public static String[] extractTableNames(final Connection con) throws SQLException {

	if (con == null) {
	    throw new IllegalArgumentException("Con is null");
	}

	String[] result = null;

	final DatabaseMetaData meta = con.getMetaData();

	final ResultSet rs = meta.getTables(null, null, "%", new String[] { "TABLE" });

	try {
	    final List<String> tableNames = new ArrayList<String>();

	    while (rs.next()) {

		final Object obj = rs.getObject("TABLE_NAME");
		if (obj instanceof String) {
		    tableNames.add((String) obj);
		}

	    }

	    result = tableNames.toArray(new String[tableNames.size()]);
	} finally {
	    rs.close();
	}

	return result;
    }

    /* Private methods */
    private static void upgradeSQLiteDb(final IDatabaseConnector connector,
	    final String migrationScriptsLocation) {
	final DataSource ds = connector.getDataSource();

	Connection con = null;

	try {
	    con = ds.getConnection();

	    int tablesCount = 0;

	    final String[] tableNames = extractTableNames(con);

	    if (tableNames != null) {
		tablesCount = tableNames.length;
	    }

	    if (tablesCount == 0) {
		initSQLiteDb(con, migrationScriptsLocation);
	    } else {
		LOG.info("SQLite table count: {}, conserving current schema", tablesCount);
	    }

	} catch (Exception ex) {
	    /* Log and re-throw */
	    final String message = "Error upgrading SQLite Db";
	    LOG.error(message, ex);
	    throw new RuntimeException(message, ex);
	} finally {

	    if (con != null) {
		try {
		    con.close();
		} catch (SQLException exClose) {
		    LOG.error("Error closing SQLite Connection", exClose);
		}
	    }

	} // End of try - catch - finally block for con

    }

    private static void initSQLiteDb(final Connection con, final String migrationScriptsLocation)
	    throws SQLException {
	final String scriptName = getSQLiteScriptName(migrationScriptsLocation);

	if (scriptName == null) {
	    throw new IllegalArgumentException("Script directory doesn't match any supported database");
	}

	final String scriptLocation = migrationScriptsLocation + '/' + scriptName;

	final ClassLoader cl = Thread.currentThread().getContextClassLoader();

	InputStream scriptIS = cl.getResourceAsStream(scriptLocation);

	if (scriptIS == null) {
	    LOG.warn("Cannot found [" + scriptLocation + "] resource");
	} else {
	    LOG.debug("Initializing SQLite Db from [" + scriptLocation + ']');

	    try {
		SQLUtils.executeSQLScript(con, scriptIS);
	    } finally {

		try {
		    scriptIS.close();
		} catch (IOException exClose) {
		    LOG.error("Error closing [" + scriptLocation + "] InputStream", exClose);
		}

	    } // End of try - finally block for scriptIS

	}

    }

    private static String getSQLiteScriptName(final String migrationScriptsLocation) {
	String scriptName = null;

	// TODO: find a safer way to do that
	if (migrationScriptsLocation.contains("/uds/")) {
	    scriptName = UDS_DB_SCRIPT_NAME;
	} else if (migrationScriptsLocation.contains("/pdi/")) {
	    scriptName = PDI_DB_SCRIPT_NAME;
	} else if (migrationScriptsLocation.contains("/ps/")) {
	    scriptName = PS_DB_SCRIPT_NAME;
	} else if (migrationScriptsLocation.contains("/msi/")) {
	    scriptName = MSI_DB_SCRIPT_NAME;
	} else if (migrationScriptsLocation.contains("/lcms/")) {
	    scriptName = LCMS_DB_SCRIPT_NAME;
	}

	return scriptName;
    }

}
