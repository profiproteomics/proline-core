package fr.proline.core.om.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.orm.ps.PeptidePtm;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class OMConverterUtilTest extends DatabaseTestCase {
	
	private OMConverterUtil converter; 

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName());
        loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml");
        converter = new OMConverterUtil( true );
	}
	
	@Test
	public void test() {
		fr.proline.core.orm.ps.Peptide ormPep = em.find(fr.proline.core.orm.ps.Peptide.class, 4);
		fr.proline.core.om.model.msi.Peptide omPep = converter.convertPeptidePsORM2OM(ormPep);
		assertNotNull(omPep);
		assertEquals(omPep.calculatedMass(), ormPep.getCalculatedMass(),0.01d);
		assertEquals(omPep.sequence(), ormPep.getSequence());
		
		//Test PTMs
		Iterator<PeptidePtm> pepPTMsIT = ormPep.getPtms().iterator();
		ArrayList<String> pepPtmsNames =new ArrayList<String>();
		ArrayList<Integer> pepPtmsLocations =new ArrayList<Integer>();
		while(pepPTMsIT.hasNext()){
			PeptidePtm nextORMPtm = pepPTMsIT.next();
			pepPtmsNames.add(nextORMPtm.getSpecificity().getPtm().getFullName());
			pepPtmsLocations.add(nextORMPtm.getSeqPosition());
		}
		
		LocatedPtm[] omPtms = omPep.ptms();		
		for(int i=0;i<omPtms.length; i++){
			LocatedPtm nextOMPtm = omPtms[i];
			int indexPtm = pepPtmsNames.indexOf(nextOMPtm.definition().names().fullName());
			if(indexPtm == -1)
				fail("Ptm not found in OM Peptide");
			else 
				assertEquals(Integer.valueOf(nextOMPtm.seqPosition()), pepPtmsLocations.get(indexPtm));
		}
		ormPep.getPtms().iterator();
	}

	public String getSQLScriptLocation() {
		return DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION;
	}
}
