package fr.proline.repository.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Ignore;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.PostgresUtils;

/**
 * Manual test (must use a PosgreSQL server configured in "ps_postgresql.properties" file.
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
	return "ps_postgresql.properties";
    }

    public void testPostgresqlConnection() {
	final IDatabaseConnector connector = getConnector();

	try {
	    final Connection con = connector.getDataSource().getConnection();

	    assertNotNull("SQL connection", con);

	    final CopyManager copyMgr = PostgresUtils.getCopyManager(con);

	    assertNotNull("PG CopyManager", copyMgr);

	    LOG.info("CopyManager : " + copyMgr);
	} catch (SQLException sqlEx) {
	    LOG.error("Error retrieving PostgreSQL Connection", sqlEx);

	    fail(sqlEx.getMessage());
	} finally {
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

}
