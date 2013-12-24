package fr.proline.core.orm;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.Instrument;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.JDBCReturningWork;
import fr.proline.repository.util.JPAUtils;
import fr.proline.repository.util.DatabaseTestCase;

public class JPAUtilsTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(JPAUtilsTest.class);

    private static final AtomicBoolean STATISTICS_ENABLED = new AtomicBoolean(false);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.UDS;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();
    }

    @Test
    public void testUtils() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	if (!STATISTICS_ENABLED.getAndSet(true)) {
	    /* Check Hibernate statistics only once */
	    JPAUtils.enableStatistics(emf, "Tests-HibernateStats");
	}

	final EntityManager udsEm = emf.createEntityManager();
	EntityTransaction transac = null;
	boolean transacOk = false;

	try {
	    udsEm.setFlushMode(FlushModeType.COMMIT);

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

		    try {

			if (stm.execute("select count(*) from instrument")) {
			    final ResultSet rs = stm.getResultSet();

			    while ((result == null) && rs.next()) {
				final Object obj = rs.getObject(1); // 1st column

				if (obj instanceof Long) {
				    result = (Long) obj;
				}

			    }

			    rs.close();
			}

		    } finally {

			try {
			    stm.close();
			} catch (SQLException exClose) {
			    LOG.error("Error closing count statement", exClose);
			}

		    }

		    return result;
		}

	    };

	    final Long result = JPAUtils.doReturningWork(udsEm, jdbcWork);

	    assertTrue("At least 2 instruments persisted", (result != null) && (result.longValue() >= 2L));

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

	    if (udsEm != null) {
		try {
		    udsEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing UDS EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
