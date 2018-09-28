package fr.proline.core.orm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.msi.repository.MsiPeptideRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;
import fr.profi.util.StringUtils;

@Ignore
public class TestPeptidesPerfs {

	private static final Logger LOG = LoggerFactory.getLogger(TestPeptidesPerfs.class);

	private static final int PEPTIDES_COUNT = 65535;

	private static final int SEQUENCE_LOOKUP_COUNT = 1000;

	private static final List<Peptide> PEPTIDES = new ArrayList<Peptide>(PEPTIDES_COUNT);

	private static final Random RANDOM_GENERATOR = new Random(); // Single threaded static

	private static final int SEQUENCE_LENGTH = 256;

	private static final int PTM_STRING_LENGTH = 64; // Not including "PTM  +" prefix

	private static final double MASS_FACTOR = 1e6;

	private static final double NO_PTM_PROBA = 1.0 / 3; // 1.0 = NO PTM

	private static final double UNKNOWN_PEPTIDE_PROBA = 1.0 / 3;

	private static final int CHAR_RANGE = 'Z' - 'A' + 1;

	private static final long MILLI_TO_NANOS = 1000000L;

	/**
	 * @param args
	 */
	public static void main(final String[] args) {

		try {

			/* Init MSI Db connection */
			MSIDatabaseTestCase msiDBTestCase = new MSIDatabaseTestCase();
			msiDBTestCase.initDatabase();

			LOG.info("Db succesfully initialized");

			/* Create Peptides data */
			long start = System.nanoTime();
			for (int i = 0; i < PEPTIDES_COUNT; ++i) {
				final fr.proline.core.orm.msi.Peptide msiPeptide = new Peptide();
				msiPeptide.setSequence(getSequence());
				msiPeptide.setPtmString(getPtmString());
				msiPeptide.setCalculatedMass(getMass());
				PEPTIDES.add(msiPeptide);
			}
			long end = System.nanoTime();
			LOG.info(String.format("%d msiPeptides created in %.1f ms", PEPTIDES_COUNT,
				((double) (end - start)) / MILLI_TO_NANOS));

			final EntityManager msiEm = msiDBTestCase.getConnector().createEntityManager();
			EntityTransaction msiTransaction = null;
			boolean msiTransacOk = false;

			try {
				msiTransaction = msiEm.getTransaction();
				msiTransaction.begin();
				msiTransacOk = false;

				/* Persist Peptides in Msi Db */
				start = System.nanoTime();
				for (int i = 0; i < PEPTIDES_COUNT; ++i) {
					final fr.proline.core.orm.msi.Peptide msiPeptide = PEPTIDES.get(i);
					msiEm.persist(msiPeptide);
				}
				end = System.nanoTime();
				LOG.info(String.format("%d MsiPeptides persisted in Msi Db in %.1f ms", PEPTIDES_COUNT,
					((double) (end - start)) / MILLI_TO_NANOS));

				LOG.debug("Committing Transactions after persist...");

				msiTransaction.commit();
				msiTransacOk = true;

				LOG.debug("Starting new Transactions for retrieving");

				msiTransaction = msiEm.getTransaction();
				msiTransaction.begin();
				msiTransacOk = false;

				/* Retrieving in Msi Db by Id */
				int hit = 0;
				int miss = 0;
				int ptm = 0;

				start = System.nanoTime();
				for (int i = 0; i < PEPTIDES_COUNT; ++i) {
					final Long key = Long.valueOf(RANDOM_GENERATOR.nextInt(PEPTIDES_COUNT) + 1);

					final fr.proline.core.orm.msi.Peptide msiPeptide = msiEm.find(
						fr.proline.core.orm.msi.Peptide.class, key);

					if (msiPeptide == null) {
						++miss;
					} else {
						++hit;

						final String ptmStr = msiPeptide.getPtmString();
						if (!StringUtils.isEmpty(ptmStr)) {
							++ptm;
						}

					}

				}
				end = System.nanoTime();
				LOG.info(String.format(
					"Retrieving %d MsiPeptides from Msi Db BY ID in %.1f ms  hit: %d  miss: %d  ptm: %d",
					PEPTIDES_COUNT, ((double) (end - start)) / MILLI_TO_NANOS, hit, miss, ptm));

				/* Retrieving in Msi Db by (sequence, ptmString) */

				hit = 0;
				miss = 0;

				start = System.nanoTime();
				for (int i = 0; i < SEQUENCE_LOOKUP_COUNT; ++i) {
					final String[] sequenceAndPtmString = getSequenceAndPtmString();

					fr.proline.core.orm.msi.Peptide msiPeptide = null;

					try {
						msiPeptide = MsiPeptideRepository.findPeptideForSequenceAndPtmStr(msiEm,
							sequenceAndPtmString[0], sequenceAndPtmString[1]);
					} catch (Exception ex) {
						LOG.error(String.format("Error retrieving MsiPeptide [%s] %s",
							sequenceAndPtmString[0], (sequenceAndPtmString[1] == null) ? "NULL"
								: '[' + sequenceAndPtmString[1] + ']'),
							ex);
					}

					if (msiPeptide == null) {
						++miss;
					} else {
						++hit;
					}

				}
				end = System.nanoTime();
				LOG.info(String
					.format("Retrieving %d MsiPeptide from Msi Db BY SEQUENCE AND PTM in %.1f ms  hit: %d  miss: %d",
						SEQUENCE_LOOKUP_COUNT, ((double) (end - start)) / MILLI_TO_NANOS, hit, miss));

			} finally {
				if ((msiTransaction != null) && !msiTransacOk) {
					LOG.info("Rollbacking Msi Transaction");

					try {
						msiTransaction.rollback();
					} catch (Exception ex) {
						LOG.error("Error rollbacking Msi Transaction", ex);
					}

				}

				if (msiEm != null) {
					try {
						msiEm.close();
					} catch (Exception exClose) {
						LOG.error("Error closing MSI EntityManager", exClose);
					}
				}

				msiDBTestCase.tearDown();
			}

			LOG.info("Dbs succesfully closed");
		} catch (Exception ex) {
			LOG.error("General error testing Msi Peptides", ex);
		}

	}

	private static String getSequence() {
		final int length = RANDOM_GENERATOR.nextInt(SEQUENCE_LENGTH) + 1;

		final StringBuilder buff = new StringBuilder(length);

		for (int i = 0; i < length; ++i) {
			buff.append(getChar());
		}

		return buff.toString();
	}

	private static String getPtmString() {
		String result = null;

		if (RANDOM_GENERATOR.nextDouble() > NO_PTM_PROBA) {
			final int length = RANDOM_GENERATOR.nextInt(PTM_STRING_LENGTH) + 1;

			final StringBuilder buff = new StringBuilder(length);
			buff.append("PTM  +");

			for (int i = 0; i < length; ++i) {
				buff.append(getChar());
			}

			result = buff.toString();
		}

		return result;
	}

	private static double getMass() {
		return (RANDOM_GENERATOR.nextDouble() * MASS_FACTOR);
	}

	private static String[] getSequenceAndPtmString() {
		String sequence = null;
		String ptmString = null;

		if (RANDOM_GENERATOR.nextDouble() > UNKNOWN_PEPTIDE_PROBA) {
			final fr.proline.core.orm.msi.Peptide msiPeptide = PEPTIDES.get(RANDOM_GENERATOR.nextInt(PEPTIDES_COUNT));

			sequence = msiPeptide.getSequence();
			ptmString = msiPeptide.getPtmString();
		} else {
			sequence = getSequence();
			ptmString = getPtmString();
		}

		return new String[] { sequence, ptmString };
	}

	private static char getChar() {
		return (char) (RANDOM_GENERATOR.nextInt(CHAR_RANGE) + 'A');
	}

}

class MSIDatabaseTestCase extends DatabaseTestCase {

	@Override
	public ProlineDatabaseType getProlineDatabaseType() {
		return ProlineDatabaseType.MSI;
	}

	@Override
	public String getPropertiesFileName() {
		return "db_msi.properties";
	}

}
