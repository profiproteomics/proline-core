package fr.proline.core.orm;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;

import javax.persistence.EntityManager;

import org.junit.Ignore;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.JDBCWork;
import fr.proline.repository.util.JPAUtils;
import fr.proline.repository.util.PostgresUtils;
import fr.proline.repository.util.DatabaseTestCase;

/**
 * Manual test (must use a PosgreSQL server configured in "pg_ps.properties" file).
 * 
 * @author LMN
 * 
 */
@Ignore
public class TestPostgresUtils extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(TestPostgresUtils.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.PS;
    }

    @Override
    public String getPropertiesFileName() {
	return "pg_ps.properties";
    }

    public void testPostgresqlConnection() {
	final IDatabaseConnector connector = getConnector();

	Connection con = null;

	EntityManager em = null;

	try {
	    con = connector.getDataSource().getConnection();

	    assertNotNull("SQL connection", con);

	    final CopyManager copyMgr = PostgresUtils.getCopyManager(con);

	    assertNotNull("PG CopyManager", copyMgr);

	    em = connector.getEntityManagerFactory().createEntityManager();

	    final JDBCWork copyManagerWork = new CopyManagerWork();

	    JPAUtils.doWork(em, copyManagerWork);
	} catch (SQLException sqlEx) {
	    LOG.error("Error retrieving PostgreSQL Connection", sqlEx);

	    fail(sqlEx.getMessage());
	} finally {

	    if (em != null) {
		try {
		    em.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing EntityManager", exClose);
		}
	    }

	    if (con != null) {
		try {
		    con.close();
		} catch (SQLException exClose) {
		    LOG.error("Error closing SQL JDBC connection", exClose);
		}
	    }

	    connector.close();
	}

    }

    public static void main(final String[] args) {
	final TestPostgresUtils test = new TestPostgresUtils();

	try {
	    test.testPostgresqlConnection();
	} finally {
	    test.tearDown();
	}

    }

    private static class CopyManagerWork implements JDBCWork {

	public void execute(final Connection paramConnection) throws SQLException {
	    assertNotNull("EntityManager wrapped SQL connection", paramConnection);

	    final CopyManager copyMgr = PostgresUtils.getCopyManager(paramConnection);

	    assertNotNull("PG CopyManager", copyMgr);
	}

    }

}
