package fr.proline.repository.utils;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DatabaseTestCase {


	public final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	protected EntityManager em;
	protected EntityManagerFactory emf;
	protected DatabaseTestConnector connector;


	public void tearDown() throws Exception {		
		getConnector().getDatabaseTester().onTearDown();
		em.close();
		emf.close();
		getConnector().closeAll();
	}
	
	public void initEntityManager(String persistenceUnitName) {
		final Map<String, Object> entityManagerSettings = getConnector().getEntityManagerSettings();
		emf = Persistence.createEntityManagerFactory(persistenceUnitName, entityManagerSettings);
		em = emf.createEntityManager();
	}

	public void initEntityManager(String persistenceUnitName, Map<String, Object> configuration) {
		final Map<String, Object> entityManagerSettings = getConnector().getEntityManagerSettings();
		for (Map.Entry<String, Object> e : configuration.entrySet()) {
			entityManagerSettings.put(e.getKey(), e.getValue());
		}
	   emf = Persistence.createEntityManagerFactory(persistenceUnitName, entityManagerSettings);
		em = emf.createEntityManager();
	}

	/** 
	 * Get the EntityManager for this Database TestCase. To obtain a valid 
	 * EntityManager, an initEntityManager should have been executed before.
	 * 
	 * @return
	 */
	public EntityManager getEntityManager(){
		return em;
	}
	
	public void initDatabase() throws ClassNotFoundException {
		DatabaseUtils.initDatabase(getConnector(), getSQLScriptLocation());
	}

	public void loadDataSet(String datasetName) throws Exception {
		DatabaseUtils.loadDataSet(getConnector(), datasetName);
		connector.getDatabaseTester().onSetup();
	}

	public void loadCompositeDataSet(String[] datasets) throws Exception {
		DatabaseUtils.loadCompositeDataSet(getConnector(), datasets);
		connector.getDatabaseTester().onSetup();
	}

	public DatabaseTestConnector getConnector() {
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
	
	
	/**
	 * Path to SQL scripts from where DB will be initialized
	 * @return
	 */
	public abstract String getSQLScriptLocation();
	
	/**
	 * 
	 * @return Full Path and Name of db properties file in classpath
	 */
	public String getPropertiesFilename() {
		return DatabaseUtils.DEFAULT_DATABASE_PROPERTIES_FILENAME;
	}

}
