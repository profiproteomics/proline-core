package fr.proline.repository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;

import fr.proline.util.SQLUtils;
import fr.proline.util.StringUtils;

/**
 * This class contains utilities to upgrade a Database schema with Flyway (H2, POSTGRESQL) or SQLite script.
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
    private static final String LCMS_DB_SCRIPT_NAME = "V0_1__init_lcmsdb.sql";
    private static final String PDI_DB_SCRIPT_NAME = "V0_1__init_pdidb.sql";
    private static final String MSI_DB_SCRIPT_NAME = "V0_1__init_msidb.sql";
    private static final String PS_DB_SCRIPT_NAME = "V0_1__init_psdb.sql";

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
	buffer.append(driverType.name().toLowerCase()).append('/');

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
	    final String scriptName = getSQLiteScriptName(migrationScriptsLocation);

	    if (scriptName == null) {
		throw new IllegalArgumentException("Script directory doesn't match to any supported database");
	    }

	    final String scriptPath = migrationScriptsLocation + '/' + scriptName;
	    final ClassLoader cl = Thread.currentThread().getContextClassLoader();

	    InputStream is = cl.getResourceAsStream(scriptPath);

	    try {
		createSQLiteDb(connector, is);
	    } finally {

		if (is != null) {
		    try {
			is.close();
		    } catch (IOException exClose) {
			LOG.error("Error closing script InputStream", exClose);
		    }
		}

	    } // End of try - finally block

	} else {
	    final Flyway flyway = new Flyway();

	    flyway.setLocations(migrationScriptsLocation);
	    flyway.setDataSource(connector.getDataSource());

	    final int migrationsCount = flyway.migrate();

	    LOG.info("Flyway applies {} migrations", migrationsCount);
	} // End if (driverType is not SQLITE)

    }

    public static void upgradeDatabase(final IDatabaseConnector connector) {

	if (connector == null) {
	    throw new IllegalArgumentException("Connector is null");
	}

	upgradeDatabase(connector,
		buildMigrationScriptsLocation(connector.getDatabase(), connector.getDriverType()));
    }

    /* Private methods */
    private static String getSQLiteScriptName(final String migrationScriptsLocation) {
	String scriptName = null;

	// TODO: find a safer way to do that
	if (migrationScriptsLocation.contains("/uds/"))
	    scriptName = UDS_DB_SCRIPT_NAME;
	else if (migrationScriptsLocation.contains("/lcms/"))
	    scriptName = LCMS_DB_SCRIPT_NAME;
	else if (migrationScriptsLocation.contains("/pdi/"))
	    scriptName = PDI_DB_SCRIPT_NAME;
	else if (migrationScriptsLocation.contains("/msi/"))
	    scriptName = MSI_DB_SCRIPT_NAME;
	else if (migrationScriptsLocation.contains("/ps/"))
	    scriptName = PS_DB_SCRIPT_NAME;

	return scriptName;
    }

    private static void createSQLiteDb(final IDatabaseConnector connector, final InputStream scriptIS) {

	// If connection mode is file
	boolean createSchema = true;
	/*
	 * if( dbConfig.connectionConfig.getString("connectionMode") == "FILE" ) {
	 * 
	 * String dbPath = dbConfig.dbDirectory + "/"+ dbConfig.connectionConfig.getString("dbName") if( new
	 * File(dbPath).exists == true ) { this.logger.warn("database file already exists") createSchema =
	 * false } else this.logger.info("create new database file: "+dbPath) }
	 */

	if (createSchema) {
	    final DataSource ds = connector.getDataSource();

	    /* By default SQLite database is in auto-commit mode */
	    Connection con = null;

	    try {
		con = ds.getConnection();

		SQLUtils.executeSQLScript(con, scriptIS);
	    } catch (Exception ex) {
		/* Log and re-throw */
		final String message = "Error executing SQLite script";
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

	    } // End of try - catch - finally block

	} // End if (createSchema)

    }

}
