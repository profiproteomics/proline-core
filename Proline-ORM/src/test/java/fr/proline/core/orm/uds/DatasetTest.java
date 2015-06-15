package fr.proline.core.orm.uds;

import static org.junit.Assert.*;

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
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class DatasetTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetTest.class);

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
    public void readDatasets() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, Long.valueOf(1L));
	    List<Dataset> rootDsets = DatasetRepository.findRootDatasetsByProject(udsEm, project.getId());
	    assertEquals(rootDsets.size(), 1);
	    Dataset rootDS = rootDsets.get(0);
	    assertNotNull(rootDS);
	    assertEquals(rootDS.getName(), "CB_342");
	    assertEquals(rootDS.getNumber(), 1);

	    List<Dataset> datasets = DatasetRepository.findDatasetsByProject(udsEm, project.getId());
	    assertEquals(datasets.size(), 3);

	    for (final Dataset nextDS : datasets) {
		final long nextDSId = nextDS.getId();

		if (nextDSId == 1L) {
		    assertEquals(nextDS.getName(), "CB_342");
		    assertEquals(nextDS.getNumber(), 1);
		} else if (nextDSId == 2L) {
		    assertEquals(nextDS.getName(), "CB_342_1");
		    assertEquals(nextDS.getNumber(), 1);
		    assertThat(nextDS.getParentDataset(), CoreMatchers.sameInstance(rootDS));
		} else if (nextDSId == 3L) {
		    assertEquals(nextDS.getName(), "CB_342_2");
		    assertEquals(nextDS.getNumber(), 1);
		    assertThat(nextDS.getParentDataset(), CoreMatchers.sameInstance(rootDS));
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
	    Dataset rootDS = udsEm.find(Dataset.class, Long.valueOf(1L));
	    List<Dataset> childs = rootDS.getChildren();
	    assertNotNull(childs);
	    assertEquals(childs.size(), 2);

	    for (final Dataset nextDS : childs) {
		assertSame(nextDS.getParentDataset(), rootDS);
		assertTrue(nextDS instanceof IdentificationDataset);
	    }

	    Set<IdentificationDataset> ids = rootDS.getIdentificationDataset();
	    assertEquals(ids.size(), 2);
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
	    Project project = udsEm.find(Project.class, Long.valueOf(1L));
	    List<String> dsNames = DatasetRepository.findRootDatasetNamesByProject(udsEm, project.getId());
	    assertEquals(dsNames.size(), 1);
	    assertEquals(dsNames.get(0), "CB_342");
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
	    IdentificationDataset idfDS = udsEm.find(IdentificationDataset.class, Long.valueOf(2L));

	    assertEquals(idfDS.getName(), "CB_342_1");
	    assertEquals(idfDS.getRawFile().getIdentifier(), "CAVEN456");
	    assertEquals(idfDS.getRawFile().getRawFileName(), "CAVEN456.raw");
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
