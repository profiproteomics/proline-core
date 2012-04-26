package fr.proline.core.orm.uds;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import javax.persistence.Query;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.utils.DatabaseTestCase;

public class QuantitationTest extends DatabaseTestCase {


	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager("udsdb_production");
        loadDataSet("/fr/proline/core/orm/uds/Quanti_15N_Dataset.xml");
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readQuantitation() {
		Quantitation quanti = em.find(Quantitation.class, 1);
		assertThat(quanti, CoreMatchers.notNullValue());
		assertThat(quanti.getFractionCount(), is(2));
		assertThat(quanti.getSampleReplicates().size(), is(8));
		assertThat(quanti.getBiologicalSamples().size(), is(4));
		assertThat(quanti.getMethod().getName(), equalTo("15N"));
	}

}
