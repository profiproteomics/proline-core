package fr.proline.repository;

import static org.junit.Assert.*;

import java.sql.DatabaseMetaData;

import org.junit.Test;

import fr.proline.repository.ConnectionPrototype.DatabaseProtocol;
import fr.proline.repository.ProlineRepository.Databases;
import fr.proline.repository.ProlineRepository.DriverType;

public class ConnectionPrototypeTest {

	@Test
	public void testH2ToConnector() throws Exception {
		ConnectionPrototype proto = new ConnectionPrototype();
		proto.driver(DriverType.H2).protocol(DatabaseProtocol.MEMORY).username("sa").password("").namePattern("test");
		DatabaseConnector connector = proto.toConnector(Databases.UDS);
		DatabaseMetaData metaData = connector.getConnection().getMetaData();
		assertEquals("jdbc:h2:mem:test_uds", metaData.getURL());
		assertEquals("sa", metaData.getUserName().toLowerCase());
	}

	@Test
	public void testPGToConnector() throws Exception {
		ConnectionPrototype proto = new ConnectionPrototype();
		proto.driver(DriverType.POSTGRESQL).protocol(DatabaseProtocol.HOST).username("bruley").password("toto").protocoleValue("localhost").namePattern("dbTest");
		DatabaseConnector connector = proto.toConnector(Databases.UDS);
		assertNotNull(connector);
	}

}
