package fr.proline.core.om.utils;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DatabaseTestCase {

	private static final String DEFAULT_DATABASE_PROPERTIES_FILENAME = "/database.properties";
	private static final String DEFAULT_DATABASE_SCRIPT_LOCATION = "/dbscripts/uds/h2";
	private static final Logger logger = LoggerFactory.getLogger(DatabaseTestCase.class);
	
	protected EntityManager em;
	protected EntityManagerFactory emf;
	protected DatabaseTestConnector connector;


	public void tearDown() throws Exception {		
		getConnector().getDatabaseTester().onTearDown();
		em.close();
		emf.close();
		getConnector().closeAll();
	}
	
	protected void initEntityManager(String persistenceUnitName) {
		final Map<String, Object> entityManagerSettings = getConnector().getEntityManagerSettings();
		emf = Persistence.createEntityManagerFactory(persistenceUnitName, entityManagerSettings);
		em = emf.createEntityManager();
	}

	protected void initEntityManager(String persistenceUnitName, Map<String, Object> configuration) {
		final Map<String, Object> entityManagerSettings = getConnector().getEntityManagerSettings();
		for (Map.Entry<String, Object> e : configuration.entrySet()) {
			entityManagerSettings.put(e.getKey(), e.getValue());
		}
	   emf = Persistence.createEntityManagerFactory(persistenceUnitName, entityManagerSettings);
		em = emf.createEntityManager();
	}

	protected void initDatabase() throws ClassNotFoundException {
		DatabaseUtils.initDatabase(getConnector(), getSQLScriptLocation());
	}

	protected void loadDataSet(String datasetName) throws Exception {
		DatabaseUtils.loadDataSet(getConnector(), datasetName);
		connector.getDatabaseTester().onSetup();
	}

	protected DatabaseTestConnector getConnector() {
		if (connector == null) {
			connector = new DatabaseTestConnector(getPropertiesFilename());
			try {
				// This is necessary since in-memory databases are closed when the last connection is closed. This
				// method call creates a first connection that will be closed by closeAll() method.
				connector.getConnection();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return connector;
	}
	
	public String getSQLScriptLocation() {
		return DEFAULT_DATABASE_SCRIPT_LOCATION;
	}
	
	public String getPropertiesFilename() {
		return DEFAULT_DATABASE_PROPERTIES_FILENAME;
	}

}
