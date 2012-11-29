package fr.proline.repository.utils;

import java.io.FileOutputStream;
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

import com.googlecode.flyway.core.Flyway;

import fr.proline.util.SQLUtils;

public class DatabaseUtils {
	
	/**
	 * Default full Path and Name of db properties file in classpath
	 */
	public static final String DEFAULT_DATABASE_PROPERTIES_FILENAME = "/database.properties";

	
	/**
	 * Location for H2 Databases for each schema
	 */
	public static final String H2_DATABASE_UDS_SCRIPT_LOCATION = "/dbscripts/uds/h2";	
	public static final String H2_DATABASE_LCMS_SCRIPT_LOCATION = "/dbscripts/lcms/h2";
	public static final String H2_DATABASE_PDI_SCRIPT_LOCATION = "/dbscripts/pdi/h2";
	public static final String H2_DATABASE_MSI_SCRIPT_LOCATION = "/dbscripts/msi/h2";
	public static final String H2_DATABASE_PS_SCRIPT_LOCATION = "/dbscripts/ps/h2";
	
	/**
	 * Location for Postgresql Databases for each schema
	 */
	public static final String POSTGRESQL_DATABASE_UDS_SCRIPT_LOCATION = "/dbscripts/uds/postgresql";	
	public static final String POSTGRESQL_DATABASE_LCMS_SCRIPT_LOCATION = "/dbscripts/lcms/postgresql";
	public static final String POSTGRESQL_DATABASE_PDI_SCRIPT_LOCATION = "/dbscripts/pdi/postgresql";
	public static final String POSTGRESQL_DATABASE_MSI_SCRIPT_LOCATION = "/dbscripts/msi/postgresql";
	public static final String POSTGRESQL_DATABASE_PS_SCRIPT_LOCATION = "/dbscripts/ps/postgresql";
	
	/**
	 * Location for SQLite Databases for each schema
	 */
	public static final String SQLITE_DATABASE_UDS_SCRIPT_LOCATION = "/dbscripts/uds/sqlite";	
	public static final String SQLITE_DATABASE_LCMS_SCRIPT_LOCATION = "/dbscripts/lcms/sqlite";
	public static final String SQLITE_DATABASE_PDI_SCRIPT_LOCATION = "/dbscripts/pdi/sqlite";
	public static final String SQLITE_DATABASE_MSI_SCRIPT_LOCATION = "/dbscripts/msi/sqlite";
	public static final String SQLITE_DATABASE_PS_SCRIPT_LOCATION = "/dbscripts/ps/sqlite";
	
	/**
	 * Script name for each database type
	 * TODO: remove these definitions when Flyway supports SQLite
	 */
	protected static final String UDS_DB_SCRIPT_NAME = "V0_1__init_udsdb.sql";
	protected static final String LCMS_DB_SCRIPT_NAME = "V0_1__init_lcmsdb.sql";
	protected static final String PDI_DB_SCRIPT_NAME = "V0_1__init_pdidb.sql";
	protected static final String MSI_DB_SCRIPT_NAME = "V0_1__init_msidb.sql";
	protected static final String PS_DB_SCRIPT_NAME = "V0_1__init_psdb.sql";
	
	private static String _getDatabaseScriptName( String scriptDirectory ) {
		
		String scriptName = null;
		
		// TODO: find a safer way to do that
		if ( scriptDirectory.contains("/uds/") )
			scriptName = UDS_DB_SCRIPT_NAME;
		else if (scriptDirectory.contains("/lcms/") )
			scriptName = LCMS_DB_SCRIPT_NAME;
		else if (scriptDirectory.contains("/pdi/") )
			scriptName = PDI_DB_SCRIPT_NAME;
		else if (scriptDirectory.contains("/msi/") )
			scriptName = MSI_DB_SCRIPT_NAME;
		else if (scriptDirectory.contains("/ps/") )
			scriptName = PS_DB_SCRIPT_NAME;		
		
		return scriptName;
	}
	
	private static void _createSQLiteDB(DatabaseTestConnector connector, InputStream scriptIS ) throws Exception {
	    
		// If connection mode is file
	    boolean createSchema = true;
		/*if( dbConfig.connectionConfig.getString("connectionMode") == "FILE" ) {
	      
			String dbPath = dbConfig.dbDirectory + "/"+ dbConfig.connectionConfig.getString("dbName")
			if( new File(dbPath).exists == true ) {
				this.logger.warn("database file already exists")
				createSchema = false
			}
			else
				this.logger.info("create new database file: "+dbPath)
		}*/
	    
	    if( createSchema ) {
	    	SQLUtils.executeSQLScript(connector.getConnection(), scriptIS );
	    }
	    
	  }
	
	public static void initDatabase(DatabaseTestConnector connector, String scriptDirectory) throws Exception {
		
		if( connector.getDriverType() == fr.proline.repository.ProlineRepository.DriverType.SQLITE ) {
			
			String scriptName =  _getDatabaseScriptName( scriptDirectory );
			if( scriptName == null ) {
				throw new IllegalArgumentException("script directory doesn't match to any supported database");
			}
			
			String scriptPath = scriptDirectory + "/" + scriptName;
			_createSQLiteDB( connector, DatabaseUtils.class.getClass().getResourceAsStream( scriptPath ) );
		}
		else {
			Flyway flyway = new Flyway();
			flyway.setLocations(scriptDirectory); //flyway.setBaseDir(scriptDirectory);		
			flyway.setDataSource(connector.getDataSource());
			flyway.migrate();
		}		
	}
	
	public static void writeDataSetXML(DatabaseTestConnector connector, String outputFilename) throws Exception {
		IDatabaseTester databaseTester = connector.getDatabaseTester();
		ITableFilter filter = new DatabaseSequenceFilter(databaseTester.getConnection());
		IDataSet fullDataSet = new FilteredDataSet(filter, databaseTester.getConnection().createDataSet());
		FlatXmlDataSet.write(fullDataSet, new FileOutputStream(outputFilename));
	}

	public static void writeDataSetDTD(DatabaseTestConnector connector, String dtdFilename) throws Exception {
		IDatabaseTester databaseTester = connector.getDatabaseTester();
		FlatDtdDataSet.write(databaseTester.getConnection().createDataSet(), new FileOutputStream(dtdFilename));
	}	
	
	public static void loadDataSet(DatabaseTestConnector connector, String datasetName) throws Exception {
		IDatabaseTester databaseTester = connector.getDatabaseTester();
		DataFileLoader dataLoader = new FlatXmlDataFileLoader();
		IDataSet dataSet = dataLoader.load(datasetName);
		databaseTester.setDataSet(dataSet);
	}
	
	public static void loadCompositeDataSet(DatabaseTestConnector connector, String[] datasetNames) throws Exception {
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
	 * Creates a new DTD dataset file that can be referenced by XML test datasets. Generating a new DTD
	 * is necessary when the database model is modified and is useful to edit/validate XML datasets.
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		try {
			DatabaseTestConnector connector = new DatabaseTestConnector(DEFAULT_DATABASE_PROPERTIES_FILENAME);
			connector.getConnection();
			initDatabase(connector, H2_DATABASE_UDS_SCRIPT_LOCATION);
			writeDataSetDTD(connector, "uds-dataset.dtd");
			connector.closeAll();
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
}
