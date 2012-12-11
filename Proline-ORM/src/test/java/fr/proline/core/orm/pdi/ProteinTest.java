package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class ProteinTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProteinTest.class);

    @Override
    public Database getDatabase() {
	return Database.PDI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");
    }

    @Test
    public void readProtein() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    ProteinIdentifier protein = pdiEm.find(ProteinIdentifier.class, 341);
	    assertThat(protein, notNullValue());
	    assertThat(protein.getIsAcNumber(), is(false));
	    assertThat(protein.getTaxon().getScientificName(), is("Pseudomonas sp."));
	    assertThat(protein.getValue(), is("1A1D_PSESP"));
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
	    BioSequence bioSeq = pdiEm.find(BioSequence.class, 171);
	    assertThat(bioSeq, notNullValue());
	    assertThat(bioSeq.getLength(), is(338));
	    assertThat(bioSeq.getProteinIdentifiers().size(), is(4));
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
