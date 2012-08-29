package fr.proline.core.orm.ps;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.ps.repository.PsPeptideRepository;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class PeptideTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(PeptideTest.class);

    private static final String SEQ_TO_FOUND = "LTGMAFR";

    private static final int PEPTIDE_COUNT = 10;

    PsPeptideRepository pepRepo;

    @Before
    public void setUp() throws Exception {
	initDatabase();
	initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName());
	loadDataSet("/fr/proline/core/orm/ps/Unimod_Dataset.xml");
	pepRepo = new PsPeptideRepository(em);
    }

    @After
    public void tearDown() throws Exception {
	super.tearDown();
    }

    @Test
    public void readPeptidesBySeq() {

	List<Peptide> peps = pepRepo.findPeptidesForSequence(SEQ_TO_FOUND);
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

    }

    @Test
    public void retrievePeptideForIds() {
	int retrievedPeptides = 0;

	/* Build a 0..9 Set */
	final Set<Integer> ids = new HashSet<Integer>();
	for (int i = 0; i < PEPTIDE_COUNT; ++i) {
	    ids.add(Integer.valueOf(i));
	}

	final List<Peptide> peptides = pepRepo.findPeptidesForIds(ids);

	if (peptides != null) {
	    for (final Peptide p : peptides) {
		if (p != null) {
		    ++retrievedPeptides;
		}
	    }
	}

	LOG.info("Retrieved Msi Peptides count: " + retrievedPeptides);

	Assert.assertTrue("Retrieved Msi Peptides count", retrievedPeptides > 0);
    }

    @Override
    public String getSQLScriptLocation() {
	return DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION;
    }

}
