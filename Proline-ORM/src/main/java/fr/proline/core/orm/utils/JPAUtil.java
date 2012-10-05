package fr.proline.core.orm.utils;

import javax.persistence.EntityManager;

import fr.proline.repository.ProlineRepository;

public final class JPAUtil {

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
