package fr.proline.core.orm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.repository.MsiPeptideRepository;
import fr.proline.core.orm.ps.Peptide;
import fr.proline.core.orm.ps.repository.PsPeptideRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.util.StringUtils;

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
	LOG.info("Initializing Dbs");

	try {
	    /* Init MSI Db connection */
	    MSIDatabaseTestCase msiDBTestCase = new MSIDatabaseTestCase();
	    msiDBTestCase.initDatabase();

	    /* Init PS Db connection */
	    PSDatabaseTestCase psDBTestCase = new PSDatabaseTestCase();
	    psDBTestCase.initDatabase();

	    LOG.info("Dbs succesfully initialized");

	    /* Create Peptides data */
	    long start = System.nanoTime();
	    for (int i = 0; i < PEPTIDES_COUNT; ++i) {
		final Peptide psPeptide = new Peptide();
		psPeptide.setSequence(getSequence());
		psPeptide.setPtmString(getPtmString());
		psPeptide.setCalculatedMass(getMass());

		PEPTIDES.add(psPeptide);
	    }
	    long end = System.nanoTime();
	    LOG.info(String.format("%d PsPeptides created in %.1f ms", PEPTIDES_COUNT,
		    ((double) (end - start)) / MILLI_TO_NANOS));

	    EntityTransaction msiTransaction = null;
	    boolean msiTransacOk = false;

	    EntityTransaction psTransaction = null;
	    boolean psTransacOk = false;

	    try {
		EntityManager msiEm = msiDBTestCase.getEntityManager();
		msiTransaction = msiEm.getTransaction();
		msiTransaction.begin();
		msiTransacOk = false;

		EntityManager psEm = psDBTestCase.getEntityManager();
		psTransaction = psEm.getTransaction();
		psTransaction.begin();
		psTransacOk = false;

		/* Persist Peptides in Ps Db */
		start = System.nanoTime();
		for (int i = 0; i < PEPTIDES_COUNT; ++i) {
		    final Peptide psPeptide = PEPTIDES.get(i);

		    psEm.persist(psPeptide);
		}
		end = System.nanoTime();
		LOG.info(String.format("%d PsPeptides persisted in Ps Db in %.1f ms", PEPTIDES_COUNT,
			((double) (end - start)) / MILLI_TO_NANOS));

		/* Persist Peptides in Msi Db */
		start = System.nanoTime();
		for (int i = 0; i < PEPTIDES_COUNT; ++i) {
		    final fr.proline.core.orm.msi.Peptide msiPeptide = new fr.proline.core.orm.msi.Peptide(
			    PEPTIDES.get(i));

		    msiEm.persist(msiPeptide);
		}
		end = System.nanoTime();
		LOG.info(String.format("%d MsiPeptides persisted in Msi Db in %.1f ms", PEPTIDES_COUNT,
			((double) (end - start)) / MILLI_TO_NANOS));

		LOG.debug("Committing Transactions after persist...");

		msiTransaction.commit();
		msiTransacOk = true;

		psTransaction.commit();
		psTransacOk = true;

		LOG.debug("Starting new Transactions for retrieving");

		msiTransaction = msiEm.getTransaction();
		msiTransaction.begin();
		msiTransacOk = false;

		psTransaction = psEm.getTransaction();
		psTransaction.begin();
		psTransacOk = false;

		/* Retrieving in Msi Db by Id */
		int hit = 0;
		int miss = 0;
		int ptm = 0;

		start = System.nanoTime();
		for (int i = 0; i < PEPTIDES_COUNT; ++i) {
		    final Integer key = Integer.valueOf(RANDOM_GENERATOR.nextInt(PEPTIDES_COUNT) + 1);

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

		/* Retrieving in Ps Db by Id */
		hit = 0;
		miss = 0;
		ptm = 0;

		start = System.nanoTime();
		for (int i = 0; i < PEPTIDES_COUNT; ++i) {
		    final Integer key = Integer.valueOf(RANDOM_GENERATOR.nextInt(PEPTIDES_COUNT) + 1);

		    final Peptide psPeptide = psEm.find(Peptide.class, key);

		    if (psPeptide == null) {
			++miss;
		    } else {
			++hit;

			final String ptmStr = psPeptide.getPtmString();
			if (!StringUtils.isEmpty(ptmStr)) {
			    ++ptm;
			}

		    }

		}
		end = System.nanoTime();
		LOG.info(String.format(
			"Retrieving %d PsPeptides from Ps Db BY ID in %.1f ms  hit: %d  miss: %d  ptm: %d",
			PEPTIDES_COUNT, ((double) (end - start)) / MILLI_TO_NANOS, hit, miss, ptm));

		/* Retrieving in Msi Db by (sequence, ptmString) */
		MsiPeptideRepository msiPeptideRepo = new MsiPeptideRepository(msiEm);

		hit = 0;
		miss = 0;

		start = System.nanoTime();
		for (int i = 0; i < SEQUENCE_LOOKUP_COUNT; ++i) {
		    final String[] sequenceAndPtmString = getSequenceAndPtmString();

		    fr.proline.core.orm.msi.Peptide msiPeptide = null;

		    try {
			msiPeptide = msiPeptideRepo.findPeptideForSequenceAndPtmStr(sequenceAndPtmString[0],
				sequenceAndPtmString[1]);
		    } catch (Exception ex) {
			LOG.error(String.format("Error retrieving MsiPeptide [%s] %s",
				sequenceAndPtmString[0], (sequenceAndPtmString[1] == null) ? "NULL"
					: '[' + sequenceAndPtmString[1] + ']'), ex);
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

		/* Retrieving in Ps Db by (sequence, ptmString) */
		PsPeptideRepository psPeptideRepo = new PsPeptideRepository(psEm);

		hit = 0;
		miss = 0;

		start = System.nanoTime();
		for (int i = 0; i < SEQUENCE_LOOKUP_COUNT; ++i) {
		    final String[] sequenceAndPtmString = getSequenceAndPtmString();

		    Peptide psPeptide = null;

		    try {
			psPeptide = psPeptideRepo.findPeptideForSequenceAndPtmStr(sequenceAndPtmString[0],
				sequenceAndPtmString[1]);
		    } catch (Exception ex) {
			LOG.error(String.format("Error retrieving PsPeptide [%s] %s",
				sequenceAndPtmString[0], (sequenceAndPtmString[1] == null) ? "NULL"
					: '[' + sequenceAndPtmString[1] + ']'), ex);
		    }

		    if (psPeptide == null) {
			++miss;
		    } else {
			++hit;
		    }

		}
		end = System.nanoTime();
		LOG.info(String
			.format("Retrieving %d PsPeptides from Ps Db BY SEQUENCE AND PTM in %.1f ms  hit: %d  miss: %d",
				SEQUENCE_LOOKUP_COUNT, ((double) (end - start)) / MILLI_TO_NANOS, hit, miss));

		LOG.debug("Committing Transactions after retrieve operations...");

		psTransaction.commit();
		psTransacOk = true;

		msiTransaction.commit();
		msiTransacOk = true;
	    } finally {

		if ((psTransaction != null) && !psTransacOk) {
		    LOG.info("Rollbacking Ps Transaction");

		    try {
			psTransaction.rollback();
		    } catch (Exception ex) {
			LOG.error("Error rollbacking Ps Transaction", ex);
		    }

		}

		if ((msiTransaction != null) && !msiTransacOk) {
		    LOG.info("Rollbacking Msi Transaction");

		    try {
			msiTransaction.rollback();
		    } catch (Exception ex) {
			LOG.error("Error rollbacking Msi Transaction", ex);
		    }

		}

	    }

	    psDBTestCase.tearDown();
	    msiDBTestCase.tearDown();

	    LOG.info("Dbs succesfully closed");
	} catch (Exception ex) {
	    LOG.error("General error testing Msi / Ps Peptides", ex);
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
	    final Peptide psPeptide = PEPTIDES.get(RANDOM_GENERATOR.nextInt(PEPTIDES_COUNT));

	    sequence = psPeptide.getSequence();
	    ptmString = psPeptide.getPtmString();
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
    public Database getDatabase() {
	return Database.MSI;
    }

    @Override
    public String getPropertiesFileName() {
	return "db_msi.properties";
    }

}

class PSDatabaseTestCase extends DatabaseTestCase {

    @Override
    public Database getDatabase() {
	return Database.PS;
    }

    @Override
    public String getPropertiesFileName() {
	return "db_ps.properties";
    }

}
