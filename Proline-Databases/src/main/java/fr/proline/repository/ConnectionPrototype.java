package fr.proline.repository;

import java.util.HashMap;
import java.util.Map;

import fr.proline.repository.ProlineRepository.DriverType;

public class ConnectionPrototype {

	public enum DatabaseProtocol { FILE, MEMORY, HOST };
	
	private Map<String, String> connectionProperties = new HashMap<String, String>();
	private DatabaseProtocol protocol;
	private String protocolValue;
	private String namePattern = "test"; 
	private String options;
	private DriverType driver;
	
	public ConnectionPrototype() {
		connectionProperties.put(DatabaseConnector.PROPERTY_PASSWORD, "");
		connectionProperties.put(DatabaseConnector.PROPERTY_USERNAME, "sa");
	}
	
	public ConnectionPrototype protocol(DatabaseProtocol protocol) {
		this.protocol = protocol;
		return this;
	}
	
	public ConnectionPrototype namePattern(String pattern) {
		this.namePattern = pattern;
		return this;
	}
	
	public ConnectionPrototype password(String passwd) {
		connectionProperties.put(DatabaseConnector.PROPERTY_PASSWORD, passwd);
		return this;
	}

	public ConnectionPrototype protocoleValue(String value) {
		this.protocolValue = value;
		return this;
	}

	public ConnectionPrototype username(String username) {
		connectionProperties.put(DatabaseConnector.PROPERTY_USERNAME, username);		
		return this;
	}

	public ConnectionPrototype driver(ProlineRepository.DriverType driver) {
		this.driver = driver;
		connectionProperties.put(DatabaseConnector.PROPERTY_DRIVERCLASSNAME, driver.driver);
		connectionProperties.put(DatabaseConnector.PROPERTY_DIALECT, driver.JPADialect);		
		return this;
	}
	
	public DatabaseConnector toConnector(ProlineRepository.Databases db) {
		StringBuilder URLbuilder = new StringBuilder();
		URLbuilder.append("jdbc:").append(driver.name().toLowerCase()).append(':');
		switch (protocol) {
		case MEMORY:
			URLbuilder.append("mem:");
			break;
		case FILE:
			URLbuilder.append("file:").append(protocolValue);
			break;
		case HOST:
			URLbuilder.append("//").append(protocolValue).append('/');
			break;
		default:
			break;
		}
		
		URLbuilder.append(namePattern).append('_').append(db.name().toLowerCase());

		if (options != null && !options.trim().isEmpty()) 
			URLbuilder.append(options);
		connectionProperties.put(DatabaseConnector.PROPERTY_URL, URLbuilder.toString());
		
		return new DatabaseConnector(connectionProperties);
	}
	
}
