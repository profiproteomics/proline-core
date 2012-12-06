package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import javax.persistence.EntityManager;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class ObjectTreeTest extends DatabaseTestCase {

    @Override
    public Database getDatabase() {
	return Database.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/msi/Resultset_Dataset.xml");
    }

    @Test
    public void readResultSetObjects() {
	ResultSet rs = getEntityManager().find(ResultSet.class, 3);
	assertThat(rs.getObjectsMap().size(), is(2));
	assertThat(rs.getObjectsMap().get("filters_history"), CoreMatchers.notNullValue());
	assertThat(rs.getObjectsMap().get("grouping_history"), CoreMatchers.notNullValue());
	assertThat(rs.getObjectsMap().get("filters_history"), is(1));
	assertThat(rs.getObjectsMap().get("grouping_history"), is(2));
    }

    @Test
    public void bindObjectTree2ResultSet() {
	final EntityManager em = getEntityManager();
	ResultSet rs = em.find(ResultSet.class, 2);
	assertThat(rs.getObjectsMap().size(), is(0));
	rs.putObject("filters_history", 1);
	em.getTransaction().begin();
	em.persist(rs);
	em.getTransaction().commit();

	em.clear();
	rs = em.find(ResultSet.class, 2);
	assertThat(rs.getObjectsMap().size(), is(1));
	assertThat(rs.getObjectsMap().get("filters_history"), is(1));
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
