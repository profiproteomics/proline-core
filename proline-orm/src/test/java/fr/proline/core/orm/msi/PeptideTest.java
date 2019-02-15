package fr.proline.core.orm.msi;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.MsiPeptideRepository;
import fr.proline.core.orm.util.JPARepositoryUtils;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class PeptideTest extends DatabaseTestCase {

	private static final Logger LOG = LoggerFactory.getLogger(PeptideTest.class);

	private static final String SEQ_TO_FOUND = "LTGMAFR";

	private static final int PEPTIDE_COUNT = 10;

	private static final int BIG_PEPTIDE_COUNT = 2534;

	@Override
	public ProlineDatabaseType getProlineDatabaseType() {
		return ProlineDatabaseType.MSI;
	}

	@Before
	public void setUp() throws Exception {
		initDatabase();

		String[] datasets = new String[] { "/dbunit/Init/msi-db.xml", "/dbunit/datasets/msi/Peptides_Dataset.xml"
		};

		loadCompositeDataSet(datasets);
	}

	@Override
	public String getPropertiesFileName() {
		return "db_msi.properties";
	}

	@Test
	public void readPeptidesBySeq() {
		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			List<Peptide> peps = MsiPeptideRepository.findPeptidesForSequence(msiEm, SEQ_TO_FOUND);

			assertNotNull(peps);
			assertEquals(2, peps.size());

			boolean foundPepWOPtm = false;
			boolean foundPepWithPtm = false;

			for (final Peptide pep : peps) {
				final Set<PeptidePtm> ptms = pep.getPtms();

				if ((ptms == null) || ptms.isEmpty()) {
					foundPepWOPtm = true;
				} else {
					foundPepWithPtm = true;
				}

			}

			assertTrue(foundPepWithPtm);
			assertTrue(foundPepWOPtm);
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
	public void retrievePeptideForIds() {
		retrievePeptideForIds(PEPTIDE_COUNT);

		retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE - 1);

		retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE);

		retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE + 1);

		retrievePeptideForIds(JPARepositoryUtils.MAX_BATCH_SIZE + 2);

		retrievePeptideForIds(BIG_PEPTIDE_COUNT);
	}

	private void retrievePeptideForIds(final long nIds) {
		assert (nIds > 0) : "retrievePeptideForIds() invalid nIds";

		final EntityManager msiEm = getConnector().createEntityManager();

		try {
			int retrievedPeptides = 0;

			/* Build a 0..nIds Set */
			final List<Long> ids = new ArrayList<Long>((int) nIds);
			for (long i = 0; i < nIds; ++i) {
				ids.add(Long.valueOf(i));
			}

			final List<Peptide> peptides = MsiPeptideRepository.findPeptidesForIds(msiEm, ids);

			if (peptides != null) {
				for (final Peptide p : peptides) {
					if (p != null) {
						++retrievedPeptides;
					}
				}
			}

			assertTrue("Retrieved Msi Peptides count", retrievedPeptides > 0);
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
