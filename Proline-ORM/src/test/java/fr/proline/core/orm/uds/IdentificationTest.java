package fr.proline.core.orm.uds;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.IdentificationRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class IdentificationTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(IdentificationTest.class);

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
    public void readIdentification() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, 1);
	    List<Identification> identifications = IdentificationRepository.findIdentificationsByProject(
		    udsEm, project.getId());
	    assertThat(identifications.size(), is(1));
	    Identification identification = identifications.get(0);
	    assertThat(identification, notNullValue());
	    assertThat(identification.getName(), equalTo("CB_342"));
	    assertThat(identification.getNumber(), is(1));
	    assertThat(identification.getFractions().size(), is(identification.getFractionCount()));
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
    public void getIdentificationNames() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager udsEm = emf.createEntityManager();

	try {
	    Project project = udsEm.find(Project.class, 1);
	    List<String> identifications = IdentificationRepository.findIdentificationNamesByProject(udsEm,
		    project.getId());
	    assertThat(identifications.size(), is(1));
	    assertThat(identifications.get(0), equalTo("CB_342"));
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
