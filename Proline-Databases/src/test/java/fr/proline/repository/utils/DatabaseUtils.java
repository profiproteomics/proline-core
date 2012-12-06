package fr.proline.repository.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseSequenceFilter;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.dataset.xml.FlatDtdDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.Flyway;

import fr.proline.repository.Database;
import fr.proline.repository.DriverType;
import fr.proline.util.SQLUtils;
import fr.proline.util.StringUtils;

public final class DatabaseUtils {

    /* Constants */
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseUtils.class);

    /* Don't need to prefix path with absolute '/' to load as resource via a ClassLoader */
    /**
     * Default full Path and Name of db properties file in classpath.
     */
    public static final String DEFAULT_DATABASE_PROPERTIES_FILENAME = "database.properties";

    public static final String MIGRATION_SCRIPTS_DIR = "dbscripts/";

    /**
     * Script name for each database type TODO: remove these definitions when Flyway supports SQLite.
     */
    private static final String UDS_DB_SCRIPT_NAME = "V0_1__init_udsdb.sql";
    private static final String LCMS_DB_SCRIPT_NAME = "V0_1__init_lcmsdb.sql";
    private static final String PDI_DB_SCRIPT_NAME = "V0_1__init_pdidb.sql";
    private static final String MSI_DB_SCRIPT_NAME = "V0_1__init_msidb.sql";
    private static final String PS_DB_SCRIPT_NAME = "V0_1__init_psdb.sql";

    /* Private constructor (Utility class) */
    private DatabaseUtils() {
    }

    public static void initDatabase(final DatabaseTestConnector connector,
	    final String migrationScriptsLocation) throws Exception {

	if (connector == null) {
	    throw new IllegalArgumentException("Connector is null");
	}

	if (StringUtils.isEmpty(migrationScriptsLocation)) {
	    throw new IllegalArgumentException("Invalid migrationScriptsLocation");
	}

	LOG.debug("MigrationScriptsLocation [{}]", migrationScriptsLocation);

	if (connector.getDriverType() == DriverType.SQLITE) {
	    final String scriptName = _getDatabaseScriptName(migrationScriptsLocation);

	    if (scriptName == null) {
		throw new IllegalArgumentException("Script directory doesn't match to any supported database");
	    }

	    final String scriptPath = migrationScriptsLocation + '/' + scriptName;
	    final ClassLoader cl = Thread.currentThread().getContextClassLoader();

	    InputStream is = cl.getResourceAsStream(scriptPath);

	    try {
		_createSQLiteDB(connector, is);
	    } finally {

		if (is != null) {
		    try {
			is.close();
		    } catch (IOException exClose) {
			LOG.error("Error closing script InputStream", exClose);
		    }
		}

	    }

	} else {
	    final Flyway flyway = new Flyway();
	    flyway.setLocations(migrationScriptsLocation);
	    flyway.setDataSource(connector.getDataSource());

	    final int migrationsCount = flyway.migrate();

	    LOG.info("Flyway applies {} migrations", migrationsCount);
	}

    }

    public static void writeDataSetXML(final DatabaseTestConnector connector, final String outputFilename)
	    throws Exception {
	IDatabaseTester databaseTester = connector.getDatabaseTester();
	ITableFilter filter = new DatabaseSequenceFilter(databaseTester.getConnection());
	IDataSet fullDataSet = new FilteredDataSet(filter, databaseTester.getConnection().createDataSet());
	FlatXmlDataSet.write(fullDataSet, new FileOutputStream(outputFilename));
    }

    public static void writeDataSetDTD(final DatabaseTestConnector connector, final String dtdFilename)
	    throws Exception {
	IDatabaseTester databaseTester = connector.getDatabaseTester();
	FlatDtdDataSet.write(databaseTester.getConnection().createDataSet(),
		new FileOutputStream(dtdFilename));
    }

    public static void loadDataSet(final DatabaseTestConnector connector, final String datasetName)
	    throws Exception {
	IDatabaseTester databaseTester = connector.getDatabaseTester();
	DataFileLoader dataLoader = new FlatXmlDataFileLoader();
	IDataSet dataSet = dataLoader.load(datasetName);
	databaseTester.setDataSet(dataSet);
    }

    public static void loadCompositeDataSet(final DatabaseTestConnector connector, final String[] datasetNames)
	    throws Exception {
	IDatabaseTester databaseTester = connector.getDatabaseTester();
	DataFileLoader dataLoader = new FlatXmlDataFileLoader();
	IDataSet[] datasets = new IDataSet[datasetNames.length];
	int count = 0;
	for (String datasetName : datasetNames) {
	    datasets[count++] = dataLoader.load(datasetName);
	}
	IDataSet compositeDataSet = new CompositeDataSet(datasets);
	databaseTester.setDataSet(compositeDataSet);
    }

    /**
     * Creates a new DTD dataset file that can be referenced by XML test datasets. Generating a new DTD is
     * necessary when the database model is modified and is useful to edit/validate XML datasets.
     * 
     * @param args
     */
    public static void main(final String[] args) {
	try {
	    final DatabaseTestConnector connector = new DatabaseTestConnector(Database.UDS,
		    DEFAULT_DATABASE_PROPERTIES_FILENAME);

	    initDatabase(connector, MIGRATION_SCRIPTS_DIR + "/uds/h2/");
	    writeDataSetDTD(connector, "uds-dataset.dtd");
	    connector.close();
	} catch (Exception ex) {
	    LOG.error("Error testing H2 UDS Db", ex);
	}
    }

    /* Private methods */
    private static String _getDatabaseScriptName(final String scriptDirectory) {
	String scriptName = null;

	// TODO: find a safer way to do that
	if (scriptDirectory.contains("/uds/"))
	    scriptName = UDS_DB_SCRIPT_NAME;
	else if (scriptDirectory.contains("/lcms/"))
	    scriptName = LCMS_DB_SCRIPT_NAME;
	else if (scriptDirectory.contains("/pdi/"))
	    scriptName = PDI_DB_SCRIPT_NAME;
	else if (scriptDirectory.contains("/msi/"))
	    scriptName = MSI_DB_SCRIPT_NAME;
	else if (scriptDirectory.contains("/ps/"))
	    scriptName = PS_DB_SCRIPT_NAME;

	return scriptName;
    }

    private static void _createSQLiteDB(final DatabaseTestConnector connector, final InputStream scriptIS)
	    throws Exception {

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
	    SQLUtils.executeSQLScript(connector.getDataSource().getConnection(), scriptIS);
	}

    }

}
