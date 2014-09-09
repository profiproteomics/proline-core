package fr.proline.repository;

import static fr.profi.util.StringUtils.LINE_SEPARATOR;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;
import com.googlecode.flyway.core.api.MigrationInfo;
import com.googlecode.flyway.core.api.MigrationState;
import com.googlecode.flyway.core.api.MigrationType;
import com.googlecode.flyway.core.api.MigrationVersion;

import fr.profi.util.SQLUtils;
import fr.profi.util.StringUtils;

/**
 * This class contains utilities to upgrade a Database schema with Flyway (H2,
 * POSTGRESQL) or to initialize a SQLite Db.
 * 
 * @author LMN
 * 
 */
public final class DatabaseUpgrader {

	/* Constants */
	public static final String MIGRATION_SCRIPTS_DIR = "dbscripts/";
	public static final String MIGRATION_CLASSPATH = "fr/proline/repository/migration/";

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseUpgrader.class);

	private static final int BUFFER_SIZE = 256;

	/**
	 * Script name for each database type TODO: remove these definitions when
	 * Flyway supports SQLite.
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
	public static String buildMigrationScriptsLocation(final ProlineDatabaseType database,
			final DriverType driverType) {

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

	/* Public class methods */
	public static String buildMigrationClassLocation(final ProlineDatabaseType database,
			final DriverType driverType) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (driverType == null) {
			throw new IllegalArgumentException("DriverType is null");
		}

		final StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
		buffer.append(MIGRATION_CLASSPATH);
		buffer.append(database.name().toLowerCase());
		String classPath = buffer.toString();
		
		//verify if package exisy
		URL rsc = ClassLoader.getSystemClassLoader().getResource(classPath);
		if(rsc == null)
			return null;
				
		return classPath;
	}

	/**
	 * 
	 * @param connector
	 *            DatabaseConnector of Database to upgrade (must not be
	 *            <code>null</code>).
	 * @param migrationScriptsLocation
	 * @return Applied migrations count : 0 if no migration applied, -1 if a
	 *         migration Error occured (see Log).
	 */
	public static int upgradeDatabase(final IDatabaseConnector connector,
			final String migrationScriptsLocation, final String migrationClassLocation) {

		if (connector == null) {
			throw new IllegalArgumentException("Connector is null");
		}

		if (StringUtils.isEmpty(migrationScriptsLocation)) {
			throw new IllegalArgumentException("Invalid migrationLocation");
		}

		final ProlineDatabaseType prolineDbType = connector.getProlineDatabaseType();

		LOG.debug("Upgrading {} Db, migrationScriptsLocation [{}], migrationClassLocation [{}]",
				prolineDbType, migrationScriptsLocation, migrationClassLocation);

		int migrationsCount = -1;

		try {

			if (connector.getDriverType() == DriverType.SQLITE) {
				migrationsCount = upgradeSQLiteDb(connector, migrationScriptsLocation);
			} else {
				final Flyway flyway = new Flyway();

				/*
				 * // Set SCHEMA_VERSION table name to upper case for H2 driver
				 * if (connector.getDriverType() == DriverType.H2) { Properties
				 * flywayProps = new Properties();
				 * flywayProps.setProperty("flyway.table", "SCHEMA_VERSION");
				 * flyway.configure(flywayProps); }
				 */

				if(migrationClassLocation !=null)
					flyway.setLocations(migrationScriptsLocation, migrationClassLocation);
				else
					flyway.setLocations(migrationScriptsLocation);
				
				flyway.setDataSource(connector.getDataSource());

				/* Trace applied Flyway migrations before migrate() call */
				final MigrationInfo[] appliedMigrations = flyway.info().applied();
				if (appliedMigrations != null) {
					final int nAppliedMigrations = appliedMigrations.length;

					if (nAppliedMigrations > 0) {
						final StringBuilder allMigrationsBuilder = new StringBuilder(BUFFER_SIZE);
						allMigrationsBuilder.append("Applied ").append(prolineDbType)
								.append(" Db Flyway migrations (").append(nAppliedMigrations)
								.append("):");

						for (int i = 0; i < nAppliedMigrations; ++i) {
							allMigrationsBuilder.append(LINE_SEPARATOR);
							allMigrationsBuilder.append("Migration ").append(i).append(' ')
									.append(formatMigrationInfo(appliedMigrations[i]));
						}

						LOG.info(allMigrationsBuilder.toString());
					}

				}

				migrationsCount = flyway.migrate();
				LOG.info("Flyway applies {} migration(s) to {}", migrationsCount, prolineDbType);

				/* Trace current Flyway migration after migrate() call */
				final MigrationInfo currentMigrationInfo = flyway.info().current();
				if (currentMigrationInfo != null) {
					LOG.info("Current {} Db Flyway Migration {}", prolineDbType,
							formatMigrationInfo(currentMigrationInfo));
				}

			} // End if (driverType is not SQLITE)

		} catch (Exception ex) {
			LOG.error("Error upgrading " + prolineDbType + " Db", ex);
		}

		return migrationsCount;
	}

	/**
	 * 
	 * @param connector
	 *            DatabaseConnector of Database to upgrade (must not be
	 *            <code>null</code>).
	 * @return Applied migrations count : 0 if no migration applied, -1 if a
	 *         migration Error occured (see Log).
	 */
	public static int upgradeDatabase(final IDatabaseConnector connector) {

		if (connector == null) {
			throw new IllegalArgumentException("Connector is null");
		}

		int result = -1;

		try {

			result = upgradeDatabase(
					connector,
					buildMigrationScriptsLocation(connector.getProlineDatabaseType(),
							connector.getDriverType()),
					buildMigrationClassLocation(connector.getProlineDatabaseType(),
							connector.getDriverType()));

		} catch (Exception ex) {
			LOG.error("Error upgrading " + connector.getProlineDatabaseType() + " Db", ex);
		}

		return result;
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

			try {
				rs.close();
			} catch (SQLException exClose) {
				LOG.error("Error closing tables ResultSet", exClose);
			}

		}

		return result;
	}

	private static String formatMigrationInfo(final MigrationInfo info) {
		assert (info != null) : "formatMigrationInfo() info is null";

		final StringBuilder buff = new StringBuilder(BUFFER_SIZE);

		final MigrationType type = info.getType();
		if (type != null) {
			buff.append("type: ").append(type);
		}

		final Integer checksum = info.getChecksum();
		if (checksum != null) {

			if (buff.length() > 0) {
				buff.append(' ');
			}

			buff.append("checksum: ").append(String.format("0x%08X", checksum));
		}

		final MigrationVersion version = info.getVersion();
		if (version != null) {

			if (buff.length() > 0) {
				buff.append(' ');
			}

			buff.append(" version: ").append(version);
		}

		final String description = info.getDescription();
		if (description != null) {

			if (buff.length() > 0) {
				buff.append(' ');
			}

			buff.append(" description [").append(description).append(']');
		}

		final String script = info.getScript();
		if (script != null) {

			if (buff.length() > 0) {
				buff.append(' ');
			}

			buff.append(" script [").append(script).append(']');
		}

		final MigrationState state = info.getState();
		if (state != null) {

			if (buff.length() > 0) {
				buff.append(' ');
			}

			buff.append(" state: ").append(state);
		}

		final Date installedOn = info.getInstalledOn();
		if (installedOn != null) {
			buff.append(" installed on ").append(installedOn);
		}

		return buff.toString();
	}

	/* Private methods */
	private static int upgradeSQLiteDb(final IDatabaseConnector connector,
			final String migrationScriptsLocation) throws SQLException {
		final ProlineDatabaseType prolineDbType = connector.getProlineDatabaseType();

		int result = -1;

		Connection con = null;

		try {
			final DataSource ds = connector.getDataSource();

			con = ds.getConnection();

			int tablesCount = 0;

			final String[] tableNames = extractTableNames(con);

			if (tableNames != null) {
				tablesCount = tableNames.length;
			}

			if (tablesCount == 0) {
				initSQLiteDb(con, migrationScriptsLocation);

				LOG.info("{} SQLite Db initialized", prolineDbType);

				result = 1;
			} else {
				LOG.info("{} SQLite Db table count: {}, conserving current schema", prolineDbType,
						tablesCount);

				result = 0;
			}

		} finally {

			if (con != null) {
				try {
					con.close();
				} catch (SQLException exClose) {
					LOG.error("Error closing  " + prolineDbType + " SQLite Db Connection", exClose);
				}
			}

		} // End of try - catch - finally block for con

		return result;
	}

	private static void initSQLiteDb(final Connection con, final String migrationScriptsLocation)
			throws SQLException {
		final String scriptName = getSQLiteScriptName(migrationScriptsLocation);

		if (scriptName == null) {
			throw new IllegalArgumentException(
					"Script directory doesn't match any supported database");
		}

		final String scriptLocation = migrationScriptsLocation + '/' + scriptName;

		final ClassLoader cl = Thread.currentThread().getContextClassLoader();

		InputStream scriptIS = cl.getResourceAsStream(scriptLocation);

		if (scriptIS == null) {
			LOG.warn("Cannot found [{}]", scriptLocation);
		} else {
			LOG.debug("Initializing SQLite Db from [{}]", scriptLocation);

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
