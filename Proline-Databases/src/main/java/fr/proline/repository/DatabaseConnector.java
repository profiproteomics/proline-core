package fr.proline.repository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import fr.proline.repository.ProlineRepository.DriverType;

/**
 * A DatabaseConnector handle connection to a relational database and can returns this connection as
 * different type of object : AbstractDataSource for Flyway or a Map of settings for 
 * JPA entityManager. A DataConnector can be created from a Map or from a properties file that must contains properties
 * defined by the PROPERTY_XXX keys.
 * 
 * @author CB205360
 *
 */
public class DatabaseConnector {

	public static final String PROPERTY_URL = "database.url";
	public static final String PROPERTY_USERNAME = "database.userName";
	public static final String PROPERTY_PASSWORD = "database.password";
	public static final String PROPERTY_DRIVERCLASSNAME = "database.driverClassName";
	public static final String PROPERTY_DIALECT = "database.hibernate.dialect";

	private Properties properties;
	private SimpleDriverDataSource dataSource;
	private Connection connection;
	private DriverType driverType;
	private static final Logger logger = LoggerFactory.getLogger(DatabaseConnector.class);
	
	public DatabaseConnector(Map<String, String> properties) throws Exception {
		this.properties = new Properties();
		this.properties.putAll(properties);
		updateDriverType();
	}

	/**
	 * Create a DatabaseConnector using a file from the class path 
	 *  
	 * @param filename : file to read from classpath
	 */
	public DatabaseConnector(String filename) throws Exception {
	  this.initDatabaseConnector( DatabaseConnector.class.getResource(filename) );
	}
	
  public DatabaseConnector(URL fileURL) throws Exception {
    this.initDatabaseConnector(fileURL);
  }
  
  private void initDatabaseConnector(URL fileURL) throws Exception {
    try {
      logger.debug("Create DatabaseConnector using {} ",fileURL);
      this.properties = readProperties(fileURL);
      this.updateDriverType();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

	private void updateDriverType() throws Exception {
		String driverClassName = getProperty(PROPERTY_DRIVERCLASSNAME);
		for(DriverType type : DriverType.values()) {
			if (type.driver.equals(driverClassName)) {
				driverType = type;
				
				// Set default Hibernate Dialect
				if( properties.containsKey(PROPERTY_DIALECT) == false ) {
				  properties.setProperty(PROPERTY_DIALECT, driverType.getJPADriver());
				}
				
				break;
			}
		}
		
		if( this.driverType == null ) {
		  throw new Exception("unsupported database driver: " + driverClassName );
		}
	}

	public DriverType getDriverType() {
		return driverType;
	}
	
	public Map<String, Object> getEntityManagerSettings() {
		final Map<String, Object> entityManagerSettings = new HashMap<String, Object>();
		entityManagerSettings.put("javax.persistence.jdbc.url", getProperty(PROPERTY_URL));
		entityManagerSettings.put("javax.persistence.jdbc.user", getProperty(PROPERTY_USERNAME));
		entityManagerSettings.put("javax.persistence.jdbc.password", getProperty(PROPERTY_PASSWORD));
		entityManagerSettings.put("javax.persistence.jdbc.driver", getProperty(PROPERTY_DRIVERCLASSNAME));
		entityManagerSettings.put("hibernate.dialect", getProperty(PROPERTY_DIALECT));
		return entityManagerSettings;
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}
	
	public AbstractDataSource getDataSource() {
		if (dataSource == null) {
			try {
				dataSource = new SimpleDriverDataSource();
				dataSource.setDriverClass(Class.forName(getProperty(PROPERTY_DRIVERCLASSNAME)));
				dataSource.setUrl(getProperty(PROPERTY_URL));
				dataSource.setUsername(getProperty(PROPERTY_USERNAME));
				dataSource.setPassword(getProperty(PROPERTY_PASSWORD));
			} catch (ClassNotFoundException cnfe) {
				cnfe.printStackTrace();
			}
		}
		return dataSource;
	}
	
  private Properties readProperties(URL fileURL) throws IOException {
    InputStream is = fileURL.openStream();
    Properties props = new Properties();
    props.load(is);
    return props;
  }

	public void closeAll() throws Exception {
			if (dataSource != null) {
				dataSource.getConnection().close();
			}
			if (connection != null) {
				connection.close();
			}
	}

	public boolean hasConnection()  {
	  return connection != null;
	}
	
	public Connection getConnection() throws Exception {
		if (connection == null) {
			Class driverClass = Class.forName(getProperty(PROPERTY_DRIVERCLASSNAME));
			connection = DriverManager.getConnection(getProperty(PROPERTY_URL),
					getProperty(PROPERTY_USERNAME), getProperty(PROPERTY_PASSWORD));
		}
		return connection;
	}
}
