package fr.proline.repository.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JPAUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JPAUtils.class);

    /* Private constructor (Utility class) */
    private JPAUtils() {
    }

    @Deprecated
    public static void enableStatistics(final EntityManagerFactory emf, final String description) {
	LOG.error("org.hibernate.jmx.StatisticsService is no more implemented in Hibernate 4.3.x use \"hibernate.jmx.enabled\" property instead");
    }

    /**
     * Checks if an <code>EntityManager</code> is valid : not <code>null</code> and in <em>open</em> state.
     * 
     * @param em
     *            EntityManager to check
     */
    public static void checkEntityManager(final EntityManager em) {

	if ((em == null) || !em.isOpen()) {
	    throw new IllegalArgumentException("Invalid EntityManager");
	}

    }

    /**
     * Executes an SQL JDBC work on given <code>EntityManager</code> instance.
     * 
     * @param em
     *            Current JPA <code>EntityManager</code>, must not be <code>null</code>.
     *            <p>
     *            Notes:
     *            <ul>
     *            <li>If the SQL work has to write (<em>insert</em>, <em>update</em>) to database, the
     *            <code>EntityManager</code> must have a valid <code>EntityTransaction</code> started.</li>
     *            <li>If the JPA <code>EntityManager</code> already has pending <em>persisted</em> or
     *            <em>updated</em> entities, it must be <strong>explicitely flushed</strong> by client code
     *            before executing JDBC work.</li>
     *            </ul>
     * @param work
     *            JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     *            <code>EntityTransaction</code>.
     */
    public static void doWork(final EntityManager em, final JDBCWork work) {

	checkEntityManager(em);

	if (work == null) {
	    throw new IllegalArgumentException("Work is null");
	}

	/* Session interface is specific to Hibernate ORM */
	final Session hibSession = em.unwrap(Session.class);

	hibSession.doWork(work);
    }

    /**
     * Executes an SQL JDBC work (returning a result) on given <code>EntityManager</code> instance.
     * 
     * @param <T>
     *            Generic type of the result of the SQL JDBC work.
     * @param em
     *            Current JPA <code>EntityManager</code>, must not be <code>null</code>.
     *            <p>
     *            Notes:
     *            <ul>
     *            <li>If the SQL work has to write (<em>insert</em>, <em>update</em>) to database, the
     *            <code>EntityManager</code> must have a valid <code>EntityTransaction</code> started.</li>
     *            <li>If the JPA <code>EntityManager</code> already has pending <em>persisted</em> or
     *            <em>updated</em> entities, it must be <strong>explicitely flushed</strong> by client code
     *            before executing JDBC work.</li>
     *            </ul>
     * @param returningWork
     *            JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     *            <code>EntityTransaction</code>.
     * @return Result of the executed JDBC task.
     */
    public static <T> T doReturningWork(final EntityManager em, final JDBCReturningWork<T> returningWork) {

	checkEntityManager(em);

	if (returningWork == null) {
	    throw new IllegalArgumentException("ReturningWork is null");
	}

	/* Session interface is specific to Hibernate ORM */
	final Session hibSession = em.unwrap(Session.class);

	return hibSession.doReturningWork(returningWork);
    }

}
