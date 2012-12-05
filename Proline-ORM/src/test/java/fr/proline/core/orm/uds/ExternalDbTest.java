package fr.proline.core.orm.uds;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.Test;

import fr.proline.repository.ConnectionMode;
import fr.proline.repository.Database;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;

public class ExternalDbTest {

    @Test
    public void testH2Mem() throws Exception {
	final ExternalDb externalDb = new ExternalDb();
	externalDb.setType(Database.UDS);
	externalDb.setDriverType(DriverType.H2);
	externalDb.setConnectionMode(ConnectionMode.MEMORY);
	externalDb.setDbName("test_uds");
	externalDb.setDbUser("sa");
	externalDb.setDbPassword("");

	final IDatabaseConnector connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		externalDb.getType(), externalDb.toPropertiesMap());

	final DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    final DatabaseMetaData metaData = con.getMetaData();

	    assertEquals("jdbc:h2:mem:test_uds", metaData.getURL());
	    assertEquals("sa", metaData.getUserName().toLowerCase());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Test
    public void testH2File() throws Exception {
	final ExternalDb externalDb = new ExternalDb();
	externalDb.setType(Database.PDI);
	externalDb.setDriverType(DriverType.H2);
	externalDb.setConnectionMode(ConnectionMode.FILE);
	externalDb.setDbName("./target/h2_pdi");
	externalDb.setDbUser("sa");
	externalDb.setDbPassword("");

	final IDatabaseConnector connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		externalDb.getType(), externalDb.toPropertiesMap());

	final DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    final DatabaseMetaData metaData = con.getMetaData();

	    assertEquals("jdbc:h2:file:./target/h2_pdi", metaData.getURL());
	    assertEquals("sa", metaData.getUserName().toLowerCase());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Test
    public void testSQLiteMem() throws Exception {
	final ExternalDb externalDb = new ExternalDb();
	externalDb.setType(Database.MSI);
	externalDb.setDriverType(DriverType.SQLITE);
	externalDb.setConnectionMode(ConnectionMode.MEMORY);

	final IDatabaseConnector connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		externalDb.getType(), externalDb.toPropertiesMap());

	final DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    final DatabaseMetaData metaData = con.getMetaData();

	    assertEquals("jdbc:sqlite::memory:", metaData.getURL());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Test
    public void testSQLiteFile() throws Exception {
	final ExternalDb externalDb = new ExternalDb();
	externalDb.setType(Database.PS);
	externalDb.setDriverType(DriverType.SQLITE);
	externalDb.setConnectionMode(ConnectionMode.FILE);
	externalDb.setDbName("./target/pdi.dat");

	final IDatabaseConnector connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		externalDb.getType(), externalDb.toPropertiesMap());

	final DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    final DatabaseMetaData metaData = con.getMetaData();

	    assertEquals("jdbc:sqlite:./target/pdi.dat", metaData.getURL());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Test
    public void testPGHost() throws Exception {
	final ExternalDb externalDb = new ExternalDb();
	externalDb.setType(Database.UDS);
	externalDb.setDriverType(DriverType.POSTGRESQL);
	externalDb.setConnectionMode(ConnectionMode.HOST);
	externalDb.setHost("localhost");
	externalDb.setDbName("uds");
	externalDb.setDbUser("bruley");
	externalDb.setDbPassword("toto");

	final IDatabaseConnector connector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		externalDb.getType(), externalDb.toPropertiesMap());

	assertEquals("Postgresql DB Connector", DriverType.POSTGRESQL, connector.getDriverType());
    }

}
