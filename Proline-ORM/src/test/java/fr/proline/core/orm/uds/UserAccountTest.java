package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class UserAccountTest extends DatabaseTestCase {

    private ProjectRepository projectRepo;

    @Before
    public void setUp() throws Exception {
	initDatabase();
	loadDataSet("/fr/proline/core/orm/uds/Project_Dataset.xml");
	projectRepo = new ProjectRepository(getEntityManager());
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

    @Test
    public void writeUserAccount() {
	UserAccount account = new UserAccount();
	account.setLogin("bruley");
	account.setCreationMode("manual");
	final EntityManager em = getEntityManager();
	em.getTransaction().begin();
	em.persist(account);
	em.getTransaction().commit();
    }

    @Test
    public void readAccount() {
	TypedQuery<UserAccount> query = getEntityManager().createQuery(
		"Select e from UserAccount e where e.login = :login", UserAccount.class);
	query.setParameter("login", "joe");
	UserAccount account = query.getSingleResult();
	assertThat(account, notNullValue());
	List<Project> ownedProjects = projectRepo.findOwnedProjects(account.getId());
	assertThat(ownedProjects.size(), is(1));
    }

    @Test
    public void listAccounts() {
	final EntityManager em = getEntityManager();
	UserAccount user = em.find(UserAccount.class, 2);
	TypedQuery<UserAccount> query = em.createQuery("select u from UserAccount u order by u.id",
		UserAccount.class);
	List<UserAccount> users = query.getResultList();
	assertTrue(users.get(1) == user);
    }

    @Override
    public Database getDatabase() {
	return Database.UDS;
    }

    @Override
    public String getSQLScriptLocation() {
	return DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION;
    }

}
