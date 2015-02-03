package fr.proline.core.orm.pdi;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.repository.PdiBioSequenceRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class BioSequenceTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(BioSequenceTest.class);

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
    
    @Override 
    public String getPropertiesFileName(){
    	return "db_pdi.properties";
    }

    @Test
    public void readBioSequence() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    BioSequence bioSeq = pdiEm.find(BioSequence.class, Long.valueOf(171L));
	    assertNotNull(bioSeq);
	    assertEquals(bioSeq.getLength(), 338);
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

    @Test
    public void findBioSequence() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    BioSequence seq = PdiBioSequenceRepository.findBioSequenceForCrcAndMass(pdiEm,
		    "01FC286177012FDF", 36672.0);
	    assertNotNull(seq);
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
	    assertNull(seq);
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
	    assertNotNull(seq);
	    assertEquals(seq.getLength(), 146);
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
