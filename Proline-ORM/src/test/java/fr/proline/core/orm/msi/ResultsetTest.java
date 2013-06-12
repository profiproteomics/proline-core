package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.*;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

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

	// "/fr/proline/core/orm/msi/Resultset_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/msi-db_init_dataset.xml",
		"/dbunit/datasets/msi/Resultset_Dataset.xml" };

	loadCompositeDataSet(datasets);
    }

    @Test
    public void readMsISearch() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, Long.valueOf(1L));
	    assertNotNull(msiSearch);
	    assertEquals(msiSearch.getPeaklist().getId(), 1L);
	    Enzyme enzyme = msiEm.find(Enzyme.class, Long.valueOf(1L));
	    assertThat(msiSearch.getSearchSetting().getEnzymes(), hasItems(enzyme));
	    msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps();
	    assertEquals(msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), 1);
	    SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps().iterator().next();
	    assertEquals(map.getSeqDatabase().getName(), "Swissprot");
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
	    ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(1L));
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, Long.valueOf(1L));
	    assertNotNull(rs);
	    assertSame(rs.getMsiSearch(), msiSearch);
	    assertTrue(rs.getChildren().isEmpty());
	    assertNull(rs.getDecoyResultSet());
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
	    ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(2L));
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, Long.valueOf(1L));
	    assertNotNull(rs);
	    assertEquals(rs.getMsiSearch(), msiSearch);
	    assertTrue(rs.getChildren().isEmpty());
	    assertNotNull(rs.getDecoyResultSet());
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
	    ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(3L));
	    assertNotNull(rs);
	    assertNull(rs.getMsiSearch());
	    assertFalse(rs.getChildren().isEmpty());
	    assertEquals(rs.getChildren().size(), 2);
	    ResultSet rs1 = msiEm.find(ResultSet.class, Long.valueOf(1L));
	    ResultSet rs2 = msiEm.find(ResultSet.class, Long.valueOf(2L));
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
	    MsmsSearch msmsSearch = msiEm.find(MsmsSearch.class, Long.valueOf(1L));
	    assertNotNull(msmsSearch);
	    Enzyme enzyme = msiEm.find(Enzyme.class, Long.valueOf(1L));
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
	    ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(2L));
	    Collection<PeptideMatch> matches = PeptideMatchRepository.findPeptideMatchByResultSet(msiEm,
		    rs.getId());
	    assertEquals(matches.size(), 4);
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
	    PeptideMatch match = msiEm.find(PeptideMatch.class, Long.valueOf(1L));
	    assertNull(match.getBestPeptideMatch());
	    assertTrue(match.getChildren().isEmpty());
	    MsQuery query = match.getMsQuery();
	    assertNotNull(query);
	    assertEquals(query.getCharge(), match.getCharge());
	    assertNotNull(query.getSpectrum());
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
	    PeptideMatch match = msiEm.find(PeptideMatch.class, Long.valueOf(1L));
	    Peptide peptide = msiEm.find(Peptide.class, Long.valueOf(match.getPeptideId()));
	    assertNotNull(peptide);
	    assertEquals(peptide.getSequence(), "VLQAELK");

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
