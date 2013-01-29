package fr.proline.core.orm.msi;

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

public class ObjectTreeTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectTreeTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/msi/Resultset_Dataset.xml");
    }

    @Test
    public void readResultSetObjects() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, 3);
	    assertThat(rs.getObjectsMap().size(), is(2));
	    assertThat(rs.getObjectsMap().get("filters_history"), CoreMatchers.notNullValue());
	    assertThat(rs.getObjectsMap().get("grouping_history"), CoreMatchers.notNullValue());
	    assertThat(rs.getObjectsMap().get("filters_history"), is(1));
	    assertThat(rs.getObjectsMap().get("grouping_history"), is(2));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", msiEm);
		}
	    }

	}

    }

    @Test
    public void bindObjectTree2ResultSet() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, 2);
	    assertThat(rs.getObjectsMap().size(), is(0));
	    rs.putObject("filters_history", 1);
	    msiEm.getTransaction().begin();
	    msiEm.persist(rs);
	    msiEm.getTransaction().commit();

	    msiEm.clear();
	    rs = msiEm.find(ResultSet.class, 2);
	    assertThat(rs.getObjectsMap().size(), is(1));
	    assertThat(rs.getObjectsMap().get("filters_history"), is(1));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", msiEm);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
