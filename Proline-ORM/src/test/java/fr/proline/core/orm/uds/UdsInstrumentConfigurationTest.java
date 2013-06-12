package fr.proline.core.orm.uds;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.UdsInstrumentConfigurationRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class UdsInstrumentConfigurationTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(UdsInstrumentConfigurationTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/uds/Project_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/uds-db_init_dataset.xml",
		"/dbunit/datasets/uds/Project_Dataset.xml" };

	loadCompositeDataSet(datasets);
    }

    @Test
    public void readInstrumentConfig() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    InstrumentConfiguration insCfg = UdsInstrumentConfigurationRepository
		    .findInstrumConfForNameAndMs1AndMsn(udsEm, "LTQ-ORBITRAP XL (A1=FTMS F=CID A2=TRAP)",
			    "FTMS", "TRAP");
	    assertNotNull(insCfg);
	    assertEquals(insCfg.getActivation().getType(), Activation.ActivationType.CID);
	} finally {

	    if (udsEm != null) {
		try {
		    udsEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing UDS EntityManager", exClose);
		}
	    }
	}
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
