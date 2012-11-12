package fr.proline.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import fr.proline.repository.ProlineRepository.DriverType;

public class ConnectionPrototype {

	public enum DatabaseProtocol { FILE, MEMORY, HOST };
	
	private Map<String, String> connectionProperties = new HashMap<String, String>();
	private DatabaseProtocol protocol;
	private String protocolValue;
	private String namePrefix = "test_";
	private String options;
	private DriverType driver;
	
	public ConnectionPrototype() {
		connectionProperties.put(DatabaseConnector.PROPERTY_PASSWORD, "");
		connectionProperties.put(DatabaseConnector.PROPERTY_USERNAME, "sa");
	}
	
    public ConnectionPrototype( HashMap<String, String> connProps ) {
      this.connectionProperties = connProps;
      this.updateFields();
    }
	
	/**
	 * Look for following entries in properties file
	 * "database.userName"
	 * "database.password"
	 * "database.protocol" (Allowed values : FILE, MEMORY, HOST )
	 * "database.drivertype" (Allowed values : H2, POSTGRESQL, SQLITE )
	 * "database.protocolValue" if necessary  (Host or File path to DB)
	 * 
	 * @param filename
	 * @throws IOException
	 */
	public ConnectionPrototype(String filename) throws IOException {
		Properties fileProperties = new Properties();
		InputStream is = ConnectionPrototype.class.getResourceAsStream(filename);
		if(is==null)
			throw new IOException("Invaid file specified "+filename);
		fileProperties.load(is);
		
		connectionProperties.put(DatabaseConnector.PROPERTY_PASSWORD, fileProperties.getProperty(DatabaseConnector.PROPERTY_PASSWORD, ""));
		connectionProperties.put(DatabaseConnector.PROPERTY_USERNAME, fileProperties.getProperty(DatabaseConnector.PROPERTY_USERNAME, "sa"));
		
		connectionProperties.put("database.protocol",fileProperties.getProperty("database.protocol"));
		connectionProperties.put("database.protocolValue",fileProperties.getProperty("database.protocolValue"));		
		connectionProperties.put("database.drivertype",fileProperties.getProperty("database.drivertype"));
		
		this.updateFields();
	}
	
	protected void updateFields() {
      this.protocol = DatabaseProtocol.valueOf(connectionProperties.get("database.protocol"));
      this.protocoleValue(connectionProperties.get("database.protocolValue"));
      this.driver( DriverType.valueOf(connectionProperties.get("database.drivertype")) );
      
      //if(fileProperties.getProperty("database.drivertype")!=null)
      //    this.driver(DriverType.valueOf(fileProperties.getProperty("database.drivertype")));
	}
	
    public DriverType getDriver() {
      return driver;
    }
    
    public DatabaseProtocol getProtocol() {
      return protocol;
    }

    public String getProtocolValue() {
      return protocolValue;
    }
	
	public ConnectionPrototype protocol(DatabaseProtocol protocol) {
		this.protocol = protocol;
		return this;
	}
	
	public ConnectionPrototype namePrefix(String prefix) {
		this.namePrefix = prefix;
		return this;
	}
	
	public ConnectionPrototype password(String passwd) {
		if(passwd == null)
			connectionProperties.remove(DatabaseConnector.PROPERTY_PASSWORD);
		else
			connectionProperties.put(DatabaseConnector.PROPERTY_PASSWORD, passwd);
		return this;
	}

	public ConnectionPrototype protocoleValue(String value) {
		this.protocolValue = value;
		return this;
	}

	public ConnectionPrototype username(String username) {
		if(username==null)
			throw new NullPointerException("User Name should not be null");
		connectionProperties.put(DatabaseConnector.PROPERTY_USERNAME, username);		
		return this;
	}

	public ConnectionPrototype driver(ProlineRepository.DriverType driver) {
		this.driver = driver;
		
		connectionProperties.put(DatabaseConnector.PROPERTY_DRIVERCLASSNAME, driver.driver);
		
		// Set default Hibernate Dialect
		if( connectionProperties.containsKey(DatabaseConnector.PROPERTY_DIALECT) == false )
		  connectionProperties.put(DatabaseConnector.PROPERTY_DIALECT, driver.JPADialect);
		
		return this;
	}
	
	public DatabaseConnector toConnector( ProlineRepository.Databases db ) throws Exception {
	  return this.toConnector(db.toString().toLowerCase());
	}
	
	public DatabaseConnector toConnector( String dbName ) throws Exception {
	    
		StringBuilder URLbuilder = new StringBuilder();
		URLbuilder.append("jdbc:").append(driver.name().toLowerCase()).append(':');		
		
		switch (protocol) {
		case MEMORY:
			if(driver!=DriverType.SQLITE)
				URLbuilder.append("mem:");
			else 
				URLbuilder.append(":memory:");
			break;
		case FILE:
			if(driver!=DriverType.SQLITE)
				URLbuilder.append("file:").append(protocolValue);
			else
				URLbuilder.append(protocolValue);
			
			// Append a last '/' if not already existing
			if( URLbuilder.toString().endsWith("/") == false )
			  URLbuilder.append('/');
		
			break;
		case HOST:
			URLbuilder.append("//").append(protocolValue).append('/');
			break;
		default:
			break;
		}
		
		URLbuilder.append(namePrefix).append(dbName);
		
		if (options != null && !options.trim().isEmpty()) 
			URLbuilder.append(options);
		
		connectionProperties.put(DatabaseConnector.PROPERTY_URL, URLbuilder.toString());
		
		return new DatabaseConnector(connectionProperties);
	}

}
