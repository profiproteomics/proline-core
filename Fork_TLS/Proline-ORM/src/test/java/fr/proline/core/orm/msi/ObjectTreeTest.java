package fr.proline.core.orm.msi;

import static org.junit.Assert.*;

import javax.persistence.EntityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class ObjectTreeTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectTreeTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/msi/Resultset_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/msi-db_init_dataset.xml",
		"/dbunit/datasets/msi/Resultset_Dataset.xml" };

	loadCompositeDataSet(datasets);
    }

    @Override 
    public String getPropertiesFileName(){
    	return "db_msi.properties";
    }
    @Test
    public void readResultSetObjects() {
	final EntityManager msiEm = getConnector().createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(3L));
	    assertEquals(rs.getObjectTreeIdByName().size(), 2);
	    assertEquals(rs.getObjectTreeIdByName().get("filters_history"), Long.valueOf(1L));
	    assertEquals(rs.getObjectTreeIdByName().get("grouping_history"), Long.valueOf(2L));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void bindObjectTree2ResultSet() {

	final EntityManager msiEm = getConnector().createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(2L));
	    assertEquals(rs.getObjectTreeIdByName().size(), 0);
	    rs.putObject("filters_history", 1);
	    msiEm.getTransaction().begin();
	    msiEm.persist(rs);
	    msiEm.getTransaction().commit();

	    msiEm.clear();
	    rs = msiEm.find(ResultSet.class, Long.valueOf(2L));
	    assertEquals(rs.getObjectTreeIdByName().size(), 1);
	    assertEquals(rs.getObjectTreeIdByName().get("filters_history"), Long.valueOf(1L));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
