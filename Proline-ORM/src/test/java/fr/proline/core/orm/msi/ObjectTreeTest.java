package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.msi.repository.PeptideMatchRepository;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class ObjectTreeTest extends DatabaseTestCase {

	PeptideMatchRepository pmRepo;

	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName());
        loadDataSet("/fr/proline/core/orm/msi/Resultset_Dataset.xml");
        pmRepo = new PeptideMatchRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	

	@Test public void readResultSetObjects() {
		ResultSet rs = em.find(ResultSet.class, 3);
		assertThat(rs.getObjectsMap().size(), is(2));
		assertThat(rs.getObjectsMap().get("filters_history"), CoreMatchers.notNullValue());
		assertThat(rs.getObjectsMap().get("grouping_history"), CoreMatchers.notNullValue());
		assertThat(rs.getObjectsMap().get("filters_history"), is(1));
		assertThat(rs.getObjectsMap().get("grouping_history"), is(2));		
		
	}
	
	@Test public void bindObjectTree2ResultSet() {
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
	
	@Override
	public String getSQLScriptLocation() {
		return DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION;
	}

}
