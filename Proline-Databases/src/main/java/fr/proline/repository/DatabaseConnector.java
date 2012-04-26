package fr.proline.repository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
	
	
	public DatabaseConnector(Map<String, String> properties) {
		this.properties = new Properties();
		this.properties.putAll(properties);
		updateDriverType();
	}

	public DatabaseConnector(String filename) {
		try {
			properties = readProperties(filename);
			updateDriverType();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void updateDriverType() {
		String driverClassName = getProperty(PROPERTY_DRIVERCLASSNAME);
		for(DriverType type : DriverType.values()) {
			if (type.driver.equals(driverClassName)) {
				driverType = type;
				break;
			}
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

	private Properties readProperties(String filename) throws IOException {
		URL propertiesURL = DatabaseConnector.class.getResource(filename);
		InputStream is = propertiesURL.openStream();
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

	public Connection getConnection() throws Exception {
		if (connection == null) {
			Class driverClass = Class.forName(getProperty(PROPERTY_DRIVERCLASSNAME));
			connection = DriverManager.getConnection(getProperty(PROPERTY_URL),
					getProperty(PROPERTY_USERNAME), getProperty(PROPERTY_PASSWORD));
		}
		return connection;
	}
}
