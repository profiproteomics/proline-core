package fr.proline.repository.util;

import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.dbunit.ext.postgresql.PostgresqlDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.profi.util.StringUtils;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;

/**
 * Implements IDatabaseConnector to add a new feature allowing dbUnit handling (IDatabaseTester for dbUnit).
 * 
 * @author CB205360
 * @author LMN
 * 
 */
public class DatabaseTestConnector implements IDatabaseConnector {

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestConnector.class);

	private final Object m_closeLock = new Object();

	/* m_closed, onTearDown() and close() are @GuardedBy("m_closeLock") */

	private final IDatabaseConnector m_realConnector;

	private final IDatabaseTester m_databaseTester;

	private boolean m_closed;

	public DatabaseTestConnector(final ProlineDatabaseType database, final Map<Object, Object> properties) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		m_realConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(database, properties);
		
		m_databaseTester = this.createDatabaseTester(m_realConnector.getDataSource());
	}

	public DatabaseTestConnector(final ProlineDatabaseType database, final String propertiesFileName) {

		if (database == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (StringUtils.isEmpty(propertiesFileName)) {
			throw new IllegalArgumentException("Invalid propertiesFileName");
		}

		m_realConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
			database,
			propertiesFileName
		);

		m_databaseTester = this.createDatabaseTester(m_realConnector.getDataSource());
	}
	
	private IDatabaseTester createDatabaseTester( DataSource dataSource ) {
		
		final DriverType driverType = this.getDriverType();
		
		// Return a DataSourceDatabaseTester configured with the appropriate IDataTypeFactory
		return new DataSourceDatabaseTester(dataSource) {
			@Override
			public IDatabaseConnection getConnection() throws Exception {
				IDatabaseConnection dbC = super.getConnection();
				
				// Retrieve the IDataTypeFactory corresponding to the DriverType
				IDataTypeFactory dataTypeFactory = null;
				switch (driverType) {
				case H2:
					dataTypeFactory = new H2DataTypeFactory();
					
					// ***** WORKAROUND FOR DBUNIT Referential integrity constraint violation ERROR ***** //
					Statement smt = dbC.getConnection().createStatement();
					smt.execute("SET REFERENTIAL_INTEGRITY FALSE");
					smt.close();
          // ***** WORKAROUND FOR DBUNIT Referential integrity constraint violation ERROR ***** //
					
					break;
				case POSTGRESQL:
					dataTypeFactory = new PostgresqlDataTypeFactory();
					break;
				case SQLITE:
					dataTypeFactory = new DefaultDataTypeFactory() {
						@SuppressWarnings("rawtypes")
						@Override
						public Collection getValidDbProducts() {
							return Arrays.asList(new String[]{"SQLite"});
						}
					};
					break;
				}
				
				dbC.getConfig().setProperty(
					DatabaseConfig.PROPERTY_DATATYPE_FACTORY, dataTypeFactory
				);
				
				return dbC;
			}
		};
	}

	public ProlineDatabaseType getProlineDatabaseType() {
		return m_realConnector.getProlineDatabaseType();
	}

	public DriverType getDriverType() {
		return m_realConnector.getDriverType();
	}

	public boolean isMemory() {
		return m_realConnector.isMemory();
	}

	@Override
	public void setAdditionalProperties(Map<Object, Object> additionalProperties) {
		m_realConnector.setAdditionalProperties(additionalProperties);
	}

	public DataSource getDataSource() {
		return m_realConnector.getDataSource();
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return m_realConnector.getEntityManagerFactory();
	}

	public void close() {

		synchronized (m_closeLock) {

			if (!m_closed) { // Close only once
				m_closed = true;

				LOG.debug("Tearing down DataSourceDatabaseTester");

				try {
					m_databaseTester.onTearDown();
				} catch (Exception ex) {
					LOG.error("Error tearing down DataSourceDatabaseTester", ex);
				}

				m_realConnector.close();
			}

		} // End of synchronized block on m_closeLock

	}

	public boolean isClosed() {
		boolean result;

		synchronized (m_closeLock) {
			result = (m_closed || m_realConnector.isClosed());
		} // End of synchronized block on m_closeLock

		return result;
	}

	public IDatabaseTester getDatabaseTester() {
		return m_databaseTester;
	}

}
