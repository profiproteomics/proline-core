package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class QuantitationTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(QuantitationTest.class);

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
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Quantitation quanti = udsEm.find(Quantitation.class, 1);
	    assertThat(quanti, CoreMatchers.notNullValue());
	    assertThat(quanti.getFractionCount(), is(2));
	    assertThat(quanti.getSampleReplicates().size(), is(8));
	    assertThat(quanti.getBiologicalSamples().size(), is(4));
	    assertThat(quanti.getMethod().getName(), equalTo("15N"));
	} finally {

	    if (udsEm != null) {
		try {
		    udsEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing UDS EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
