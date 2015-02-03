package fr.proline.core.orm.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.UserAccount;
import fr.proline.repository.ConnectionMode;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class DataStoreConnectorFactoryTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DataStoreConnectorFactoryTest.class);

    private long m_projectId;

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	final IDatabaseConnector connector = getConnector();

	final Map<Object, Object> props = new HashMap<Object, Object>();
	props.put("hibernate.show_sql", "true");
	props.put("hibernate.format_sql", "true");

	connector.setAdditionalProperties(props);

	initDatabase();

	final EntityManagerFactory emf = connector.getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	EntityTransaction transac = null;
	boolean transacOk = false;

	try {
	    transac = udsEm.getTransaction();
	    transac.begin();
	    transacOk = false;

	    /* Create a Test PDI Db */
	    final ExternalDb pdiDb = new ExternalDb();
	    pdiDb.setType(ProlineDatabaseType.PDI);
	    pdiDb.setConnectionMode(ConnectionMode.MEMORY);
	    pdiDb.setDbName("pdi_test");
	    pdiDb.setDbUser("sa");
	    pdiDb.setDbPassword("");
	    pdiDb.setDbVersion("0.1");
	    pdiDb.setIsBusy(false);

	    udsEm.persist(pdiDb);

	    /* Create a Test PS Db */
	    final ExternalDb psDb = new ExternalDb();
	    psDb.setType(ProlineDatabaseType.PS);
	    psDb.setConnectionMode(ConnectionMode.MEMORY);
	    psDb.setDbName("ps_test");
	    psDb.setDbUser("sa");
	    psDb.setDbPassword("");
	    psDb.setDbVersion("0.1");
	    psDb.setIsBusy(false);

	    udsEm.persist(psDb);

	    /* Create a TEST Project */
	    final UserAccount projectOwner = new UserAccount();
	    projectOwner.setPasswordHash("notEncryptedPassword");
	    projectOwner.setCreationMode("manual");
	    projectOwner.setLogin("Bob");

	    udsEm.persist(projectOwner);

	    final Project project = new Project(projectOwner);
	    project.setName("Mon projet");
	    project.setDescription("Un super projet");

	    udsEm.persist(project);

	    /* Create a Project MSI Db */
	    final ExternalDb msiDb = new ExternalDb();
	    msiDb.setType(ProlineDatabaseType.MSI);
	    msiDb.setConnectionMode(ConnectionMode.MEMORY);
	    msiDb.setDbName("msi_1_test");
	    msiDb.setDbUser("sa");
	    msiDb.setDbPassword("");
	    msiDb.setDbVersion("0.1");
	    msiDb.setIsBusy(false);

	    msiDb.addProject(project);
	    project.addExternalDatabase(msiDb); // Reverse association

	    udsEm.persist(msiDb);

	    /* Create a Project LCMS Db */
	    final ExternalDb lcMsDb = new ExternalDb();
	    lcMsDb.setType(ProlineDatabaseType.LCMS);
	    lcMsDb.setConnectionMode(ConnectionMode.MEMORY);
	    lcMsDb.setDbName("lcms_1_test");
	    lcMsDb.setDbUser("sa");
	    lcMsDb.setDbPassword("");
	    lcMsDb.setDbVersion("0.1");
	    lcMsDb.setIsBusy(false);

	    lcMsDb.addProject(project);
	    project.addExternalDatabase(lcMsDb); // Reverse association

	    udsEm.persist(lcMsDb);

	    transac.commit();
	    transacOk = true;

	    m_projectId = project.getId();
	} finally {

	    if ((transac != null) & !transacOk) {
		LOG.info("Rollbacking UDS Db transaction");

		try {
		    transac.rollback();
		} catch (Exception ex) {
		    LOG.error("Error rollbacking UDS Db transaction", ex);
		}

	    }

	    try {
		udsEm.close();
	    } catch (Exception exClose) {
		LOG.error("Error closing UDS EntityManager", exClose);
	    }

	}

    }

    @Override 
    public String getPropertiesFileName(){
    	return "database.properties";
    }
    
    @Test
    public void testDatabaseManager() {
	final DataStoreConnectorFactory connectorFactory = DataStoreConnectorFactory.getInstance();

	connectorFactory.initialize(getConnector());

	assertTrue("DatabaseManager is initialized", connectorFactory.isInitialized());

	/* Explicitly upgrade all Databases */
	DataStoreUpgrader.upgradeAllDatabases(connectorFactory);

	assertNotNull("PDI Db Connector", connectorFactory.getPdiDbConnector());

	assertNotNull("PS Db Connector", connectorFactory.getPsDbConnector());

	assertNotNull("MSI DB Connector for Project " + m_projectId,
		connectorFactory.getMsiDbConnector(m_projectId));

	assertNotNull("LCMS DB Connector for Project " + m_projectId,
		connectorFactory.getLcMsDbConnector(m_projectId));
    }

    @After
    @Override
    public void tearDown() {
	/*
	 * Special tearDown here : first close the DatabaseTestConnector (for UDS Db) then close all created
	 * in-memory test Databases (PDI, PS, MSI, LCMS Dbs...) Note : UDS Db is closed twice (by tearDown and
	 * DatabaseManager.closeAll), this cause a harmless IllegalStateException "Connection pool has been
	 * disposed".
	 */
	super.tearDown();

	DataStoreConnectorFactory.getInstance().closeAll();
    }

}
