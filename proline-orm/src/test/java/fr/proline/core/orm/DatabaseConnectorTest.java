package fr.proline.core.orm;

import static org.junit.Assert.*;

import java.sql.Connection;

import javax.persistence.EntityManager;
import javax.sql.DataSource;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.IDatabaseConnector;

public class DatabaseConnectorTest {

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnectorTest.class);

	@Test
	public void testH2() {
		final IDatabaseConnector h2Connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
			ProlineDatabaseType.MSI, "db_msi.properties");

		checkDatabaseConnector("H2 MSI mem", h2Connector);
	}

	@Test
	public void testSQLite() {
		final IDatabaseConnector sqliteConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
			ProlineDatabaseType.MSI, "sqlite_msi.properties");

		checkDatabaseConnector("SQLite MSI mem", sqliteConnector);
	}

	private static void checkDatabaseConnector(final String description, final IDatabaseConnector connector) {
		assertNotNull(description + " DatabaseConnector instance", connector);

		final DataSource ds = connector.getDataSource();

		final String dsMessage = description + " DataSource";
		assertNotNull(dsMessage, ds);

		LOG.info(dsMessage + " : " + ds);

		try {
			final Connection con = ds.getConnection();
			assertNotNull(description + " JDBC Connection", con);

			con.close();
		} catch (Exception ex) {
			final String message = description + " JDBC DataSource handling";
			LOG.error(message, ex);

			fail(message);
		}

		/*final EntityManagerFactory emf = connector.getEntityManagerFactory();
		
		final String emfMessage = description + " EntityManagerFactory";
		assertNotNull(emfMessage, emf);
		
		LOG.info(emfMessage + " : " + emf);*/

		final EntityManager em = connector.createEntityManager();
		assertNotNull(description + " EntityManager", em);

		em.close();

		connector.close();
	}

}
