package fr.proline.repository.migration.msi;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V0_11__RSxValidationPropertiesType implements JdbcMigration  {

    private static final Logger LOG = LoggerFactory.getLogger(V0_11__RSxValidationPropertiesType.class);

    /**
     * Migrate MSI db in order to update result_summary properties to change labels "peptide" to "psm".
     *
     */
    @Override
    public void migrate(Connection msiConn) throws Exception {
        Statement statement = msiConn.createStatement();

        //PreparedStatement used to save resultSet properties
        PreparedStatement updateResultSummaryPropsStmt = msiConn.prepareStatement("UPDATE result_summary SET serialized_properties=? WHERE id=?");

        // --- Get RSM  ---
        ResultSet rsms = statement.executeQuery("SELECT result_summary.id, result_summary.serialized_properties from result_summary");
        while (rsms.next()){

            // * For each merged RSM : test if nb peptide == nb psm (Aggregation) or not (Union)
            Long rsmId = rsms.getLong(1);
            String prop = rsms.getString(2);

            prop = prop.replaceAll("\"peptide_", "\"psm_");

            // * Store properties
            updateResultSummaryPropsStmt.setString(1,prop);
            updateResultSummaryPropsStmt.setLong(2,rsmId);
            updateResultSummaryPropsStmt.executeUpdate();

        }

    }
}
