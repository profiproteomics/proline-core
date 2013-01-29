package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.DatasetRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class DatasetTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetTest.class);

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
    public void readDatasets() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, 1);
	    List<Dataset> rootDsets = DatasetRepository.findRootDatasetsByProject(
		    udsEm, project.getId());
	    assertThat(rootDsets.size(), is(1));
	    Dataset rootDS = rootDsets.get(0);
	    assertThat(rootDS, notNullValue());
	    assertThat(rootDS.getName(), equalTo("CB_342"));
	    assertThat(rootDS.getNumber(), is(1));
	    
	    List<Dataset> datasets = DatasetRepository.findDatasetsByProject(
		    udsEm, project.getId());
	    assertThat(datasets.size(), is(3));
	    for(Dataset nextDS : datasets){
		switch(nextDS.getId()){
		case 1 : 		
		    assertThat(nextDS.getName(), equalTo("CB_342"));
		    assertThat(nextDS.getNumber(), is(1));
		    break;
		case 2:
		    assertThat(nextDS.getName(), equalTo("CB_342_1"));
		    assertThat(nextDS.getNumber(), is(1));
		    assertThat(nextDS.getParentDataset(), CoreMatchers.sameInstance(rootDS));
		    break;		
		case 3:
		    assertThat(nextDS.getName(), equalTo("CB_342_2"));
		    assertThat(nextDS.getNumber(), is(1));
		    assertThat(nextDS.getParentDataset(), CoreMatchers.sameInstance(rootDS));
		    break;
		}
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
    
    @Test
    public void readDSHierarchy() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Dataset rootDS = udsEm.find(Dataset.class, 1);
	    Set<Dataset> childs = rootDS.getChildren();
	    assertThat(childs,notNullValue() );
	    assertThat(childs.size(), is(2) );
	    for(Dataset nextDS : childs){
		assertThat(nextDS.getParentDataset(), CoreMatchers.sameInstance(rootDS));
		assertTrue(IdentificationDataset.class.isAssignableFrom(nextDS.getClass()));
	    }
	    
	    Set<IdentificationDataset> ids = rootDS.getIdentificationDataset();
	    assertThat(ids.size(), is(2) );
    	    
	    	    
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
    public void getDatasetNames() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, 1);
	    List<String> dsNames = DatasetRepository.findRootDatasetNamesByProject(udsEm,
		    project.getId());
	    assertThat(dsNames.size(), is(1));
	    assertThat(dsNames.get(0), equalTo("CB_342"));
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
    public void getIdentificationDataset() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    IdentificationDataset idfDS = udsEm.find(IdentificationDataset.class, 2);
	    
	    assertThat(idfDS.getName(), is("CB_342_1"));
	    assertThat(idfDS.getRawFile().getRawFileName(), equalTo("CAVEN456"));
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
