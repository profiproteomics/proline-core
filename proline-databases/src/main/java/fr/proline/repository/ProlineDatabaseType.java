package fr.proline.repository;

import fr.profi.util.StringUtils;

public enum ProlineDatabaseType {

    UDS("udsdb_production"),
    MSI("msidb_production"),
    LCMS("lcmsdb_production"),
    SEQ("seqdb_production");

	private final String m_puName;

	private ProlineDatabaseType(final String puName) {
		assert (!StringUtils.isEmpty(puName)) : "ProlineDatabaseType() invalid puName";

		m_puName = puName;
	}

	public static ProlineDatabaseType withPersistenceUnitName(final String puName) {

		for (final ProlineDatabaseType pdt : ProlineDatabaseType.values()) {

			if (pdt.getPersistenceUnitName().equals(puName)) {
				return pdt;
			}

		}

		throw new IllegalArgumentException("Unknown puName");
	}

	/**
	 * Retrieves the persistence-unit name used by ORM <code>EntityManagerFactory</code> mapping files.
	 * 
	 * @return the persistence-unit name
	 */
	public String getPersistenceUnitName() {
		return m_puName;
	}

}
