package fr.proline.repository;

import fr.proline.util.StringUtils;

public enum Database {

    UDS("udsdb_production"), PDI("pdidb_production"), PS("psdb_production"), MSI("msidb_production"), LCMS(
	    "lcmsdb_production");

    private final String m_puName;

    Database(final String puName) {
	assert (!StringUtils.isEmpty(puName)) : "Database() invalid puName";

	m_puName = puName;
    }

    public String getPersistenceUnitName() {
	return m_puName;
    }
    
    public static Database withPersistenceUnitName(String value) {
      for (Database re : Database.values()) {
        if (re.m_puName.compareTo(value) == 0) {
          return re;
        }
      }
      throw new IllegalArgumentException("Invalid Database value: " + value);
    }

}
