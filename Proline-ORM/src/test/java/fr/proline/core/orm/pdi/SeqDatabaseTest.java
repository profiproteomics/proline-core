package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.pdi.repository.PdiSeqDatabaseRepository;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class SeqDatabaseTest extends DatabaseTestCase {

	@Before public void setUp() throws Exception {
      initDatabase();
      initEntityManager(JPAUtil.PersistenceUnitNames.PDI_Key.getPersistenceUnitName());
      loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	
	@Test public void findSeqDbPerNameAndFile() {
		PdiSeqDatabaseRepository repo = new PdiSeqDatabaseRepository(em);
		SequenceDbInstance seqDB  = repo.findSeqDbInstanceWithNameAndFile("sprot", "H:/Sequences/uniprot/knowledgebase2011_06/uniprot_sprot.fasta");		
		assertThat(seqDB, notNullValue());
		assertThat(seqDB.getSequenceCount(), is(4));
	}
	
	
	@Test public void findUnknownSeqDbPerNameAndFile() {
		PdiSeqDatabaseRepository repo = new PdiSeqDatabaseRepository(em);
		SequenceDbInstance seqDB  = repo.findSeqDbInstanceWithNameAndFile("Sprot_2011_06", "/path/to/myDB.fasta");		
		assertThat(seqDB, CoreMatchers.nullValue());
	}
	
	@Test public void readSeqDbInstance() {
		SequenceDbInstance seqDB  = em.find(SequenceDbInstance.class, 33);
		assertThat(seqDB, notNullValue());
		assertThat(seqDB.getSequenceCount(), is(4));
	}

	
	public String getSQLScriptLocation() {
		return DatabaseUtils.H2_DATABASE_PDI_SCRIPT_LOCATION;
	}
}
