package fr.proline.core.orm.uds;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.QuantitationMethodRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class QuantitationTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(QuantitationTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/uds/Quanti_15N_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/uds-db_init_dataset.xml",
		"/dbunit/datasets/uds/Quanti_15N_Dataset.xml" };

	loadCompositeDataSet(datasets);
    }
    
    @Override 
    public String getPropertiesFileName(){
    	return "db_uds.properties";
    }
    
    @Test
    public void readQuantitation() {
	final EntityManager udsEm = getConnector().createEntityManager();

	try {
	    Dataset quanti = udsEm.find(Dataset.class, Long.valueOf(1L));
	    assertNotNull(quanti);
	    assertEquals(quanti.getChildrenCount(), 2);
	    assertEquals(quanti.getSampleReplicates().size(), 6);
	    assertEquals(quanti.getBiologicalSamples().size(), 4);
	    assertEquals(quanti.getMethod().getName(), "15N");
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
    
    //  <QUANT_METHOD ID="2" NAME="label free based on spectral counting" TYPE="label_free" ABUNDANCE_UNIT="spectral_counts"/>
    @Test
    public void getQuantitationMethod() {
    	final EntityManager udsEm = getConnector().createEntityManager();
    	QuantitationMethod qm = QuantitationMethodRepository.findQuantMethodForTypeAndAbundanceUnit(udsEm, "label_free","spectral_counts");
    	assertNotNull(qm);
    	assertEquals(2, qm.getId());
    }
    
    @Test    
    public void getUnknownQuantitationMethod() {
    	final EntityManager udsEm = getConnector().createEntityManager();
    	QuantitationMethod qm = QuantitationMethodRepository.findQuantMethodForTypeAndAbundanceUnit(udsEm, "label_free","ee");
    	assertNull(qm);
    }
    
    
    @After
    public void tearDown() {
	super.tearDown();
    }

}
