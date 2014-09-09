package fr.proline.repository.migration.msi;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.googlecode.flyway.core.api.migration.jdbc.JdbcMigration;

public class V0_5__RS_Hierarchy_Update implements JdbcMigration {

	
	@Override
	public void migrate(Connection msiConn) throws Exception {

		// VDS test on uds ... 
//				PreparedStatement statement =
//					msiConn.prepareStatement("INSERT INTO activation (type) VALUES ('MyTest !')");
//	        try {
//	            statement.execute();
//	        } finally {
//	            statement.close();
//	        }
		

	}

}
