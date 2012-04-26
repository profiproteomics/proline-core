package fr.proline.core.orm.provider.msi;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import scala.None$;
import scala.Option;
import scala.collection.mutable.ArrayBuffer;
import fr.proline.core.om.model.msi.IonTypes;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.Peptide;
import fr.proline.core.om.model.msi.PtmDefinition;
import fr.proline.core.om.model.msi.PtmEvidence;
import fr.proline.core.om.model.msi.PtmNames;
import fr.proline.core.orm.utils.DatabaseTestCase;

public class ORMPeptideProviderTest extends DatabaseTestCase {

	ORMPeptideProvider ormPepProvider; 
	private final String SEQ_TO_FOUND = "LTGMAFR";
	 
	@Override
	public String getSQLScriptLocation() {
		return "/dbscripts/ps/h2";
	}
	
	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager("psdb_production");
        loadDataSet("/fr/proline/core/orm/ps/Unimod_Dataset.xml");
        ormPepProvider = ORMPeptideProvider.getInstance(em);
	}
	
	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test
	public void getSinglePeptide() {
		Option<Peptide> pep = ormPepProvider.getPeptide(4);
		assertThat(pep, CoreMatchers.notNullValue());
		Assert.assertNotSame(pep, None$.MODULE$);		
		assertThat(pep.get().calculatedMass(), CoreMatchers.equalTo(810.405807));
	}

	@Test
	public void getMultiplePeptides() {
		ArrayBuffer<Object> ids =new ArrayBuffer<Object>();
		ids.$plus$eq(0);
		ids.$plus$eq(1);
		ids.$plus$eq(4);
		Option<Peptide>[] peps = ormPepProvider.getPeptides(ids);
		assertThat(peps, CoreMatchers.notNullValue());
		assertThat(peps.length, CoreMatchers.equalTo(3));
		assertThat(peps[2].get().id(),CoreMatchers.equalTo(4));
		assertThat(peps[2].get().calculatedMass(), CoreMatchers.equalTo(810.405807));
	}

	@Test
	public void getPeptideOnSeqAndNoPtms() {
		LocatedPtm[] ptms = new LocatedPtm[0];
		
		Option<Peptide> pep = ormPepProvider.getPeptide(SEQ_TO_FOUND, ptms);
		assertThat(pep, CoreMatchers.notNullValue());
		assertNotSame(pep, None$.MODULE$);	
		assertTrue(pep.get().ptms() == null ||  pep.get().ptms().length==0  );
	}
	
	@Test
	public void getPeptideOnSeqAndPtms() {		
		LocatedPtm[] ptms = new LocatedPtm[1];
		
		PtmEvidence ptmEvi = new PtmEvidence(IonTypes.Precursor(),"",  Double.MAX_VALUE, Double.MAX_VALUE, false);
		PtmEvidence[] ptmEvidences ={ptmEvi};
		PtmDefinition ptmDef = new PtmDefinition(0, "ANYWHERE", new PtmNames("Oxidation", "Oxidation or Hydroxylation"), ptmEvidences, 'M', null, 0);
		ptms[0] = new LocatedPtm(ptmDef, 3, Double.MAX_VALUE, Double.MAX_VALUE, "O", false, false);
		
		Option<Peptide> pep = ormPepProvider.getPeptide(SEQ_TO_FOUND, ptms);
		assertThat(pep, CoreMatchers.notNullValue());
		assertNotSame(pep, None$.MODULE$);	
		assertTrue(pep.get().ptms().length== 1);
		assertThat(pep.get().ptms()[0].seqPosition(), CoreMatchers.equalTo(3));
	}
	
}
