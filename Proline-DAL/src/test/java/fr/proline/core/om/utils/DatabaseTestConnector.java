package fr.proline.core.om.utils;

import java.util.Map;

import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;

import fr.proline.repository.DatabaseConnector;

/**
 * Inherits from DatabaseConnector to add a new feature allowing dbUnit handling (IDatabaseTester for dbUnit)
 * 
 * @author CB205360
 *
 */
public class DatabaseTestConnector extends DatabaseConnector {


	private IDatabaseTester databaseTester;
	
	public DatabaseTestConnector(Map<String, String> properties) {
		super(properties);
	}
	
	public DatabaseTestConnector(String filename) {
		super(filename);
	}

	public IDatabaseTester getDatabaseTester() {
		if (databaseTester == null) {
			try {
				databaseTester = new JdbcDatabaseTester(getProperty(PROPERTY_DRIVERCLASSNAME),
						getProperty(PROPERTY_URL), getProperty(PROPERTY_USERNAME),
						getProperty(PROPERTY_PASSWORD));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		return databaseTester;
	}

	@Override
	public void closeAll() throws Exception {
		super.closeAll();
		try {
			if (databaseTester != null) {
				databaseTester.getConnection().close();
				databaseTester = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
