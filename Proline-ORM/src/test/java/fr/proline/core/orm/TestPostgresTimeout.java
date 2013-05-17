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
import fr.proline.repository.utils.DatabaseTestCase;

/**
 * Manual test (must use a PosgreSQL server configured in "pg_ps.properties" file).
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
	return ProlineDatabaseType.PS;
    }

    @Override
    public String getPropertiesFileName() {
	return "pg_ps.properties";
    }

    public void testPostgresqlTimeout() {
	final ExecutorService executor = Executors.newCachedThreadPool();

	final IDatabaseConnector psDbConnector = getConnector();

	boolean goOn = true;

	while (goOn) {

	    for (int i = 0; i < N_CONCURRENT_THREADS; ++i) {
		final Runnable queryTask = new QueryRunner(psDbConnector);

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

	psDbConnector.close();

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

    private final IDatabaseConnector m_psDbConnector;

    public QueryRunner(final IDatabaseConnector psDbConnector) {

	if (psDbConnector == null) {
	    throw new IllegalArgumentException("PsDbConnector is null");
	}

	m_psDbConnector = psDbConnector;
    }

    public void run() {
	EntityManager psEm = null;

	try {
	    psEm = m_psDbConnector.getEntityManagerFactory().createEntityManager();

	    final Query countQuery = psEm
		    .createQuery("select count (pep) from fr.proline.core.orm.ps.Peptide pep");

	    final Object obj = countQuery.getSingleResult();

	    if (obj instanceof Long) {
		LOG.info("There are {} peptides in PS Db", (Long) obj);
	    }

	    Thread.sleep(QUERY_PAUSE); // Sleep 1 minute keeping psEm open ...
	} catch (Exception ex) {
	    LOG.error("Error running PostgreSQL query on PS Db", ex);

	    fail(ex.getMessage());
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

}
