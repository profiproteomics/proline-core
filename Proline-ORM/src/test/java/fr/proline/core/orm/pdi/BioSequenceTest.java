package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.pdi.repository.BioSequenceRepository;
import fr.proline.repository.utils.DatabaseTestCase;

public class BioSequenceTest extends DatabaseTestCase {

	private BioSequenceRepository seqRepo;
	
	@Before public void setUp() throws Exception {
      initDatabase();
      initEntityManager("pdidb_production");
      loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");
      seqRepo = new BioSequenceRepository(getEntityManager());
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	
	@Test public void readBioSequence() {
		BioSequence bioSeq = em.find(BioSequence.class, 171);
		assertThat(bioSeq, notNullValue());
		assertThat(bioSeq.getLength(), is(338));
		assertThat(bioSeq.getProteinIdentifiers().size(), is(4));
	}

	@Test public void findBioSequence() {
		BioSequence seq = seqRepo.findBioSequence("01FC286177012FDF", "AA", 36672.0);
		assertThat(seq, notNullValue());
	}
	
	@Test public void findMissingBioSequence() {
		BioSequence seq = seqRepo.findBioSequence("FFFFFFF", "AA", 9999.0);
		assertThat(seq, nullValue());
	}
	
	@Test public void findBioSequencePerAccessionAndSeqDB() {
		BioSequence seq = seqRepo.findBioSequencePerAccessionAndSeqDB("Q6WN28",33 );
		assertThat(seq, notNullValue());
		assertThat(seq.getLength(), CoreMatchers.equalTo(146));
	}
	
	public String getSQLScriptLocation() {
		return "/dbscripts/pdi/h2";
	}
}
