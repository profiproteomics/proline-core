package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class ProjectTest extends DatabaseTestCase {

    private ProjectRepository projectRepo;

    @Override
    public Database getDatabase() {
	return Database.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();
	loadDataSet("/fr/proline/core/orm/uds/Project_Dataset.xml");
	projectRepo = new ProjectRepository(getEntityManager());
    }

    @Test
    public void readAccount() {
	TypedQuery<UserAccount> query = getEntityManager().createQuery(
		"Select e from UserAccount e where e.login = :login", UserAccount.class);
	query.setParameter("login", "joe");
	UserAccount account = query.getSingleResult();
	assertThat(account, CoreMatchers.notNullValue());
	List<Project> ownedProjects = projectRepo.findOwnedProjects(account.getId());
	assertThat(ownedProjects.size(), is(1));
    }

    @Test
    public void readProject() {
	Project project = getEntityManager().find(Project.class, 1);
	assertThat(project, notNullValue());
	assertThat(project.getOwner().getLogin(), equalTo("joe"));
	assertThat(project.getMembers().size(), is(2));
    }

    @Test
    public void addMemberToProject() {
	final EntityManager em = getEntityManager();
	Project project = em.find(Project.class, 1);
	UserAccount jim = em.find(UserAccount.class, 3);
	project.addMember(jim);
	assertThat(project.getMembers().size(), is(3));
	em.getTransaction().begin();
	em.persist(project);
	em.getTransaction().commit();
	em.clear();
	Project rProject = em.find(Project.class, 1);
	assertTrue(project != rProject);
	assertThat(rProject.getMembers().size(), is(3));
	jim = em.find(UserAccount.class, 3);
	List<Project> projects = projectRepo.findProjects(jim.getId());
	assertThat(projects.size(), is(1));
    }

    @Test
    public void createProject() {
	final EntityManager em = getEntityManager();
	UserAccount owner = em.find(UserAccount.class, 2);
	Project project = new Project(owner);
	project.setName("Test Project");
	project.setDescription("This is a second project");
	em.getTransaction().begin();
	em.persist(project);
	em.getTransaction().commit();
	Project rProject = em.find(Project.class, 2);
	assertThat(rProject, equalTo(project));
	assertTrue(rProject.getMembers().contains(owner));
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
