package fr.proline.core.orm.ps;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.ps.repository.PsPeptideRepository;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class PeptideTest extends DatabaseTestCase {
	 PsPeptideRepository pepRepo;
	
	 private final String SEQ_TO_FOUND = "LTGMAFR";
	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName());
        loadDataSet("/fr/proline/core/orm/ps/Unimod_Dataset.xml");
        pepRepo = new PsPeptideRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readPeptidesBySeq() {
		List<Peptide> peps = pepRepo.findPeptidesForSequence(SEQ_TO_FOUND);
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
		return DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION;
	}
	
}
