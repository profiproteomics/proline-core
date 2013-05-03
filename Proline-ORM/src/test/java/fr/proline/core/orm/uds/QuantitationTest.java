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

import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class QuantitationTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(QuantitationTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();
	
	//"/fr/proline/core/orm/uds/Quanti_15N_Dataset.xml"
	String[] datasets = new String[]{
		"/dbunit/datasets/uds-db_init_dataset.xml",
		"/dbunit/datasets/uds/Quanti_15N_Dataset.xml"
	};
	
	loadCompositeDataSet(datasets);
    }

    @Test
    public void readQuantitation() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Dataset quanti = udsEm.find(Dataset.class, 1);
	    assertThat(quanti, CoreMatchers.notNullValue());
	    assertThat(quanti.getChildrenCount(), is(2));
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
