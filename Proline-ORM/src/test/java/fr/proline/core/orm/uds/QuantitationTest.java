package fr.proline.core.orm.uds;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class QuantitationTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(QuantitationTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/uds/Quanti_15N_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/uds-db_init_dataset.xml",
		"/dbunit/datasets/uds/Quanti_15N_Dataset.xml" };

	loadCompositeDataSet(datasets);
    }

    @Test
    public void readQuantitation() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Dataset quanti = udsEm.find(Dataset.class, Long.valueOf(1L));
	    assertNotNull(quanti);
	    assertEquals(quanti.getChildrenCount(), 2);
	    assertEquals(quanti.getSampleReplicates().size(), 8);
	    assertEquals(quanti.getBiologicalSamples().size(), 4);
	    assertEquals(quanti.getMethod().getName(), "15N");
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
