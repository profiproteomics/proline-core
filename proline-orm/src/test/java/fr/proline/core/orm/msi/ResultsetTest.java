package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.MatcherAssert;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import fr.proline.core.orm.MergeMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.PeptideMatchRepository;
import fr.proline.core.orm.msi.repository.PeptideReadablePtmStringRepository;
import fr.proline.core.orm.msi.repository.ResultSetRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;
import fr.profi.util.MathUtils;

public class ResultsetTest extends DatabaseTestCase {

	private static final Logger LOG = LoggerFactory.getLogger(ResultsetTest.class);

	@Override
	public ProlineDatabaseType getProlineDatabaseType() {
		return ProlineDatabaseType.MSI;
	}

	@Before
	public void setUp() throws Exception {
		initDatabase();

		String[] datasets = new String[] { "/dbunit/Init/msi-db.xml", "/dbunit/datasets/msi/Resultset_Dataset.xml" };
		loadCompositeDataSet(datasets);

	}

	@Override
	public String getPropertiesFileName() {
		return "db_msi.properties";
	}

	@Test
	public void testMergeMode(){
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(1L));
			MergeMode mm = rs.getMergeMode();
			assertEquals(MergeMode.NO_MERGE, mm);

			ResultSet rs2 = msiEm.find(ResultSet.class, Long.valueOf(2L));
			MergeMode mm2 = rs2.getMergeMode();
			assertEquals(MergeMode.UNION, mm2);

		} catch (Exception e) {
			fail("error getting MergeMode "+e.getMessage());
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
	public void readMsISearch() {
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			MsiSearch msiSearch = msiEm.find(MsiSearch.class, Long.valueOf(1L));
			assertNotNull(msiSearch);
			assertEquals(msiSearch.getPeaklist().getId(), 1L);
			Enzyme enzyme = msiEm.find(Enzyme.class, Long.valueOf(1L));
			MatcherAssert.assertThat(msiSearch.getSearchSetting().getEnzymes(), hasItems(enzyme));
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
	public void readChildMsISearch() {
		final EntityManager msiEm = getConnector().createEntityManager();

		try {

			List<Long> msiSIds = ResultSetRepository.findChildMsiSearchIdsForResultSet(msiEm, 3L);
			assertNotNull(msiSIds);
			assertEquals(msiSIds.size(), 2);

			msiSIds = ResultSetRepository.findChildMsiSearchIdsForResultSet(msiEm, 4L); //test with 2 levels hierarchy
			assertNotNull(msiSIds);
			assertEquals(msiSIds.size(), 2);
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

		final EntityManager msiEm = getConnector().createEntityManager();

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
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(2L));
			MsiSearch msiSearch = msiEm.find(MsiSearch.class, Long.valueOf(2L));
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
	public void checkReadablePtmString() {
		final EntityManager msiEm = getConnector().createEntityManager();

		EntityTransaction transac = null;
		boolean transacOK = false;

		try {
			ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(2L));
			assertNotNull(rs);

			List<PeptideMatch> pms = PeptideMatchRepository.findPeptideMatchByResultSet(msiEm, rs.getId());
			assertTrue("Loaded PeptideMatches from ResultSet #2", (pms != null) && !pms.isEmpty());

			PeptideMatch firstPm = pms.get(0);

			Peptide firstPeptide = msiEm.find(Peptide.class, Long.valueOf(firstPm.getPeptideId()));
			assertNotNull("First Peptide of ResultSet #2", firstPeptide);

			/* Save a new PeptideReadablePtmString */
			transac = msiEm.getTransaction();
			transac.begin();
			transacOK = false;

			PeptideReadablePtmStringPK prpsPK = new PeptideReadablePtmStringPK();
			prpsPK.setPeptideId(firstPeptide.getId());
			prpsPK.setResultSetId(rs.getId());

			PeptideReadablePtmString prps = new PeptideReadablePtmString();
			prps.setId(prpsPK);
			prps.setReadablePtmString("Toto");
			prps.setPeptide(firstPeptide);
			prps.setResultSet(rs);

			msiEm.persist(prps);

			transac.commit();
			transacOK = true;

			PeptideReadablePtmString loadedPtmString = PeptideReadablePtmStringRepository
				.findReadablePtmStrForPeptideAndResultSet(msiEm, firstPeptide.getId(), rs.getId());
			assertNotNull("Re-loaded PeptideReadablePtmString", loadedPtmString);
			assertEquals("Readable PTM string", "Toto", loadedPtmString.getReadablePtmString());
		} finally {

			if ((transac != null) && !transacOK) {
				LOG.warn("Rollbacking MSI DB EntityTransaction");

				try {
					transac.rollback();
				} catch (Exception ex) {
					LOG.error("Error rollbacking MSI Db EntityTransaction", ex);
				}

			}

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
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			ResultSet rs = msiEm.find(ResultSet.class, Long.valueOf(3L));
			assertNotNull(rs);
			assertNull(rs.getMsiSearch());
			assertFalse(rs.getChildren().isEmpty());
			assertEquals(rs.getChildren().size(), 2);
			ResultSet rs1 = msiEm.find(ResultSet.class, Long.valueOf(1L));
			ResultSet rs2 = msiEm.find(ResultSet.class, Long.valueOf(2L));
			MatcherAssert.assertThat(rs.getChildren(), hasItems(rs1, rs2));
			MatcherAssert.assertThat(rs.getChildren(), not(hasItems(rs)));
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
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			MsmsSearch msmsSearch = msiEm.find(MsmsSearch.class, Long.valueOf(1L));
			assertNotNull(msmsSearch);
			Enzyme enzyme = msiEm.find(Enzyme.class, Long.valueOf(1L));
			MatcherAssert.assertThat(msmsSearch.getEnzymes(), hasItems(enzyme));
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
		final EntityManager msiEm = getConnector().createEntityManager();

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
		final EntityManager msiEm = getConnector().createEntityManager();

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
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			PeptideMatch match = msiEm.find(PeptideMatch.class, Long.valueOf(1L));
			Peptide peptide = msiEm.find(Peptide.class, Long.valueOf(match.getPeptideId()));
			assertNotNull(peptide);
			assertEquals(peptide.getSequence(), "VLQAELK");

			List<PeptideMatch> matches = PeptideMatchRepository.findPeptideMatchByPeptide(msiEm,
				match.getPeptideId());
			MatcherAssert.assertThat(matches, hasItems(match));
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
