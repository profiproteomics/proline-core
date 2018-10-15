package fr.proline.repository.migration.msi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V0_10__RSxMergeType implements JdbcMigration  {

    private static final Logger LOG = LoggerFactory.getLogger(V0_10__RSxMergeType.class);

    /**
     * Migrate MSI db in order to update result_set or result_summary properties to indicate which kind of merge was done (Aggregation or Union).
     *
     *  Only properties of
     *  - merge RS will be updated according to merge type
     *  - merge RSM will be updated according to merge type
     *
     */
    @Override
    public void migrate(Connection msiConn) throws Exception {
        Statement statement = msiConn.createStatement();

        //PreparedStatement used to get nbr Peptide for RS
        PreparedStatement countPepStmnt = msiConn.prepareStatement("select count(distinct peptide_match.peptide_id) from peptide_match where peptide_match.result_set_id = ? ");
        //PreparedStatement used to get nbr Peptide Matches
        PreparedStatement countPepMatchStmnt = msiConn.prepareStatement("select count(id) from peptide_match where peptide_match.result_set_id = ? ");

        //PreparedStatement used to get nbr Peptide for RSM
        PreparedStatement countPepInstanceStmnt = msiConn.prepareStatement("select count(id) from peptide_instance where result_summary_id = ? ");
        //PreparedStatement used to get nbr Peptide Matches
        PreparedStatement countPepInstPepMatchStmnt = msiConn.prepareStatement("select sum(peptide_match_count) from peptide_instance where result_summary_id = ? ");

        //PreparedStatement used to save resultSet properties
        PreparedStatement updateResultSetPropsStmt = msiConn.prepareStatement("UPDATE result_set SET serialized_properties=? WHERE id=?");

        //PreparedStatement used to save resultSet properties
        PreparedStatement updateResultSummaryPropsStmt = msiConn.prepareStatement("UPDATE result_summary SET serialized_properties=? WHERE id=?");

        // Create a JSON parser
        JsonParser jsonParser = new JsonParser();

        // --- Get RS Merge : User ResulSet without MergedRSMId ---
        ResultSet rs = statement.executeQuery("SELECT id, serialized_properties from result_set where type like '%USER' and merged_rsm_id is null");
        while (rs.next()){

            // * For each merged RS : test if nb peptide == nb psm (Aggregation) or not (Union)
            Long rsId = rs.getLong(1);
            String prop = rs.getString(2);

            countPepStmnt.setLong(1,rsId);
            ResultSet countPepRs = countPepStmnt.executeQuery();
            countPepMatchStmnt.setLong(1,rsId);
            ResultSet countPepMatchRs = countPepMatchStmnt.executeQuery();
            if(countPepRs.next() && countPepMatchRs.next()) {

                // Parse serialized properties
                JsonObject propsAsJson;
                try {
                    propsAsJson = jsonParser.parse(prop).getAsJsonObject();
                } catch ( Exception e)  {
                    LOG.info("Error while trying to parse to 'result set' properties. May be null, assume empty");
                    propsAsJson = jsonParser.parse("{}").getAsJsonObject();
                }

                int pepCount =countPepRs.getInt(1);
                int pepMatchCount =countPepMatchRs.getInt(1);
                if(pepCount == pepMatchCount) {
                    //SAVE AGGREGATION
                    propsAsJson.addProperty("merge_mode", "aggregation");
                } else {
                    //SAVE UNION
                    propsAsJson.addProperty("merge_mode", "union");
                }

                // * save properties
                updateResultSetPropsStmt.setString(1,propsAsJson.toString());
                updateResultSetPropsStmt.setLong(2,rsId);
                updateResultSetPropsStmt.executeUpdate();
            } else {
                LOG.warn("Unable to get peptide count for RS "+rsId);
            }
        }

        // --- Get RSM Merge : User ResulSet with MergedRSMId => RSM Merged ID... ---
        ResultSet rsms = statement.executeQuery("SELECT result_summary.id, result_summary.serialized_properties from result_set inner join result_summary on result_set.merged_rsm_id = result_summary.id");
        while (rsms.next()){
            // * For each merged RSM : test if nb peptide == nb psm (Aggregation) or not (Union)
            Long rsmId = rsms.getLong(1);
            String prop = rsms.getString(2);

            countPepInstanceStmnt.setLong(1,rsmId);
            ResultSet countPepRs = countPepInstanceStmnt.executeQuery();
            countPepInstPepMatchStmnt.setLong(1,rsmId);
            ResultSet countPepMatchRs = countPepInstPepMatchStmnt.executeQuery();
            if(countPepRs.next() && countPepMatchRs.next()) {

                // Parse serialized properties
                JsonObject propsAsJson;
                try {
                    propsAsJson = jsonParser.parse(prop).getAsJsonObject();
                } catch ( Exception e)  {
                    LOG.error("Error while trying to parse to 'result set' properties");
                    propsAsJson = jsonParser.parse("{}").getAsJsonObject();
                }

                int pepCount =countPepRs.getInt(1);
                int pepMatchCount =countPepMatchRs.getInt(1);
                if(pepCount == pepMatchCount) {
                    //SAVE AGGREGATION
                    propsAsJson.addProperty("merge_mode", "aggregation");
                } else {
                    //SAVE UNION
                    propsAsJson.addProperty("merge_mode", "union");
                }

                // * Store properties
                updateResultSummaryPropsStmt.setString(1,propsAsJson.toString());
                updateResultSummaryPropsStmt.setLong(2,rsmId);
                updateResultSummaryPropsStmt.executeUpdate();

            } else {
                LOG.warn("Unable to get peptide count for RSM "+rsmId);
            }
        }

    }
}
