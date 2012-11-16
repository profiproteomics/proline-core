package fr.proline.core.orm.msi.repository;

import java.io.File;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import junit.framework.Assert;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
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
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseUtils;

public class MsiPeptideRepositoryTest {

	private static final String DB_DIALECT = "fr.proline.core.orm.utils.SQLiteDialect";
	private static final String DB_DRIVER = "org.sqlite.JDBC";
	private static final String DB_URL = "jdbc:sqlite:msi-db.sqlite";
	private static final String DB_USER = "sa";
	private static final String DB_PASSWORD = "";
	
	private static final Logger LOG = LoggerFactory.getLogger(MsiPeptideRepositoryTest.class);

	private EntityManager msiEm;
	
	@Before
	public void setUp() throws Exception {
		
		final Map<String, String> entityManagerSettings = new HashMap<String, String>();
		entityManagerSettings.put("hibernate.connection.driver_class", DB_DRIVER);
		entityManagerSettings.put("hibernate.connection.url", DB_URL);
		entityManagerSettings.put("hibernate.dialect", DB_DIALECT);
		entityManagerSettings.put("hibernate.hbm2ddl.auto", "create-drop");
		entityManagerSettings.put("hibernate.connection.username", DB_USER);
		entityManagerSettings.put("hibernate.connection.password", DB_PASSWORD);
		LOG.info("START setUp");
		EntityManagerFactory emf = Persistence.createEntityManagerFactory(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName(),entityManagerSettings);
		msiEm = emf.createEntityManager();
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
		final IDatabaseConnection connection = new DatabaseConnection(getDatabaseConnection());
//		connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new org.dbunit.eHsqldbDataTypeFactory());

		try {	LOG.info("START CLEAN_INSERT");
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

	
	protected Connection getDatabaseConnection() throws Exception {
		Class.forName(DB_DRIVER);
		return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
	}

	@After
	public void tearDown() throws Exception {
		msiEm.close();
	}

	@Test
	public void testFindThousandsPeptidesForIds() {
	
		LOG.info("START ");

		ArrayList<Integer> ids= new ArrayList<Integer>();
		for (int i = 0; i <= 2000; i++) 
			ids.add(i);
		LOG.info("CALL ");
		final MsiPeptideRepository msiPeptideRepo = new MsiPeptideRepository(msiEm);
		List<Peptide> peptides = msiPeptideRepo.findPeptidesForIds(ids);
		LOG.info("TEST ");
		Assert.assertNotNull(peptides);
		
	}

	
	
}
