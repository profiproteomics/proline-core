package fr.proline.core.orm.utils;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.Instrument;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class JPAUtilTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(JPAUtilTest.class);

    @Before
    public void setUp() throws Exception {
	initDatabase();
	initEntityManager(JPAUtil.PersistenceUnitNames.UDS_Key.getPersistenceUnitName());
    }

    @After
    public void tearDown() throws Exception {
	super.tearDown();
    }

    @Override
    public String getSQLScriptLocation() {
	return DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION;
    }

    @Test
    public void testUtils() {
	final EntityManager udsEm = getEntityManager();
	udsEm.setFlushMode(FlushModeType.COMMIT);

	EntityTransaction transac = null;
	boolean transacOk = false;

	try {
	    transac = udsEm.getTransaction();
	    transac.begin();
	    transacOk = false;

	    final Instrument inst1 = new Instrument();
	    inst1.setName("Instrument 1");
	    inst1.setSource("Src 1");

	    udsEm.persist(inst1);

	    final Instrument inst2 = new Instrument();
	    inst2.setName("Instrument 2");
	    inst2.setSource("Src 2");

	    udsEm.persist(inst2);

	    udsEm.flush(); // Flush to see persisted entities from JDBC context

	    final JDBCReturningWork<Long> jdbcWork = new JDBCReturningWork<Long>() {

		@Override
		public Long execute(final Connection connection) throws SQLException {
		    Long result = null;

		    final Statement stm = connection.createStatement();

		    if (stm.execute("select count(*) from instrument")) {
			final ResultSet rs = stm.getResultSet();

			if (rs.next()) {
			    result = Long.valueOf(rs.getLong(1));
			}

			rs.close();
		    }

		    stm.close();

		    return result;
		}

	    };

	    Long result = JPAUtil.doReturningWork(udsEm, jdbcWork);

	    assertEquals("2 instruments persisted", Long.valueOf(2), result);

	    LOG.info("Found instruments : {}", result);

	    transac.commit();
	    transacOk = true;
	} finally {

	    if ((transac != null) && !transacOk) {
		LOG.info("Rollbacking Uds Db transaction...");

		try {
		    transac.rollback();
		} catch (Exception ex) {
		    LOG.error("Error rollbacking Uds Db transaction", ex);
		}

	    }

	}

    }

}
