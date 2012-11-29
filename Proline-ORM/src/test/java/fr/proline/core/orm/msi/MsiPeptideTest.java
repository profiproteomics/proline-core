package fr.proline.core.orm.msi;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.MsiPeptideRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class MsiPeptideTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(MsiPeptideTest.class);

    private static final int PEPTIDE_COUNT = 10;

    @Before
    public void setUp() throws Exception {
	initDatabase();
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

    @Test
    public void testMsiPeptideRepository() {
	int retrievedPeptides = 0;

	final EntityManager msiEm = getEntityManager();

	EntityTransaction msiTransaction1 = null;
	boolean msiTransacOk = false;

	try {
	    /* First transaction to persist some peptide */
	    msiTransaction1 = msiEm.getTransaction();
	    msiTransaction1.begin();
	    msiTransacOk = false;

	    for (int i = 0; i < PEPTIDE_COUNT; ++i) {
		final Peptide msiPeptide = new Peptide();
		msiPeptide.setId(Integer.valueOf(i));
		msiPeptide.setSequence("Pept #" + i);
		msiPeptide.setCalculatedMass(i);

		msiEm.persist(msiPeptide);
	    }

	    msiTransaction1.commit();
	    msiTransacOk = true;
	} finally {

	    if ((msiTransaction1 != null) && !msiTransacOk) {
		LOG.info("Rollbacking Msi Transaction");

		try {
		    msiTransaction1.rollback();
		} catch (Exception ex) {
		    LOG.error("Error rollbacking Msi Transaction", ex);
		}

	    }

	}

	EntityTransaction msiTransaction2 = null;

	try {
	    /* Second transaction to test peptide repository */
	    msiTransaction2 = msiEm.getTransaction();
	    msiTransaction2.begin();
	    msiTransacOk = false;

	    final MsiPeptideRepository msiPeptideRepo = new MsiPeptideRepository(msiEm);

	    final List<Integer> ids = new ArrayList<Integer>();
	    for (int i = 0; i < PEPTIDE_COUNT; ++i) {
		ids.add(Integer.valueOf(i));
	    }

	    final List<Peptide> peptides = msiPeptideRepo.findPeptidesForIds(ids);

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
		LOG.info("Rollbacking Msi Transaction");

		try {
		    msiTransaction2.rollback();
		} catch (Exception ex) {
		    LOG.error("Error rollbacking Msi Transaction", ex);
		}

	    }

	}

	LOG.info("Retrieved Msi Peptides count: " + retrievedPeptides);

	assertTrue("Retrieved Msi Peptides count", retrievedPeptides > 0);
    }

    @Override
    public Database getDatabase() {
	return Database.MSI;
    }

    @Override
    public String getSQLScriptLocation() {
	return DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION;
    }

}
