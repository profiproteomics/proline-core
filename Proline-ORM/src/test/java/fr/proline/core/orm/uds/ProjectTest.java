package fr.proline.core.orm.uds;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
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
    
    @Override 
    public String getPropertiesFileName(){
    	return "db_uds.properties";
    }

    @Test
    public void readAccount() {
	final EntityManager udsEm = getConnector().createEntityManager();

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
	final EntityManager udsEm = getConnector().createEntityManager();

	try {		
	    Project project = udsEm.find(Project.class, Long.valueOf(1L));
	    Set<ProjectUserAccountMap> usersMap = project.getProjectUserAccountMap();
	    assertNotNull(project);
	    assertEquals(project.getOwner().getLogin(), "joe");
	    assertEquals(usersMap.size(), 3);
	    int nbrRW = 0;
	    int nbrRO = 0;
	    for(ProjectUserAccountMap member : usersMap){
	    	if(member.getWritePermission())
	    		nbrRW++;
	    	else
	    		nbrRO++;
	    		
	    }
	    assertEquals(1, nbrRO);
	    assertEquals(2, nbrRW);
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
	final EntityManager udsEm = getConnector().createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, Long.valueOf(1L));
	    UserAccount jim = udsEm.find(UserAccount.class, Long.valueOf(3L));
	    project.addMember(jim, true);
	    assertEquals(4, project.getProjectUserAccountMap().size() );
	    udsEm.getTransaction().begin();
	    udsEm.persist(project);
	    for(ProjectUserAccountMap nextUser :project.getProjectUserAccountMap()){
	    	udsEm.persist(nextUser);	
	    }	    
	    udsEm.getTransaction().commit();
	    udsEm.clear();
	    Project rProject = udsEm.find(Project.class, Long.valueOf(1L));
	    assertNotSame(project, rProject);
	    assertEquals(4, rProject.getProjectUserAccountMap().size());
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
	final EntityManager udsEm = getConnector().createEntityManager();

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
	    boolean ownerFound = false;
	    for(ProjectUserAccountMap nextUserInMap : rProject.getProjectUserAccountMap()){
	    	if(nextUserInMap.getUserAccount().equals(owner)){
	    		ownerFound = true;
	    		break;
	    	}
	    }
	    assertTrue(ownerFound);

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
