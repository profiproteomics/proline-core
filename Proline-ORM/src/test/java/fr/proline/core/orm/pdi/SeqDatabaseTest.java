package fr.proline.core.orm.pdi;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.repository.PdiSeqDatabaseRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class SeqDatabaseTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(SeqDatabaseTest.class);

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
    public void findSeqDbPerNameAndFile() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    SequenceDbInstance seqDB = PdiSeqDatabaseRepository.findSeqDbInstanceWithNameAndFile(pdiEm,
		    "sprot", "H:/Sequences/uniprot/knowledgebase2011_06/uniprot_sprot.fasta");
	    assertNotNull(seqDB);
	    assertEquals(seqDB.getSequenceCount(), Integer.valueOf(4));
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
    public void findUnknownSeqDbPerNameAndFile() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    SequenceDbInstance seqDB = PdiSeqDatabaseRepository.findSeqDbInstanceWithNameAndFile(pdiEm,
		    "Sprot_2011_06", "/path/to/myDB.fasta");
	    assertNull(seqDB);
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
    public void readSeqDbInstance() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager pdiEm = emf.createEntityManager();

	try {
	    SequenceDbInstance seqDB = pdiEm.find(SequenceDbInstance.class, Long.valueOf(33L));
	    assertNotNull(seqDB);
	    assertEquals(seqDB.getSequenceCount(), Integer.valueOf(4));
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
