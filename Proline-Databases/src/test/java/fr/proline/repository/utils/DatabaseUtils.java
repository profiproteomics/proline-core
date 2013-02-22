package fr.proline.repository.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseSequenceFilter;
import org.dbunit.database.IDatabaseConnection;
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

import fr.proline.repository.ProlineDatabaseType;
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

    public static void writeDataSetXML(final DatabaseTestConnector connector, final String outputFileName)
	    throws Exception {
	final IDatabaseTester databaseTester = connector.getDatabaseTester();

	final IDatabaseConnection con = databaseTester.getConnection();

	OutputStream out = null;

	try {
	    final ITableFilter filter = new DatabaseSequenceFilter(con);
	    final IDataSet fullDataSet = new FilteredDataSet(filter, con.createDataSet());

	    out = new FileOutputStream(outputFileName);

	    FlatXmlDataSet.write(fullDataSet, out);
	} finally {

	    if (out != null) {
		try {
		    out.close();
		} catch (IOException exClose) {
		    LOG.error("Error closing [" + outputFileName + "] OutputStream", exClose);
		}
	    }

	    if (con != null) {
		try {
		    con.close();
		} catch (SQLException exClose) {
		    LOG.error("Error closing IDatabaseConnection", exClose);
		}
	    }

	}

    }

    public static void writeDataSetDTD(final DatabaseTestConnector connector, final String dtdFileName)
	    throws Exception {
	final IDatabaseTester databaseTester = connector.getDatabaseTester();

	final IDatabaseConnection con = databaseTester.getConnection();

	OutputStream out = null;

	try {
	    out = new FileOutputStream(dtdFileName);

	    FlatDtdDataSet.write(con.createDataSet(), new FileOutputStream(dtdFileName));
	} finally {

	    if (out != null) {
		try {
		    out.close();
		} catch (IOException exClose) {
		    LOG.error("Error closing [" + dtdFileName + "] OutputStream", exClose);
		}
	    }

	    if (con != null) {
		try {
		    con.close();
		} catch (SQLException exClose) {
		    LOG.error("Error closing IDatabaseConnection", exClose);
		}
	    }

	}

    }

    public static void loadDataSet(final DatabaseTestConnector connector, final String datasetName)
	    throws Exception {
	final IDatabaseTester databaseTester = connector.getDatabaseTester();

	final DataFileLoader dataLoader = new FlatXmlDataFileLoader();
	final IDataSet dataSet = dataLoader.load(datasetName);

	databaseTester.setDataSet(dataSet);
    }

    public static void loadCompositeDataSet(final DatabaseTestConnector connector, final String[] datasetNames)
	    throws Exception {
	final IDatabaseTester databaseTester = connector.getDatabaseTester();

	final DataFileLoader dataLoader = new FlatXmlDataFileLoader();

	final IDataSet[] datasets = new IDataSet[datasetNames.length];

	int count = 0;
	for (final String datasetName : datasetNames) {
	    datasets[count++] = dataLoader.load(datasetName);
	}

	final IDataSet compositeDataSet = new CompositeDataSet(datasets);

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
	    final DatabaseTestConnector connector = new DatabaseTestConnector(ProlineDatabaseType.UDS,
		    DEFAULT_DATABASE_PROPERTIES_FILENAME);

	    DatabaseUpgrader.upgradeDatabase(connector);

	    writeDataSetDTD(connector, "uds-dataset.dtd");
	    connector.close();
	} catch (Exception ex) {
	    LOG.error("Error testing H2 UDS Db", ex);
	}

    }

}
