package fr.proline.repository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.util.PropertiesUtils;
import fr.proline.util.StringUtils;

public class ConnectionPrototype {

    public enum DatabaseProtocol {
	FILE, MEMORY, HOST
    };

    public enum DriverType {
	H2, POSTGRESQL, SQLITE
    };

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPrototype.class);

    private Map<Object, Object> m_connectionProperties = new HashMap<Object, Object>();
    private DatabaseProtocol m_protocol;
    private DriverType m_driverType;
    private String m_protocolValue;
    private String m_namePrefix = "test_";
    private String m_options;

    public ConnectionPrototype() {
	m_connectionProperties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_USER_KEY, "sa");
	m_connectionProperties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY, "");
    }

    public ConnectionPrototype(final Map<Object, Object> connProps) {
	m_connectionProperties = connProps;
	updateFields();
    }

    /**
     * Look for following entries in properties file "database.userName" "database.password"
     * "database.protocol" (Allowed values : FILE, MEMORY, HOST ) "database.drivertype" (Allowed values : H2,
     * POSTGRESQL, SQLITE ) "database.protocolValue" if necessary (Host or File path to DB).
     * 
     * @param filename
     * @throws IOException
     */
    public ConnectionPrototype(final String propertiesFileName) {
	final Properties props = PropertiesUtils.loadProperties(propertiesFileName);

	if (props == null) {
	    throw new IllegalArgumentException("Invalid properties file");
	}

	m_connectionProperties.putAll(props);

	updateFields();
    }

    public void setDriverType(final DriverType driverType) {
	m_driverType = driverType;
    }

    public DriverType getDriverType() {
	return m_driverType;
    }

    public void setProtocol(final DatabaseProtocol protocol) {
	m_protocol = protocol;
    }

    public DatabaseProtocol getProtocol() {
	return m_protocol;
    }

    public void setProtocolValue(final String value) {
	m_protocolValue = value;
    }

    public String getProtocolValue() {
	return m_protocolValue;
    }

    public void setNamePrefix(final String namePrefix) {
	m_namePrefix = namePrefix;
    }

    public String getNamePrefix() {
	return m_namePrefix;
    }

    public void setUserName(final String userName) {

	if (userName == null) {
	    throw new IllegalArgumentException("User Name should not be null");
	}

	m_connectionProperties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_USER_KEY, userName);
    }

    public void setPassword(final String passwd) {

	if (passwd == null) {
	    m_connectionProperties.remove(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY);
	} else {
	    m_connectionProperties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY, passwd);
	}

    }

    protected void updateFields() {
	setDriverType(DriverType.valueOf(PropertiesUtils.getProperty(m_connectionProperties,
		"database.drivertype")));

	setProtocol(DatabaseProtocol.valueOf(PropertiesUtils.getProperty(m_connectionProperties,
		"database.protocol")));

	setProtocolValue(PropertiesUtils.getProperty(m_connectionProperties, "database.protocolValue"));
    }

    public IDatabaseConnector toConnector(final Database db) {
	final StringBuilder urlBuilder = new StringBuilder();

	final DriverType driverType = getDriverType();

	urlBuilder.append(AbstractDatabaseConnector.JDBC_SCHEME).append(':');
	urlBuilder.append(driverType.name().toLowerCase()).append(':');

	final String protocolValue = getProtocolValue();

	switch (getProtocol()) {
	case MEMORY:
	    if (driverType == DriverType.SQLITE) {
		urlBuilder.append("memory:");
	    } else {
		urlBuilder.append("mem:");
	    }

	    break;

	case FILE:
	    if (driverType != DriverType.SQLITE) {
		urlBuilder.append("file:");
	    }

	    if (!StringUtils.isEmpty(protocolValue)) {
		urlBuilder.append(protocolValue);

		/* Append a last '/' if not already existing */
		if (!StringUtils.isTerminated(urlBuilder, '/')) {
		    urlBuilder.append('/');
		}

	    }

	    break;

	case HOST:
	    urlBuilder.append("//");

	    if (!StringUtils.isEmpty(protocolValue)) {
		urlBuilder.append(protocolValue);

		/* Append a last '/' if not already existing */
		if (!StringUtils.isTerminated(urlBuilder, '/')) {
		    urlBuilder.append('/');
		}

	    }

	    break;

	default:

	}

	final String namePrefix = getNamePrefix();

	if (!StringUtils.isEmpty(namePrefix)) {
	    urlBuilder.append(namePrefix);
	}

	urlBuilder.append(db.name().toLowerCase());

	if (!StringUtils.isEmpty(m_options)) {
	    urlBuilder.append(m_options);
	}

	LOG.debug("Database URL [{}]", urlBuilder);

	m_connectionProperties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY, urlBuilder.toString());

	return AbstractDatabaseConnector.createDatabaseConnectorInstance(db, m_connectionProperties);
    }

}
