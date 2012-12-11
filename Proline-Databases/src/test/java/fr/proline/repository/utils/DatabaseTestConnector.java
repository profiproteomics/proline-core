package fr.proline.repository.utils;

import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.Database;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.util.StringUtils;

/**
 * Implements IDatabaseConnector to add a new feature allowing dbUnit handling (IDatabaseTester for dbUnit).
 * 
 * @author CB205360
 * @author LMN
 * 
 */
public class DatabaseTestConnector implements IDatabaseConnector {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestConnector.class);

    private final Object m_connectorLock = new Object();

    private final IDatabaseConnector m_realConnector;

    private final IDatabaseTester m_databaseTester;

    public DatabaseTestConnector(final Database database, final Map<Object, Object> properties) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	m_realConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(database, properties);

	m_databaseTester = new DataSourceDatabaseTester(m_realConnector.getDataSource());
    }

    public DatabaseTestConnector(final Database database, final String propertiesFileName) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (StringUtils.isEmpty(propertiesFileName)) {
	    throw new IllegalArgumentException("Invalid propertiesFileName");
	}

	m_realConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(database,
		propertiesFileName);

	m_databaseTester = new DataSourceDatabaseTester(m_realConnector.getDataSource());
    }

    public Database getDatabase() {
	return m_realConnector.getDatabase();
    }

    public DriverType getDriverType() {
	return m_realConnector.getDriverType();
    }

    public boolean isMemory() {
	return m_realConnector.isMemory();
    }

    public DataSource getDataSource() {
	return m_realConnector.getDataSource();
    }

    public EntityManagerFactory getEntityManagerFactory() {
	return m_realConnector.getEntityManagerFactory();
    }

    public void close() {

	synchronized (m_connectorLock) {
	    LOG.debug("Tearing down DataSourceDatabaseTester");

	    try {
		final IDatabaseConnection con = m_databaseTester.getConnection();

		if (con != null) {
		    con.close();
		}

	    } catch (Exception exClose) {
		LOG.error("Error closing IDatabaseConnection", exClose);
	    }

	    try {
		m_databaseTester.onTearDown();
	    } catch (Exception ex) {
		LOG.error("Error tearing down DataSourceDatabaseTester", ex);
	    }

	    m_realConnector.close();
	} // End of synchronized block on m_connectorLock

    }

    public IDatabaseTester getDatabaseTester() {
	return m_databaseTester;
    }

}
