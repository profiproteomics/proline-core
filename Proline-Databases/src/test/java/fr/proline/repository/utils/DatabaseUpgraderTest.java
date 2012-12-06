package fr.proline.repository.utils;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import fr.proline.repository.AbstractDatabaseConnector;
import fr.proline.repository.Database;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DatabaseUpgrader;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;

public class DatabaseUpgraderTest {

    @Test
    public void test() throws SQLException {
	final Map<Object, Object> properties = new HashMap<Object, Object>();
	properties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_DRIVER_KEY,
		DriverType.SQLITE.getJdbcDriver());
	properties
		.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY, "jdbc:sqlite:./target/db_test.dat");

	final IDatabaseConnector sqliteConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		Database.UDS, properties);

	DatabaseUpgrader.upgradeDatabase(sqliteConnector);

	DatabaseUpgrader.upgradeDatabase(sqliteConnector);
    }

}
