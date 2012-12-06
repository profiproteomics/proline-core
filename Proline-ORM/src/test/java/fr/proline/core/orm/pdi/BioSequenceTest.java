package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.pdi.repository.PdiBioSequenceRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class BioSequenceTest extends DatabaseTestCase {

    private PdiBioSequenceRepository seqRepo;

    @Override
    public Database getDatabase() {
	return Database.PDI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");

	seqRepo = new PdiBioSequenceRepository(getEntityManager());
    }

    @Test
    public void readBioSequence() {
	BioSequence bioSeq = getEntityManager().find(BioSequence.class, 171);
	assertThat(bioSeq, notNullValue());
	assertThat(bioSeq.getLength(), is(338));
	assertThat(bioSeq.getProteinIdentifiers().size(), is(4));
    }

    @Test
    public void findBioSequence() {
	BioSequence seq = seqRepo.findBioSequenceForCrcAndMass("01FC286177012FDF", 36672.0);
	assertThat(seq, notNullValue());
    }

    @Test
    public void findMissingBioSequence() {
	BioSequence seq = seqRepo.findBioSequenceForCrcAndMass("FFFFFFF", 9999.0);
	assertThat(seq, nullValue());
    }

    @Test
    public void findBioSequencePerAccessionAndSeqDB() {
	BioSequence seq = seqRepo.findBioSequencePerAccessionAndSeqDB("Q6WN28", 33);
	assertThat(seq, notNullValue());
	assertThat(seq.getLength(), CoreMatchers.equalTo(146));
    }

    @Test
    public void findBioSequencesForCrcs() {
	final Set<String> crcs = new HashSet<String>();
	crcs.add("01FC286177012FDF".toUpperCase());
	crcs.add("FA6F75FEBEAB28BA".toUpperCase());
	crcs.add("B3BBDF9B6D1A18BA".toUpperCase());

	final List<BioSequence> bioSequences1 = seqRepo.findBioSequencesForCrcs(crcs);
	final int size1 = bioSequences1.size();

	assertTrue("Retrieve at least 1 BioSequence", size1 >= 1);

	/* Check object equality of retieved BioSequences */
	final List<BioSequence> bioSequences2 = seqRepo.findBioSequencesForCrcs(crcs);

	final Set<BioSequence> distinctBioSequences = new HashSet<BioSequence>();
	distinctBioSequences.addAll(bioSequences1);
	distinctBioSequences.addAll(bioSequences2);

	final int distinctSize = distinctBioSequences.size();

	assertEquals("Same BioSequences after two successive queries", size1, distinctSize);
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
