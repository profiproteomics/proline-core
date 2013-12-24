package fr.proline.core.orm.msi;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.MsiPeptideRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class MsiPeptideTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(MsiPeptideTest.class);

    private static final long PEPTIDE_COUNT = 10L;

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();
    }

    @Test
    public void testMsiPeptideRepository() {
	int retrievedPeptides = 0;

	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {

	    EntityTransaction msiTransaction1 = null;
	    boolean msiTransacOk = false;

	    try {
		/* First transaction to persist some peptide */
		msiTransaction1 = msiEm.getTransaction();
		msiTransaction1.begin();
		msiTransacOk = false;

		for (long i = 0; i < PEPTIDE_COUNT; ++i) {
		    final Peptide msiPeptide = new Peptide();
		    msiPeptide.setId(i);
		    msiPeptide.setSequence("Pept #" + i);
		    msiPeptide.setCalculatedMass(i);

		    msiEm.persist(msiPeptide);
		}

		msiTransaction1.commit();
		msiTransacOk = true;
	    } finally {

		if ((msiTransaction1 != null) && !msiTransacOk) {
		    LOG.info("Rollbacking first Msi Transaction");

		    try {
			msiTransaction1.rollback();
		    } catch (Exception ex) {
			LOG.error("Error rollbacking first Msi Transaction", ex);
		    }

		}

	    }

	    EntityTransaction msiTransaction2 = null;

	    try {
		/* Second transaction to test peptide repository */
		msiTransaction2 = msiEm.getTransaction();
		msiTransaction2.begin();
		msiTransacOk = false;

		final List<Long> ids = new ArrayList<Long>();

		for (long i = 0; i < PEPTIDE_COUNT; ++i) {
		    ids.add(Long.valueOf(i));
		}

		final List<Peptide> peptides = MsiPeptideRepository.findPeptidesForIds(msiEm, ids);

		if (peptides != null) {
		    for (final Peptide p : peptides) {
			if (p != null) {
			    ++retrievedPeptides;
			}
		    }
		}

		msiTransaction2.commit();
		msiTransacOk = true;
	    } finally {

		if ((msiTransaction2 != null) && !msiTransacOk) {
		    LOG.info("Rollbacking second Msi Transaction");

		    try {
			msiTransaction2.rollback();
		    } catch (Exception ex) {
			LOG.error("Error rollbacking second Msi Transaction", ex);
		    }

		}

	    }

	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

	LOG.info("Retrieved Msi Peptides count: " + retrievedPeptides);

	assertTrue("Retrieved Msi Peptides count", retrievedPeptides > 0);
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
