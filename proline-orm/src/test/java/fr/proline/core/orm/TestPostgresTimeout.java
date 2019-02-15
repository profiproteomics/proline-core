package fr.proline.core.orm;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

/**
 * Manual test (must use a PosgreSQL server configured in "pg_msi.properties" file).
 * 
 * @author LMN
 * 
 */
@Ignore
public class TestPostgresTimeout extends DatabaseTestCase {

	private static final Logger LOG = LoggerFactory.getLogger(TestPostgresTimeout.class);

	private static final long BASE_TIMEOUT = 3 * 60 * 60 * 1000L; // 3 hour

	private static final long MAX_TIMEOUT_OFFSET = 60 * 60 * 1000L; // 1 hour

	private static final int N_CONCURRENT_THREADS = 20;

	@Override
	public ProlineDatabaseType getProlineDatabaseType() {
		return ProlineDatabaseType.MSI;
	}

	@Override
	public String getPropertiesFileName() {
		return "pg_msi.properties";
	}

	public void testPostgresqlTimeout() {
		final ExecutorService executor = Executors.newCachedThreadPool();

		final IDatabaseConnector msiDbConnector = getConnector();

		boolean goOn = true;

		while (goOn) {

			for (int i = 0; i < N_CONCURRENT_THREADS; ++i) {
				final Runnable queryTask = new QueryRunner(msiDbConnector);

				executor.execute(queryTask);
			}

			final long timeout = BASE_TIMEOUT + (long) (Math.random() * MAX_TIMEOUT_OFFSET);
			LOG.info("Waiting {} ms ...", timeout);

			try {
				Thread.sleep(timeout);
			} catch (InterruptedException intEx) {
				LOG.warn("Thread.sleep() interrupted", intEx);

				goOn = false;
			}

		}

		msiDbConnector.close();

		executor.shutdown();
	}

	public static void main(final String[] args) {
		final TestPostgresTimeout test = new TestPostgresTimeout();

		try {
			test.testPostgresqlTimeout();
		} finally {
			test.tearDown();
		}

	}

}

class QueryRunner implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(QueryRunner.class);

	private static final long QUERY_PAUSE = 60 * 1000L; // 1 minute

	private final IDatabaseConnector m_msiDbConnector;

	public QueryRunner(final IDatabaseConnector msiDbConnector) {

		if (msiDbConnector == null) {
			throw new IllegalArgumentException("MsiDbConnector is null");
		}

		m_msiDbConnector = msiDbConnector;
	}

	public void run() {
		EntityManager msiEm = null;

		try {
			msiEm = m_msiDbConnector.createEntityManager();

			final Query countQuery = msiEm
				.createQuery("select count (pep) from fr.proline.core.orm.msi.Peptide pep");

			final Object obj = countQuery.getSingleResult();

			if (obj instanceof Long) {
				LOG.info("There are {} peptides in MSI Db", (Long) obj);
			}

			Thread.sleep(QUERY_PAUSE); // Sleep 1 minute keeping msiEm open ...
		} catch (Exception ex) {
			LOG.error("Error running PostgreSQL query on MSI Db", ex);

			fail(ex.getMessage());
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

}
