package fr.proline.core.orm.ps;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.ps.repository.PsPeptideRepository;
import fr.proline.core.orm.util.JPARepositoryUtils;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class PeptideTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(PeptideTest.class);

    private static final String SEQ_TO_FOUND = "LTGMAFR";

    private static final int PEPTIDE_COUNT = 10;

    private static final int BIG_PEPTIDE_COUNT = 2534;

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.PS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	//"/fr/proline/core/orm/ps/Unimod_Dataset.xml"
	String[] datasets = new String[]{
		"/dbunit/datasets/ps-db_init_dataset.xml",
		"/dbunit/datasets/ps/Peptides_Dataset.xml"
	};
	
	loadCompositeDataSet(datasets);
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
		    LOG.error("Error closing PS EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void retrievePeptideForIds() {
	retrievePeptideForIds(PEPTIDE_COUNT);

	retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE - 1);

	retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE);

	retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE + 1);

	retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE + 2);

	retrievePeptideForIds(BIG_PEPTIDE_COUNT);
    }

    private void retrievePeptideForIds(final int nIds) {
	assert (nIds > 0) : "retrievePeptideForIds() invalid nIds";

	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager psEm = emf.createEntityManager();

	try {
	    int retrievedPeptides = 0;

	    /* Build a 0..nIds Set */
	    final List<Integer> ids = new ArrayList<Integer>(nIds);
	    for (int i = 0; i < nIds; ++i) {
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

	    assertTrue("Retrieved Msi Peptides count", retrievedPeptides > 0);
	} finally {

	    if (psEm != null) {
		try {
		    psEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
