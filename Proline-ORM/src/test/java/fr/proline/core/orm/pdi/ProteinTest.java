package fr.proline.core.orm.pdi;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class ProteinTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProteinTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.PDI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/pdi/Proteins_Dataset.xml"
	loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml");
    }

    @Test
    public void readProtein() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    ProteinIdentifier protein = pdiEm.find(ProteinIdentifier.class, Long.valueOf(341L));
	    assertNotNull(protein);
	    assertFalse(protein.getIsAcNumber());
	    assertEquals(protein.getTaxon().getScientificName(), "Pseudomonas sp.");
	    assertEquals(protein.getValue(), "1A1D_PSESP");
	} finally {

	    if (pdiEm != null) {
		try {
		    pdiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PDI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void readBioSequence() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    BioSequence bioSeq = pdiEm.find(BioSequence.class, Long.valueOf(171L));
	    assertNotNull(bioSeq);
	    assertEquals(bioSeq.getLength(), Integer.valueOf(338));
	    assertEquals(bioSeq.getProteinIdentifiers().size(), 4);
	} finally {

	    if (pdiEm != null) {
		try {
		    pdiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PDI EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
