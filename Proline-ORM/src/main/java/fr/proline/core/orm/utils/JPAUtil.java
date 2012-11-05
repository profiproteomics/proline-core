package fr.proline.core.orm.utils;

import java.lang.management.ManagementFactory;
import java.util.Hashtable;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

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

	if (emf == null) {
	    throw new IllegalArgumentException("Emf is null");
	}

	if (StringUtils.isEmpty(description)) {
	    throw new IllegalArgumentException("Invalid description");
	}

	if (emf instanceof HibernateEntityManagerFactory) {
	    final HibernateEntityManagerFactory hemf = (HibernateEntityManagerFactory) emf;

	    final SessionFactory hsf = hemf.getSessionFactory();
	    if (hsf != null) {

		try {
		    final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

		    final StatisticsService stats = new StatisticsService();
		    stats.setSessionFactory(hsf);
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

	    } // End if (hsf is not null)

	} else {
	    LOG.warn("{} is NOT an instance of HibernateEntityManagerFactory", emf);
	}

    }

    /**
     * Check if an <code>EntityManager</code> is valid : not <code>null</code> and in <em>open</em> state.
     * 
     * @param em
     *            EntityManager to check
     */
    public static void checkEntityManager(final EntityManager em) {

	if ((em == null) || !em.isOpen()) {
	    throw new IllegalArgumentException("Invalid EntityManager");
	}

    }

}
