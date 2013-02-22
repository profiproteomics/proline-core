package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class ProjectTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/uds/Project_Dataset.xml");
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
	    assertThat(account, CoreMatchers.notNullValue());
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
    public void readProject() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, 1);
	    assertThat(project, notNullValue());
	    assertThat(project.getOwner().getLogin(), equalTo("joe"));
	    assertThat(project.getMembers().size(), is(2));
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
    public void addMemberToProject() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, 1);
	    UserAccount jim = udsEm.find(UserAccount.class, 3);
	    project.addMember(jim);
	    assertThat(project.getMembers().size(), is(3));
	    udsEm.getTransaction().begin();
	    udsEm.persist(project);
	    udsEm.getTransaction().commit();
	    udsEm.clear();
	    Project rProject = udsEm.find(Project.class, 1);
	    assertTrue(project != rProject);
	    assertThat(rProject.getMembers().size(), is(3));
	    jim = udsEm.find(UserAccount.class, 3);
	    List<Project> projects = ProjectRepository.findProjects(udsEm, jim.getId());
	    assertThat(projects.size(), is(1));
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
    public void createProject() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    UserAccount owner = udsEm.find(UserAccount.class, 2);
	    Project project = new Project(owner);
	    project.setName("Test Project");
	    project.setDescription("This is a second project");
	    udsEm.getTransaction().begin();
	    udsEm.persist(project);
	    udsEm.getTransaction().commit();
	    Project rProject = udsEm.find(Project.class, 2);
	    assertThat(rProject, equalTo(project));
	    assertTrue(rProject.getMembers().contains(owner));
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
