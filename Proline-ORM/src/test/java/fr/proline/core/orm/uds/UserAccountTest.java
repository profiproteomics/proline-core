package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class UserAccountTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(UserAccountTest.class);

    @Override
    public Database getDatabase() {
	return Database.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/uds/Project_Dataset.xml");
    }

    @Test
    public void writeUserAccount() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    UserAccount account = new UserAccount();
	    account.setLogin("bruley");
	    account.setCreationMode("manual");
	    udsEm.getTransaction().begin();
	    udsEm.persist(account);
	    udsEm.getTransaction().commit();
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

    @Test
    public void readAccount() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    TypedQuery<UserAccount> query = udsEm.createQuery(
		    "Select e from UserAccount e where e.login = :login", UserAccount.class);
	    query.setParameter("login", "joe");
	    UserAccount account = query.getSingleResult();
	    assertThat(account, notNullValue());
	    List<Project> ownedProjects = ProjectRepository.findOwnedProjects(udsEm, account.getId());
	    assertThat(ownedProjects.size(), is(1));
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

    @Test
    public void listAccounts() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    UserAccount user = udsEm.find(UserAccount.class, 2);
	    TypedQuery<UserAccount> query = udsEm.createQuery("select u from UserAccount u order by u.id",
		    UserAccount.class);
	    List<UserAccount> users = query.getResultList();
	    assertTrue(users.get(1) == user);
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
