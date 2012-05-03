package fr.proline.core.om.utils;


import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.om.model.msi.IonTypes;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.PtmDefinition;
import fr.proline.core.om.model.msi.PtmEvidence;
import fr.proline.core.om.model.msi.PtmNames;
import fr.proline.core.orm.ps.PeptidePtm;
import fr.proline.core.orm.utils.JPAUtil;

public class OMComparatorUtilTest extends DatabaseTestCase {
	
	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName());
        loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml");       
	}
	
	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	public String getSQLScriptLocation() {
		return "/dbscripts/ps/h2";
	}

	@Test
	public void comparePepPtm() {
		PeptidePtm pepPtm = em.find(PeptidePtm.class,1);
		
		PtmEvidence ptmEvi = new PtmEvidence(IonTypes.Precursor(),"Post-translational",  15.994915, 15.9994, true);
		PtmEvidence[] ptmEvidences ={ptmEvi};
		PtmDefinition ptmDef = new PtmDefinition(-100, "ANYWHERE", new PtmNames("Oxidation", "Oxidation or Hydroxylation"), ptmEvidences, 'M', "Post-translational", 0);
		LocatedPtm lPtm = new LocatedPtm(ptmDef, 3, Double.MAX_VALUE, Double.MAX_VALUE, "O", false, false);
		assertTrue(OMComparatorUtil.comparePeptidePtm(lPtm, pepPtm));
	
	}

	
}
