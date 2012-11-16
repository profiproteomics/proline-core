package fr.proline.core.orm.utils;

import java.lang.management.ManagementFactory;
import java.util.Hashtable;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.jmx.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ProlineRepository;

public final class JPAUtil {

    private static final Logger LOG = LoggerFactory.getLogger(JPAUtil.class);

    /* Private constructor (Utility class) */
    private JPAUtil() {
    }

    public enum PersistenceUnitNames {

	UDS_Key("udsdb_production"), PS_Key("psdb_production"), MSI_Key("msidb_production"), PDI_Key(
		"pdidb_production");
	String pu_name;

	PersistenceUnitNames(String name) {
	    pu_name = name;
	}

	public String getPersistenceUnitName() {
	    return pu_name;
	}

	public static String getPersistenceUnitNameForDB(ProlineRepository.Databases db) {
	    switch (db) {
	    case LCMS:
		return null;
	    case MSI:
		return MSI_Key.getPersistenceUnitName();
	    case PDI:
		return PDI_Key.getPersistenceUnitName();
	    case PS:
		return PS_Key.getPersistenceUnitName();
	    case UDS:
		return UDS_Key.getPersistenceUnitName();
	    default:
		return null;
	    }
	}
    };

    public static void enableStatistics(final EntityManagerFactory emf, final String description) {

	if (!(emf instanceof HibernateEntityManagerFactory)) {
	    throw new IllegalArgumentException("Invalid emf");
	}

	if (StringUtils.isEmpty(description)) {
	    throw new IllegalArgumentException("Invalid description");
	}

	final HibernateEntityManagerFactory hibEMF = (HibernateEntityManagerFactory) emf;

	final SessionFactory hibSF = hibEMF.getSessionFactory();
	if (hibSF != null) {

	    try {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

		final StatisticsService stats = new StatisticsService();
		stats.setSessionFactory(hibSF);
		stats.setStatisticsEnabled(true); // Must be enabled after SessionFactory association

		final Hashtable<String, String> table = new Hashtable<String, String>();
		table.put("type", "statistics");
		table.put("sessionFactory", description);

		final ObjectName objName = new ObjectName(JPAUtil.class.getPackage().getName(), table);

		server.registerMBean(stats, objName);

		LOG.info("New Hibernate statistics MBean {} registered", stats);
	    } catch (Exception ex) {
		LOG.error("Error registering Hibernate StatisticsService instance", ex);
	    }

	} // End if (hibSF is not null)

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
     * @param work
     *            JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     *            <code>EntityTransaction</code>.
     * @return Result of the executed JDBC task.
     */
    public static <T> T doReturningWork(final EntityManager em, final JDBCReturningWork<T> work) {

	checkEntityManager(em);

	if (work == null) {
	    throw new IllegalArgumentException("Work is null");
	}

	final Session hibSession = em.unwrap(Session.class);

	return hibSession.doReturningWork(work);
    }

}
