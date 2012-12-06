package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class QuantitationTest extends DatabaseTestCase {

    @Override
    public Database getDatabase() {
	return Database.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/uds/Quanti_15N_Dataset.xml");
    }

    @Test
    public void readQuantitation() {
	Quantitation quanti = getEntityManager().find(Quantitation.class, 1);
	assertThat(quanti, CoreMatchers.notNullValue());
	assertThat(quanti.getFractionCount(), is(2));
	assertThat(quanti.getSampleReplicates().size(), is(8));
	assertThat(quanti.getBiologicalSamples().size(), is(4));
	assertThat(quanti.getMethod().getName(), equalTo("15N"));
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
