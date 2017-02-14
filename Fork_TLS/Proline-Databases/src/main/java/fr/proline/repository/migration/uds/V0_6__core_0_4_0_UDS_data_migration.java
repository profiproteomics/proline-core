package fr.proline.repository.migration.uds;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

public class V0_6__core_0_4_0_UDS_data_migration implements JdbcMigration {

	/**
	 * Migrate UDS db in order to:
	 * - set the value of mzDB file name and directory from serialized properties
	 */
	@Override
	public void migrate(Connection udsConn) throws Exception {

		Statement statement = udsConn.createStatement();

		PreparedStatement updateRawFileStmt = udsConn.prepareStatement(
			"UPDATE raw_file SET mzdb_file_name = ?, mzdb_file_directory = ? WHERE identifier = ?");

		try {

			ResultSet rs = statement.executeQuery("SELECT identifier, serialized_properties FROM raw_file");

			while (rs.next()) {

				// Retrieve record values
				String identifier = rs.getString("identifier");
				String properties = rs.getString("serialized_properties");

				if (properties != null && !properties.isEmpty()) {
					// Parse the mzDB file path
					String[] stringsBetweenQuotes = properties.split("\"");
					String mzdbPath = stringsBetweenQuotes[3];
					File mzdbFileLocation = new java.io.File(mzdbPath);

					// Update raw_file record
					updateRawFileStmt.setString(1, mzdbFileLocation.getName());
					updateRawFileStmt.setString(2, mzdbFileLocation.getParent());
					updateRawFileStmt.setString(3, identifier);
					updateRawFileStmt.execute();
				}
			}

		} finally {
			statement.close();
			updateRawFileStmt.close();
		}

	}

}