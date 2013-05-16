package fr.proline.core.orm;

import static org.junit.Assert.*;

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

    private static final long MAX_OFFSET = 60 * 60 * 1000L; // 1 hour

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.PS;
    }

    @Override
    public String getPropertiesFileName() {
	return "pg_ps.properties";
    }

    public void testPostgresqlTimeout() {
	final IDatabaseConnector connector = getConnector();

	boolean goOn = true;

	while (goOn) {

	    EntityManager em = null;

	    try {
		em = connector.getEntityManagerFactory().createEntityManager();

		Query countQuery = em
			.createQuery("select count (pep) from fr.proline.core.orm.ps.Peptide pep");

		final Object obj = countQuery.getSingleResult();

		if (obj instanceof Long) {
		    LOG.info("There are {} peptides in PS Db", (Long) obj);
		}

	    } catch (Exception ex) {
		LOG.error("Error running PostgreSQL quuery", ex);

		fail(ex.getMessage());
	    } finally {

		if (em != null) {
		    try {
			em.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing EntityManager", exClose);
		    }
		}

	    }

	    final long timeout = BASE_TIMEOUT + (long) (Math.random() * MAX_OFFSET);
	    LOG.info("Waiting {} ms ...", timeout);

	    try {
		Thread.sleep(timeout);
	    } catch (InterruptedException intEx) {
		LOG.warn("Thread.sleep() interrupted", intEx);

		goOn = false;
	    }

	}

	connector.close();
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
