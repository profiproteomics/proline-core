package fr.proline.core.orm.msi.repository;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

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
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.JDBCWork;
import fr.proline.repository.util.JPAUtils;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseTestConnector;

public class MsiPeptideRepositoryTest {

    private static final String DB_DRIVER = "org.sqlite.JDBC";
    private static final String DB_URL = "jdbc:sqlite:./target/msi-db_pep_repo.sqlite";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static final String MSI_SEARCH_DATASET_LOCATION = "dbunit/datasets/msi/MsiSearch_Dataset.xml";

    private static final Logger LOG = LoggerFactory.getLogger(MsiPeptideRepositoryTest.class);

    private DatabaseTestConnector m_connector;

    private EntityManager m_keepAliveEntityManager;

    @Before
    public void setUp() throws Exception {
	final Map<Object, Object> props = new HashMap<Object, Object>();
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_DRIVER_KEY, DB_DRIVER);
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY, DB_URL);
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_USER_KEY, DB_USER);
	props.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY, DB_PASSWORD);
	props.put("hibernate.hbm2ddl.auto", "create-drop");

	LOG.debug("START setUp");
	m_connector = new DatabaseTestConnector(ProlineDatabaseType.MSI, props);

	/* Force creation of Database schema by ORM by retrieving an EntityManager */
	final EntityManagerFactory emf = m_connector.getEntityManagerFactory();

	m_keepAliveEntityManager = emf.createEntityManager();

	/* Print Database Tables */
	EntityTransaction transac = null;
	boolean transacOk = false;

	try {
	    transac = m_keepAliveEntityManager.getTransaction();
	    transac.begin();
	    transacOk = false;

	    final JDBCWork jdbcWork = new JDBCWork() {

		public void execute(final Connection connection) throws SQLException {
		    LOG.debug("Post-init EntityManager Connection : {}  {}", connection,
			    DatabaseTestCase.formatTableNames(connection));
		}

	    };

	    JPAUtils.doWork(m_keepAliveEntityManager, jdbcWork);

	    transac.commit();
	    transacOk = true;
	} finally {

	    if ((transac != null) && !transacOk) {
		LOG.info("Rollbacking EntityManager transaction");

		try {
		    transac.rollback();
		} catch (Exception ex) {
		    LOG.error("Error rollbacking EntityManager transaction", ex);
		}

	    }

	}

	LOG.debug("START dataSet");
	// Setup the seed data
	ReplacementDataSet dataSet = null;

	try {
	    // dataSet = new ReplacementDataSet(new FlatXmlDataSet(getTestFileURL(), false, true));
	    dataSet = new ReplacementDataSet(new FlatXmlDataSet(getTestFileIS(), false));

	    LOG.debug("START addReplacementSubstring");
	    dataSet.addReplacementSubstring("NaN", "0.0");
	} catch (Exception ex) {
	    LOG.error("Cannot create dataSet from flat file: " + MSI_SEARCH_DATASET_LOCATION, ex);

	}

	if (dataSet != null) {
	    final IDatabaseConnection connection = m_connector.getDatabaseTester().getConnection();
	    // connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new
	    // org.dbunit.eHsqldbDataTypeFactory());

	    LOG.debug("START CLEAN_INSERT");

	    try {
		DatabaseOperation.CLEAN_INSERT.execute(connection, dataSet);
	    } catch (Exception ex) {
		LOG.error("Error executing ReplacementDataSet", ex);
		throw ex;
	    } finally {

		try {
		    connection.close();
		} catch (SQLException exClose) {
		    LOG.error("Error closing SQL Connection", exClose);
		}

	    }

	} // End if (dataSet is not null)

    }

    private static InputStream getTestFileIS() {

	InputStream result = null;

	try {
	    final ClassLoader cl = Thread.currentThread().getContextClassLoader();

	    result = cl.getResourceAsStream(MSI_SEARCH_DATASET_LOCATION);

	    LOG.debug("MsiSearch Dataset location [{}]", MSI_SEARCH_DATASET_LOCATION);
	} catch (Exception ex) {
	    LOG.error("Error retieving MsiSearch Dataset location", ex);
	}

	return result;
    }

    @Test
    public void testFindThousandsPeptidesForIds() {
	LOG.debug("START testFindThousandsPeptidesForIds");

	final List<Long> ids = new ArrayList<Long>();

	for (long i = 0; i <= 2000L; ++i) {
	    ids.add(Long.valueOf(i));
	}

	LOG.debug("CALL ");
	final EntityManagerFactory emf = m_connector.getEntityManagerFactory();
	final EntityManager msiEm = emf.createEntityManager();

	try {
	    List<Peptide> peptides = MsiPeptideRepository.findPeptidesForIds(msiEm, ids);
	    LOG.debug("TEST JUnit Assertion");
	    assertNotNull("Retrieved MSI Peptides", peptides);
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

    @After
    public void tearDown() {

	if (m_keepAliveEntityManager != null) {
	    LOG.debug("Closing keep-alive EntityManager");

	    try {
		m_keepAliveEntityManager.close();
	    } catch (Exception exClose) {
		LOG.error("Error closing keep-alive EntityManager", exClose);
	    }

	}

	if (m_connector != null) {
	    m_connector.close();
	}

    }

}
