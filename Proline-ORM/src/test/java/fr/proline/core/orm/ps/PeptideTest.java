package fr.proline.core.orm.ps;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.ps.repository.PeptideRepository;
import fr.proline.core.orm.utils.DatabaseTestCase;

public class PeptideTest extends DatabaseTestCase {
	 PeptideRepository pepRepo;
	
	 private final String SEQ_TO_FOUND = "LTGMAFR";
	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager("psdb_production");
        loadDataSet("/fr/proline/core/orm/ps/Unimod_Dataset.xml");
        pepRepo = new PeptideRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readPeptidesBySeq() {
		List<Peptide> peps = pepRepo.findPeptidesBySequence(SEQ_TO_FOUND);
		assertNotNull(peps);
		assertEquals(2, peps.size());
		boolean foundPepWOPtm = false;
		boolean foundPepWithPtm = false;
		for(Peptide pep : peps){
			if(pep.getPtms() == null || pep.getPtms().isEmpty())
				foundPepWOPtm = true;
			else
				foundPepWithPtm = true;
		}
		assertTrue(foundPepWithPtm);
		assertTrue(foundPepWOPtm);
		
		
	}
	
	@Override
	public String getSQLScriptLocation() {
		return "/dbscripts/ps/h2";
	}
	
}
