package fr.proline.core.orm.msi.repository;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import junit.framework.Assert;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.repository.AbstractDatabaseConnector;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestConnector;

public class MsiPeptideRepositoryTest {

    private static final String DB_DRIVER = "org.sqlite.JDBC";
    private static final String DB_URL = "jdbc:sqlite:msi-db.sqlite";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static final Logger LOG = LoggerFactory.getLogger(MsiPeptideRepositoryTest.class);

    private DatabaseTestConnector m_connector;

    @Before
    public void setUp() throws Exception {

	final Map<Object, Object> props = new HashMap<Object, Object>();
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_DRIVER_KEY, DB_DRIVER);
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY, DB_URL);
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_USER_KEY, DB_USER);
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY, DB_PASSWORD);
	props.put("hibernate.hbm2ddl.auto", "create-drop");

	LOG.info("START setUp");
	m_connector = new DatabaseTestConnector(Database.MSI, props);

	LOG.info("START dataSet");
	// Setup the seed data
	ReplacementDataSet dataSet;
	try {
	    dataSet = new ReplacementDataSet(new FlatXmlDataSet(getTestFileURL(), false, true));
	} catch (Exception e) {
	    LOG.error("Cannot create dataSet from flat file : " + getTestFileURL());
	    LOG.error(e.getLocalizedMessage());
	    return;
	}
	LOG.info("START addReplacementSubstring");
	dataSet.addReplacementSubstring("NaN", "0.0");
	final IDatabaseConnection connection = m_connector.getDatabaseTester().getConnection();
	// connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new
	// org.dbunit.eHsqldbDataTypeFactory());

	try {
	    LOG.info("START CLEAN_INSERT");
	    DatabaseOperation.CLEAN_INSERT.execute(connection, dataSet);
	} catch (Exception e) {
	    LOG.error(e.getLocalizedMessage());
	    throw e;
	} finally {
	    connection.close();
	}
    }

    private File getTestFileURL() {
	try {
	    return new File(getClass().getResource("/fr/proline/core/orm/msi/MsiSearch_Dataset.xml").toURI());
	} catch (URISyntaxException e) {
	    e.printStackTrace();
	    return null;
	}
    }

    @After
    public void tearDown() {

	if (m_connector != null) {
	    m_connector.close();
	}

    }

    @Test
    public void testFindThousandsPeptidesForIds() {
	LOG.info("START ");

	ArrayList<Integer> ids = new ArrayList<Integer>();
	for (int i = 0; i <= 2000; i++)
	    ids.add(i);
	LOG.info("CALL ");
	final EntityManagerFactory emf = m_connector.getEntityManagerFactory();
	final EntityManager msiEm = emf.createEntityManager();

	try {
	    final MsiPeptideRepository msiPeptideRepo = new MsiPeptideRepository(msiEm);
	    List<Peptide> peptides = msiPeptideRepo.findPeptidesForIds(ids);
	    LOG.info("TEST ");
	    Assert.assertNotNull(peptides);
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

}
