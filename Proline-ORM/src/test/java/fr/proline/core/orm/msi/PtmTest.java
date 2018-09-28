package fr.proline.core.orm.msi;

import static org.junit.Assert.*;

import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.MsiPtmRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class PtmTest extends DatabaseTestCase {

	private static final Logger LOG = LoggerFactory.getLogger(PtmTest.class);

	@Override
	public ProlineDatabaseType getProlineDatabaseType() {
		return ProlineDatabaseType.MSI;
	}

	@Before
	public void setUp() throws Exception {
		initDatabase();
		String[] datasets = { "/dbunit/Init/msi-db.xml", "/dbunit/datasets/msi/Peptides_Dataset.xml" };
		loadCompositeDataSet(datasets);
	}

	@Override
	public String getPropertiesFileName() {
		return "db_msi.properties";
	}

	@Test
	public void readPtm() {
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			TypedQuery<Ptm> query = msiEm.createQuery(
				"Select ptm from Ptm ptm where ptm.unimodId = :unimod_id", Ptm.class);
			query.setParameter("unimod_id", Long.valueOf(21L));
			Ptm ptm = query.getSingleResult();
			assertEquals(ptm.getFullName(), "Phosphorylation");
			Set<PtmEvidence> evidences = ptm.getEvidences();
			assertEquals(evidences.size(), 5);

			Set<PtmSpecificity> specificities = ptm.getSpecificities();
			assertEquals(specificities.size(), 8);
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
	public void findPtmByName() {
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			Ptm phosPtm = MsiPtmRepository.findPtmForName(msiEm, "Phospho");
			assertNotNull(phosPtm);
			assertEquals(phosPtm.getShortName(), "Phospho");
			assertEquals(phosPtm.getFullName(), "Phosphorylation");
			Ptm phosPtm2 = MsiPtmRepository.findPtmForName(msiEm, "PHosPHo");
			assertNotNull(phosPtm2);
			assertSame(phosPtm2, phosPtm);
			Ptm phosPtm3 = MsiPtmRepository.findPtmForName(msiEm, "PHosPHorylation");
			assertNotNull(phosPtm3);
			assertSame(phosPtm3, phosPtm);
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
	public void findPtmClassification() {
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			final PtmClassification classification = MsiPtmRepository.findPtmClassificationForName(msiEm,
				"Chemical derivative");

			assertNotNull("Chemical derivative PtmClassification", classification);
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
