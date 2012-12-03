package fr.proline.repository;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.Ignore;
import org.junit.Test;

import fr.proline.repository.ConnectionPrototype.DatabaseProtocol;
import fr.proline.repository.ConnectionPrototype.DriverType;

public class ConnectionPrototypeTest {

    private static final String DB_PROPERTIES_FILE = "connection_proto_db.properties";

    @Test
    public void testH2ToConnector() throws Exception {
	ConnectionPrototype proto = new ConnectionPrototype();
	proto.setDriverType(DriverType.H2);
	proto.setProtocol(DatabaseProtocol.MEMORY);
	proto.setNamePrefix("test_");
	proto.setUserName("sa");
	proto.setPassword("");

	IDatabaseConnector connector = proto.toConnector(Database.UDS);

	DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    DatabaseMetaData metaData = con.getMetaData();
	    assertEquals("jdbc:h2:mem:test_uds", metaData.getURL());
	    assertEquals("sa", metaData.getUserName().toLowerCase());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Ignore
    // LMN null password does not work (29 nov. 2012)
    public void testH2NullPwdToConnector() throws Exception {
	ConnectionPrototype proto = new ConnectionPrototype();
	proto.setDriverType(DriverType.H2);
	proto.setProtocol(DatabaseProtocol.MEMORY);
	proto.setNamePrefix("test_");
	proto.setUserName("sa");
	proto.setPassword(null);

	IDatabaseConnector connector = proto.toConnector(Database.UDS);

	DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    DatabaseMetaData metaData = con.getMetaData();
	    assertEquals("jdbc:h2:mem:test_uds", metaData.getURL());
	    assertEquals("sa", metaData.getUserName().toLowerCase());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Test
    public void testPGToConnector() throws Exception {
	ConnectionPrototype proto = new ConnectionPrototype();
	proto.setDriverType(DriverType.POSTGRESQL);
	proto.setProtocol(DatabaseProtocol.HOST);
	proto.setUserName("bruley");
	proto.setProtocolValue("localhost");
	proto.setNamePrefix("dbTest_");
	proto.setPassword("toto");

	IDatabaseConnector connector = proto.toConnector(Database.UDS);
	assertTrue("Postgresql DB Connector", connector instanceof PostgresDatabaseConnector);
    }

    @Test
    public void testFilePrototypeConnector() throws Exception {
	ConnectionPrototype proto = new ConnectionPrototype(DB_PROPERTIES_FILE);
	proto.setNamePrefix("proline_test_");

	IDatabaseConnector connector = proto.toConnector(Database.UDS);
	assertNotNull(connector);

	DataSource ds = connector.getDataSource();

	Connection con = ds.getConnection();

	try {
	    DatabaseMetaData metaData = con.getMetaData();
	    assertEquals("jdbc:sqlite:./target/proline_test_uds", metaData.getURL());
	} finally {

	    if (ds != null) {
		con.close();
	    }

	}

    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFilePrototypeConnector() throws Exception {
	new ConnectionPrototype("toto");
    }

}
