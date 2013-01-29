package fr.proline.repository;

import fr.proline.util.StringUtils;

public enum ProlineDatabaseType {

    UDS("udsdb_production"), PDI("pdidb_production"), PS("psdb_production"), MSI("msidb_production"), LCMS(
	    "lcmsdb_production");

    private final String m_puName;

    ProlineDatabaseType(final String puName) {
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

    public String getPersistenceUnitName() {
	return m_puName;
    }

}
