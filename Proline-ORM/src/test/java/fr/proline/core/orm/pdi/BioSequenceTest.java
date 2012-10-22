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
import fr.proline.repository.utils.DatabaseTestCase;

public class BioSequenceTest extends DatabaseTestCase {

    private PdiBioSequenceRepository seqRepo;

    @Before
    public void setUp() throws Exception {
	initDatabase();
	initEntityManager("pdidb_production");
	loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");
	seqRepo = new PdiBioSequenceRepository(getEntityManager());
    }

    @After
    public void tearDown() throws Exception {
	super.tearDown();
    }

    @Test
    public void readBioSequence() {
	BioSequence bioSeq = em.find(BioSequence.class, 171);
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
    public void finBioSequencesForCrcs() {
	final Set<String> crcs = new HashSet<String>();
	crcs.add("01FC286177012FDF");
	final List<BioSequence> bioSequences = seqRepo.findBioSequencesForCrcs(crcs);
	assertTrue("At least one BioSequence find by crcs", bioSequences.size() >= 1);
    }

    public String getSQLScriptLocation() {
	return "/dbscripts/pdi/h2";
    }
}
