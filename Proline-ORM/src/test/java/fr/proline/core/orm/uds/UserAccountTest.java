package fr.proline.core.orm.uds;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.persistence.TypedQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class UserAccountTest extends DatabaseTestCase {

	private ProjectRepository projectRepo;

	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager(JPAUtil.PersistenceUnitNames.UDS_Key.getPersistenceUnitName());
        loadDataSet("/fr/proline/core/orm/uds/Project_Dataset.xml");
        projectRepo = new ProjectRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	

	@Test public void writeUserAccount() {
		UserAccount account = new UserAccount();
		account.setLogin("bruley");
		account.setCreationMode("manual");
		em.getTransaction().begin();
		em.persist(account);
		em.getTransaction().commit();
	}
	
	@Test public void readAccount() {
		TypedQuery<UserAccount> query = em.createQuery("Select e from UserAccount e where e.login = :login", UserAccount.class);
		query.setParameter("login", "joe");
		UserAccount account = query.getSingleResult();
		assertThat(account, notNullValue());
		List<Project> ownedProjects = projectRepo.findOwnedProjects(account.getId()); 
		assertThat(ownedProjects.size(), is(1));
	}
	
	@Test public void listAccounts() {
		UserAccount user = em.find(UserAccount.class, 2);
		TypedQuery<UserAccount> query = em.createQuery("select u from UserAccount u order by u.id",UserAccount.class);
		List<UserAccount> users = query.getResultList();
		assertTrue(users.get(1) == user);

	}

	@Override
	public String getSQLScriptLocation() {
		return DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION;
	}
	
}
