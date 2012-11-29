package fr.proline.repository.utils;

import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.AbstractDatabaseConnector;
import fr.proline.repository.Database;
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

    private final IDatabaseConnector m_realConnector;

    private final IDatabaseTester m_databaseTester;

    public DatabaseTestConnector(final Database database, final Map<Object, Object> properties) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	m_realConnector = AbstractDatabaseConnector.createDatabaseConnectorInstance(database, properties);

	m_databaseTester = new DataSourceDatabaseTester(m_realConnector.getDataSource());
    }

    public DatabaseTestConnector(final Database database, final String propertiesFileName) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (StringUtils.isEmpty(propertiesFileName)) {
	    throw new IllegalArgumentException("Invalid propertiesFileName");
	}

	m_realConnector = AbstractDatabaseConnector.createDatabaseConnectorInstance(database,
		propertiesFileName);

	m_databaseTester = new DataSourceDatabaseTester(m_realConnector.getDataSource());
    }

    public Database getDatabase() {
	return m_realConnector.getDatabase();
    }

    public DataSource getDataSource() {
	return m_realConnector.getDataSource();
    }

    public EntityManagerFactory getEntityManagerFactory() {
	return m_realConnector.getEntityManagerFactory();
    }

    public void close() {
	LOG.debug("Closing DataSourceDatabaseTester test connection");

	try {
	    m_databaseTester.getConnection().close();
	} catch (Exception exClose) {
	    LOG.debug("Error closing DataSourceDatabaseTester test connection", exClose);
	}

	m_realConnector.close();
    }

    public IDatabaseTester getDatabaseTester() {
	return m_databaseTester;
    }

}
