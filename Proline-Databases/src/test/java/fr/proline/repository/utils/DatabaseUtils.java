package fr.proline.repository.utils;

import java.io.FileOutputStream;

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

import fr.proline.repository.Database;
import fr.proline.repository.DatabaseUpgrader;

public final class DatabaseUtils {

    /* Constants */
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseUtils.class);

    /* Don't need to prefix path with absolute '/' to load as resource via a ClassLoader */
    /**
     * Default full Path and Name of db properties file in classpath.
     */
    public static final String DEFAULT_DATABASE_PROPERTIES_FILENAME = "database.properties";

    /* Private constructor (Utility class) */
    private DatabaseUtils() {
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

	    DatabaseUpgrader.upgradeDatabase(connector);

	    writeDataSetDTD(connector, "uds-dataset.dtd");
	    connector.close();
	} catch (Exception ex) {
	    LOG.error("Error testing H2 UDS Db", ex);
	}
    }

}
