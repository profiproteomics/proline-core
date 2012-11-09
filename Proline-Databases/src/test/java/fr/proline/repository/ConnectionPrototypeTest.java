package fr.proline.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.sql.DatabaseMetaData;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ConnectionPrototype.DatabaseProtocol;
import fr.proline.repository.ProlineRepository.Databases;
import fr.proline.repository.ProlineRepository.DriverType;

public class ConnectionPrototypeTest {
	
	public final Logger logger = LoggerFactory.getLogger(this.getClass());

	String dbPropertiesFile = "/connection_proto_db.properties" ;

	@Test
	public void testH2ToConnector() throws Exception {
		ConnectionPrototype proto = new ConnectionPrototype();
		proto.driver(DriverType.H2).protocol(DatabaseProtocol.MEMORY).username("sa").password("").namePrefix("test_");
		DatabaseConnector connector = proto.toConnector(Databases.UDS);
		DatabaseMetaData metaData = connector.getConnection().getMetaData();
		assertEquals("jdbc:h2:mem:test_uds", metaData.getURL());
		assertEquals("sa", metaData.getUserName().toLowerCase());
	}
	
	@Test
	public void testH2NullPwdToConnector() throws Exception {
		ConnectionPrototype proto = new ConnectionPrototype();
		proto.driver(DriverType.H2).protocol(DatabaseProtocol.MEMORY).username("sa").namePrefix("test_");
		proto.password(null);
		DatabaseConnector connector = proto.toConnector(Databases.UDS);
		DatabaseMetaData metaData = connector.getConnection().getMetaData();
		assertEquals("jdbc:h2:mem:test_uds", metaData.getURL());
		assertEquals("sa", metaData.getUserName().toLowerCase());
	}
	
	@Test
	public void testPGToConnector() throws Exception {
		ConnectionPrototype proto = new ConnectionPrototype();
		proto.driver(DriverType.POSTGRESQL).protocol(DatabaseProtocol.HOST).username("bruley").password("toto").protocoleValue("localhost").namePrefix("dbTest_");
		DatabaseConnector connector = proto.toConnector(Databases.UDS);
		assertNotNull(connector);
	}
	
	@Test
	public void testFilePrototypeConnector() throws Exception {
		ConnectionPrototype proto = new ConnectionPrototype(dbPropertiesFile);
		proto.namePrefix("proline_test_");
		DatabaseConnector connector = proto.toConnector(Databases.UDS);
		assertNotNull(connector);
		assertEquals("jdbc:sqlite:./target/proline_test_uds", connector.getConnection().getMetaData().getURL());
    }

	@Test(expected=IOException.class)
	public void testInvalidFilePrototypeConnector() throws Exception {
		 new ConnectionPrototype("Mytest"+dbPropertiesFile);
	}
}
