package fr.proline.core.orm.uds;

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
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class ProjectTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/uds/Project_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/uds-db_init_dataset.xml",
		"/dbunit/datasets/uds/Project_Dataset.xml" };

	loadCompositeDataSet(datasets);
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
	    assertNotNull(account);
	    List<Project> ownedProjects = ProjectRepository.findOwnedProjects(udsEm, account.getId());
	    assertEquals(ownedProjects.size(), 1);
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
	    Project project = udsEm.find(Project.class, Long.valueOf(1L));
	    assertNotNull(project);
	    assertEquals(project.getOwner().getLogin(), "joe");
	    assertEquals(project.getMembers().size(), 2);
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
	    Project project = udsEm.find(Project.class, Long.valueOf(1L));
	    UserAccount jim = udsEm.find(UserAccount.class, Long.valueOf(3L));
	    project.addMember(jim);
	    assertEquals(project.getMembers().size(), 3);
	    udsEm.getTransaction().begin();
	    udsEm.persist(project);
	    udsEm.getTransaction().commit();
	    udsEm.clear();
	    Project rProject = udsEm.find(Project.class, Long.valueOf(1L));
	    assertNotSame(project, rProject);
	    assertEquals(rProject.getMembers().size(), 3);
	    jim = udsEm.find(UserAccount.class, Long.valueOf(3L));
	    List<Project> projects = ProjectRepository.findProjects(udsEm, jim.getId());
	    assertEquals(projects.size(), 1);
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
	    UserAccount owner = udsEm.find(UserAccount.class, Long.valueOf(2L));
	    Project project = new Project(owner);
	    project.setName("Test Project");
	    project.setDescription("This is a second project");
	    udsEm.getTransaction().begin();
	    udsEm.persist(project);
	    udsEm.getTransaction().commit();
	    Project rProject = udsEm.find(Project.class, Long.valueOf(2L));
	    assertEquals(rProject, project);
	    assertTrue(rProject.getMembers().contains(owner));

	    final List<Long> projectIds = ProjectRepository.findAllProjectIds(udsEm);
	    assertTrue("Project Ids List", (projectIds != null) && !projectIds.isEmpty());

	    if (LOG.isDebugEnabled()) {
		LOG.debug("Total count of Projects: " + projectIds.size());
	    }

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
