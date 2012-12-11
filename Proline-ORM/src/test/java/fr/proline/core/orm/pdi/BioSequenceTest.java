package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.repository.PdiBioSequenceRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class BioSequenceTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(BioSequenceTest.class);

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

    @Test
    public void findBioSequence() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    BioSequence seq = PdiBioSequenceRepository.findBioSequenceForCrcAndMass(pdiEm,
		    "01FC286177012FDF", 36672.0);
	    assertThat(seq, notNullValue());
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
    public void findMissingBioSequence() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    BioSequence seq = PdiBioSequenceRepository.findBioSequenceForCrcAndMass(pdiEm, "FFFFFFF", 9999.0);
	    assertThat(seq, nullValue());
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
    public void findBioSequencePerAccessionAndSeqDB() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    BioSequence seq = PdiBioSequenceRepository.findBioSequencePerAccessionAndSeqDB(pdiEm, "Q6WN28",
		    33);
	    assertThat(seq, notNullValue());
	    assertThat(seq.getLength(), CoreMatchers.equalTo(146));
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
    public void findBioSequencesForCrcs() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    final Set<String> crcs = new HashSet<String>();
	    crcs.add("01FC286177012FDF".toUpperCase());
	    crcs.add("FA6F75FEBEAB28BA".toUpperCase());
	    crcs.add("B3BBDF9B6D1A18BA".toUpperCase());

	    final List<BioSequence> bioSequences1 = PdiBioSequenceRepository.findBioSequencesForCrcs(pdiEm,
		    crcs);
	    final int size1 = bioSequences1.size();

	    assertTrue("Retrieve at least 1 BioSequence", size1 >= 1);

	    /* Check object equality of retieved BioSequences */
	    final List<BioSequence> bioSequences2 = PdiBioSequenceRepository.findBioSequencesForCrcs(pdiEm,
		    crcs);

	    final Set<BioSequence> distinctBioSequences = new HashSet<BioSequence>();
	    distinctBioSequences.addAll(bioSequences1);
	    distinctBioSequences.addAll(bioSequences2);

	    final int distinctSize = distinctBioSequences.size();

	    assertEquals("Same BioSequences after two successive queries", size1, distinctSize);
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
