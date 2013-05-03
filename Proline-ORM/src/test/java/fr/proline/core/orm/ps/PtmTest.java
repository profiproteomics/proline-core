package fr.proline.core.orm.ps;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;

import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.ps.repository.PsPtmRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;

public class PtmTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(PtmTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.PS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	//"/fr/proline/core/orm/ps/Unimod_Dataset.xml"
	String[] datasets = new String[]{
		"/dbunit/datasets/ps-db_init_dataset.xml",
		"/dbunit/datasets/ps/Peptides_Dataset.xml"
	};
	
	loadCompositeDataSet(datasets);
    }

    @Test
    public void readPtm() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager psEm = emf.createEntityManager();

	try {
	    TypedQuery<Ptm> query = psEm.createQuery(
		    "Select ptm from Ptm ptm where ptm.unimodId = :unimod_id", Ptm.class);
	    query.setParameter("unimod_id", 21);
	    Ptm ptm = query.getSingleResult();
	    assertThat(ptm.getFullName(), equalTo("Phosphorylation"));
	    Set<PtmEvidence> evidences = ptm.getEvidences();
	    assertThat(evidences.size(), is(5));

	    Set<PtmSpecificity> specificities = ptm.getSpecificities();
	    assertThat(specificities.size(), is(8));
	} finally {

	    if (psEm != null) {
		try {
		    psEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void findPtmByName() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager psEm = emf.createEntityManager();

	try {
	    Ptm phosPtm = PsPtmRepository.findPtmForName(psEm, "Phospho");
	    assertThat(phosPtm, notNullValue());
	    assertThat(phosPtm.getShortName(), equalTo("Phospho"));
	    assertThat(phosPtm.getFullName(), equalTo("Phosphorylation"));
	    Ptm phosPtm2 = PsPtmRepository.findPtmForName(psEm, "PHosPHo");
	    assertThat(phosPtm2, notNullValue());
	    assertThat(phosPtm2, sameInstance(phosPtm));
	    Ptm phosPtm3 = PsPtmRepository.findPtmForName(psEm, "PHosPHorylation");
	    assertThat(phosPtm3, notNullValue());
	    assertThat(phosPtm3, sameInstance(phosPtm));
	} finally {

	    if (psEm != null) {
		try {
		    psEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void findPtmClassification() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager psEm = emf.createEntityManager();

	try {
	    final PtmClassification classification = PsPtmRepository.findPtmClassificationForName(psEm,
		    "Chemical derivative");

	    assertNotNull("Chemical derivative PtmClassification", classification);
	} finally {

	    if (psEm != null) {
		try {
		    psEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing PS EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
