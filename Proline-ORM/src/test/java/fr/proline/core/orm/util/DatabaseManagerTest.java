package fr.proline.core.orm.util;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;
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
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class DatabaseManagerTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManagerTest.class);

    private int m_projectId;

    @Override
    public Database getDatabase() {
	return Database.UDS;
    }

    @Override
    public String getSQLScriptLocation() {
	return DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	final EntityManager udsEm = getEntityManager();

	EntityTransaction transac = udsEm.getTransaction();
	boolean transacOk = false;

	try {
	    transac.begin();
	    transacOk = false;

	    /* Create a Test PDI Db */
	    final ExternalDb pdiDb = new ExternalDb();
	    pdiDb.setType(Database.PDI);
	    pdiDb.setConnectionMode(ConnectionMode.MEMORY);
	    pdiDb.setDbName("pdi_test");
	    pdiDb.setDbUser("sa");
	    pdiDb.setDbPassword("");
	    pdiDb.setDbVersion("0.1");
	    pdiDb.setIsBusy(Boolean.FALSE);

	    udsEm.persist(pdiDb);

	    /* Create a Test PDI Db */
	    final ExternalDb psDb = new ExternalDb();
	    psDb.setType(Database.PS);
	    psDb.setConnectionMode(ConnectionMode.MEMORY);
	    psDb.setDbName("ps_test");
	    psDb.setDbUser("sa");
	    psDb.setDbPassword("");
	    psDb.setDbVersion("0.1");
	    psDb.setIsBusy(Boolean.FALSE);

	    udsEm.persist(psDb);

	    /* Create a TEST Project */
	    final UserAccount projectOwner = new UserAccount();
	    projectOwner.setCreationMode("manual");
	    projectOwner.setLogin("Bob");

	    udsEm.persist(projectOwner);

	    final Project project = new Project(projectOwner);
	    project.setName("Mon projet");
	    project.setDescription("Un super projet");

	    udsEm.persist(project);

	    /* Create a Project MSI Db */
	    final ExternalDb msiDb = new ExternalDb();
	    msiDb.setType(Database.MSI);
	    msiDb.setConnectionMode(ConnectionMode.MEMORY);
	    msiDb.setDbName("msi_1_test");
	    msiDb.setDbUser("sa");
	    msiDb.setDbPassword("");
	    msiDb.setDbVersion("0.1");
	    msiDb.setIsBusy(Boolean.FALSE);

	    msiDb.addProject(project);
	    project.addExternalDatabase(msiDb); // Reverse association

	    udsEm.persist(msiDb);

	    /* Create a Project LCMS Db */
	    final ExternalDb lcMsDb = new ExternalDb();
	    lcMsDb.setType(Database.LCMS);
	    lcMsDb.setConnectionMode(ConnectionMode.MEMORY);
	    lcMsDb.setDbName("lcms_1_test");
	    lcMsDb.setDbUser("sa");
	    lcMsDb.setDbPassword("");
	    lcMsDb.setDbVersion("0.1");
	    lcMsDb.setIsBusy(Boolean.FALSE);

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

	}

    }

    @Test
    public void testDatabaseManager() {
	final DatabaseManager dbManager = DatabaseManager.getInstance();

	dbManager.initialize(getConnector());

	assertTrue("DatabaseManager is initialized", dbManager.isInitialized());

	assertNotNull("PDI Db Connector", dbManager.getPdiDbConnector());

	assertNotNull("PS Db Connector", dbManager.getPsDbConnector());

	assertNotNull("MSI DB Connector for Project " + m_projectId, dbManager.getMsiDbConnector(m_projectId));

	assertNotNull("LCMS DB Connector for Project " + m_projectId,
		dbManager.getLcMsDbConnector(m_projectId));
    }

    @After
    @Override
    public void tearDown() {
	final DatabaseManager dbManager = DatabaseManager.getInstance();

	if (dbManager.isInitialized()) {
	    /* Special tearDown here for all created in-memory test Databases */
	    dbManager.closeAll();
	} else {
	    super.tearDown();
	}

    }

}
