package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.hasItems;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.PeptideMatchRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.util.MathUtils;

public class ResultsetTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(ResultsetTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();
	
	//"/fr/proline/core/orm/msi/Resultset_Dataset.xml"
	String[] datasets = new String[]{
		"/dbunit/datasets/msi-db_init_dataset.xml",
		"/dbunit/datasets/msi/Resultset_Dataset.xml"
	};

	loadCompositeDataSet(datasets);
    }

    @Test
    public void readMsiSearch() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, 1);
	    assertThat(msiSearch, CoreMatchers.notNullValue());
	    assertThat(msiSearch.getPeaklist().getId(), is(1));
	    Enzyme enzyme = msiEm.find(Enzyme.class, 1);
	    assertThat(msiSearch.getSearchSetting().getEnzymes(), hasItems(enzyme));
	    msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps();
	    assertThat(msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), is(1));
	    SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps().iterator().next();
	    assertThat(map.getSeqDatabase().getName(), is("Swissprot"));
	    assertEquals(msiSearch.getSearchSetting().getInstrumentConfig().getId(), msiSearch
		    .getSearchSetting().getInstrumentConfigId());
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void readDecoyResultSet() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, 1);
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, 1);
	    assertThat(rs, CoreMatchers.notNullValue());
	    assertThat(rs.getMsiSearch(), sameInstance(msiSearch));
	    assertThat(rs.getChildren().isEmpty(), is(true));
	    assertThat(rs.getDecoyResultSet(), CoreMatchers.nullValue());
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void readResultSet() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, 2);
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, 1);
	    assertThat(rs, CoreMatchers.notNullValue());
	    assertThat(rs.getMsiSearch(), is(msiSearch));
	    assertThat(rs.getChildren().isEmpty(), is(true));
	    assertThat(rs.getDecoyResultSet(), CoreMatchers.notNullValue());
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void readResultSetHierarchy() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, 3);
	    assertThat(rs, CoreMatchers.notNullValue());
	    assertThat(rs.getMsiSearch(), CoreMatchers.nullValue());
	    assertThat(rs.getChildren().isEmpty(), is(false));
	    assertThat(rs.getChildren().size(), is(2));
	    ResultSet rs1 = msiEm.find(ResultSet.class, 1);
	    ResultSet rs2 = msiEm.find(ResultSet.class, 2);
	    assertThat(rs.getChildren(), hasItems(rs1, rs2));
	    assertThat(rs.getChildren(), not(hasItems(rs)));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void testSearchInheritance() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsmsSearch msmsSearch = msiEm.find(MsmsSearch.class, 1);
	    assertThat(msmsSearch, CoreMatchers.notNullValue());
	    Enzyme enzyme = msiEm.find(Enzyme.class, 1);
	    assertThat(msmsSearch.getEnzymes(), hasItems(enzyme));
	    assertEquals(msmsSearch.getFragmentMassErrorTolerance(), 0.8, MathUtils.EPSILON_LOW_PRECISION);
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void testReadPeptideMatches() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    ResultSet rs = msiEm.find(ResultSet.class, 2);
	    Collection<PeptideMatch> matches = PeptideMatchRepository.findPeptideMatchByResultSet(msiEm,
		    rs.getId());
	    assertThat(matches.size(), is(4));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void peptideMatchRelations() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    PeptideMatch match = msiEm.find(PeptideMatch.class, 1);
	    assertThat(match.getBestPeptideMatch(), CoreMatchers.nullValue());
	    assertThat(match.getChildren().isEmpty(), is(true));
	    MsQuery query = match.getMsQuery();
	    assertThat(query, CoreMatchers.notNullValue());
	    assertThat(query.getCharge(), is(match.getCharge()));
	    assertThat(query.getSpectrum(), CoreMatchers.notNullValue());
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}
    }

    @Test
    public void peptidesFromMatches() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    PeptideMatch match = msiEm.find(PeptideMatch.class, 1);
	    Peptide peptide = msiEm.find(Peptide.class, match.getPeptideId());
	    assertThat(peptide, CoreMatchers.notNullValue());
	    assertThat(peptide.getSequence(), is("VLQAELK"));

	    List<PeptideMatch> matches = PeptideMatchRepository.findPeptideMatchByPeptide(msiEm,
		    match.getPeptideId());
	    assertThat(matches, hasItems(match));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
