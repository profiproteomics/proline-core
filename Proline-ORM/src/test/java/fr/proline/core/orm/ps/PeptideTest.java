package fr.proline.core.orm.ps;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.ps.repository.PsPeptideRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class PeptideTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(PeptideTest.class);

    private static final String SEQ_TO_FOUND = "LTGMAFR";

    private static final int PEPTIDE_COUNT = 10;

    @Override
    public Database getDatabase() {
	return Database.PS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/ps/Unimod_Dataset.xml");
    }

    @Test
    public void readPeptidesBySeq() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager psEm = emf.createEntityManager();

	try {
	    List<Peptide> peps = PsPeptideRepository.findPeptidesForSequence(psEm, SEQ_TO_FOUND);

	    assertNotNull(peps);
	    assertEquals(2, peps.size());
	    boolean foundPepWOPtm = false;
	    boolean foundPepWithPtm = false;
	    for (Peptide pep : peps) {
		if (pep.getPtms() == null || pep.getPtms().isEmpty())
		    foundPepWOPtm = true;
		else
		    foundPepWithPtm = true;
	    }
	    assertTrue(foundPepWithPtm);
	    assertTrue(foundPepWOPtm);
	} finally {

	    if (psEm != null) {
		try {
		    psEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS EntityManager");
		}
	    }

	}

    }

    @Test
    public void retrievePeptideForIds() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager psEm = emf.createEntityManager();

	try {
	    int retrievedPeptides = 0;

	    /* Build a 0..9 Set */
	    final Set<Integer> ids = new HashSet<Integer>();
	    for (int i = 0; i < PEPTIDE_COUNT; ++i) {
		ids.add(Integer.valueOf(i));
	    }

	    final List<Peptide> peptides = PsPeptideRepository.findPeptidesForIds(psEm, ids);

	    if (peptides != null) {
		for (final Peptide p : peptides) {
		    if (p != null) {
			++retrievedPeptides;
		    }
		}
	    }

	    LOG.info("Retrieved Msi Peptides count: " + retrievedPeptides);

	    Assert.assertTrue("Retrieved Msi Peptides count", retrievedPeptides > 0);
	} finally {

	    if (psEm != null) {
		try {
		    psEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS EntityManager");
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
