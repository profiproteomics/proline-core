package fr.proline.core.om.utils;

import java.io.FileOutputStream;

import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.DatabaseSequenceFilter;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.dataset.xml.FlatDtdDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;

import com.googlecode.flyway.core.Flyway;

public class DatabaseUtils {
	
	public static void initDatabase(DatabaseTestConnector connector, String scriptDirectory) throws ClassNotFoundException {
		Flyway flyway = new Flyway();
		flyway.setBaseDir(scriptDirectory);
		flyway.setDataSource(connector.getDataSource());
		flyway.migrate();
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

	/**
	 * Creates a new DTD dataset file that can be referenced by XML test datasets. Generating a new DTD
	 * is necessary when the database model is modified and is useful to edit/validate XML datasets.
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		try {
			DatabaseTestConnector connector = new DatabaseTestConnector("/database.properties");
			connector.getConnection();
			initDatabase(connector, "/dbscripts/msi/h2");
			writeDataSetDTD(connector, "msi-dataset.dtd");
			connector.closeAll();
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
}
