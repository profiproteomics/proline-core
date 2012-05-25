package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class ProteinTest extends DatabaseTestCase {

	@Before public void setUp() throws Exception {
      initDatabase();
      initEntityManager(JPAUtil.PersistenceUnitNames.PDI_Key.getPersistenceUnitName());
      loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readProtein() {
		ProteinIdentifier protein = em.find(ProteinIdentifier.class, 341);
		assertThat(protein, notNullValue());
		assertThat(protein.getIsAcNumber(), is(false));
		assertThat(protein.getTaxon().getScientificName(), is("Pseudomonas sp."));
		assertThat(protein.getValue(), is("1A1D_PSESP"));
	}
	
	@Test public void readBioSequence() {
		BioSequence bioSeq = em.find(BioSequence.class, 171);
		assertThat(bioSeq, notNullValue());
		assertThat(bioSeq.getLength(), is(338));
		assertThat(bioSeq.getProteinIdentifiers().size(), is(4));
	}

	
	public String getSQLScriptLocation() {
		return DatabaseUtils.H2_DATABASE_PDI_SCRIPT_LOCATION;
	}
}
