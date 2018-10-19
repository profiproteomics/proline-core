package fr.proline.repository.migration.uds

import java.io.ByteArrayInputStream
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util
import java.util.Properties

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.postgresql.PGConnection
import org.postgresql.copy.CopyManager
import fr.profi.jdbc.easy._
import fr.profi.util.StringUtils
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.algo.msq.config.LabelFreeQuantConfigConverter
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.om.model.msq.MasterQuantProteinSetProfile
import fr.proline.core.om.provider.msi.cache.PeptideCacheRegistry
import fr.proline.core.om.provider.msq.impl.SQLMasterQuantPeptideProvider
import fr.proline.core.om.provider.msq.impl.SQLMasterQuantProteinSetProvider
import fr.proline.repository.DatabaseUpgrader
import fr.proline.repository.DriverType

class V0_8__core_2_0_0_UDS_MSI_data_migration extends JdbcMigration with LazyLogging {
  
  private final val me = V0_8__core_2_0_0_UDS_MSI_data_migration

  /**
   * Migrate UDS db in order to:
   *
   *  - migrate and update data from PSdb to each MSIdb.
   *  - perform minor UDSdb data upgrades
   */
  override def migrate(udsConn: Connection): Unit = {

    logger.info("Migrating UDS_MSI for Core 2.0.0...")

    // Check that the current driver is PostgreSQL
    val dbMetaData = udsConn.getMetaData
    val isPgDriver = dbMetaData.getDriverName.startsWith("PostgreSQL")

    // Retrieve userName and userPassword
    val userName = dbMetaData.getUserName
    val userPassword = DatabaseUpgrader.JDBC_PASSWORD

    // Initialize some variables
    var userHost: String = null
    var dataBaseName: String = null
    var serialProperties: String = null
    var allMigrationsOK: Boolean = true

    // Create some string builders
    val sbUrlMsi = new StringBuilder
    val sbUrlPs = new StringBuilder

    // Create a statement that will be used for SELECT operations on the UDSdb
    val udsStmt = udsConn.createStatement
    val udsStmt2 = udsConn.createStatement //for nested queries

    // Create a prepared statement to update the serialized properties of the external_db table
    val updateExternalDbState = udsConn.prepareStatement("UPDATE external_db SET serialized_properties=? WHERE type='MSI' and name=?")
    // Create a prepared statement to remove the PSdb from the external_db table
    val deletePsDbRowStmt = udsConn.prepareStatement("DELETE FROM external_db WHERE name='ps_db' AND type='PS'")
    // Create a prepared statement to update the number field of the quant_label table
    val updateQuantLabelNumberStmt = udsConn.prepareStatement("UPDATE quant_label SET number=? WHERE id=?")
    // Create a prepared statement to update the serialized properties of the master_quant_channel table
    val updateMqcPropsStmt = udsConn.prepareStatement("UPDATE master_quant_channel SET ident_data_set_id=?, ident_result_summary_id=?, serialized_properties=? WHERE id=?")
    // Create a prepared statement to update the serialized properties of the object_tree table of type 'quantitation.label_free_config'
    val updateQuantPropObjectTreePropsStmt = udsConn.prepareStatement("UPDATE object_tree SET clob_data=? WHERE id=?")

    try {

      // STEP 1 : Upgrade MSIdb (transfer PSdb data inside the MSIdb, and then upgrade the MSIdb data) --- //
      // --- NO UDS MODIFICATIONS SHOULD BE DONE During this STEP, UDS commit is called to save MSI extrenalDB Properties.
      // Specific UDS Modifications are done in STEP 2

      // Create a JSON parser
      val jsonParser = new JsonParser

      val extDbRs = udsStmt.executeQuery("SELECT id,name,host,serialized_properties FROM external_db WHERE type='MSI' ORDER BY id")
      while (extDbRs.next) {

        // Retrieve external DB information
        val extDbId = extDbRs.getLong("id")
        dataBaseName = extDbRs.getString("name")
        userHost = extDbRs.getString("host")
        serialProperties = extDbRs.getString("serialized_properties")

        // Reset the URL string builders
        sbUrlMsi.setLength(0)
        sbUrlPs.setLength(0)

        // Get or create external DB properties
        var extDbJsonProps: JsonObject = null
        try {
          extDbJsonProps = jsonParser.parse(serialProperties).getAsJsonObject
        } catch {
          case e: Exception => {
            logger.warn("***   Error while trying to access to External_db properties. May be null., assume empty.",e)
            // We use a fallback to an empty JSON object to avoid error if processing an externalDb that has no serialized properties.
            extDbJsonProps = jsonParser.parse("{}").getAsJsonObject
          }
        }

        if (
          StringUtils.isNotEmpty(dataBaseName) &&
          StringUtils.isNotEmpty(userName) &&
          StringUtils.isNotEmpty(userPassword) &&
          StringUtils.isNotEmpty(userHost)
        ) {

          // Create JDBC connection properties
          val connProps = new Properties
          connProps.setProperty("user", userName)
          connProps.setProperty("password", userPassword)
          connProps.setProperty("proline.project.max.pool.connection", "3")

          // Remove PSdb only if the current driver is PostgreSQL
          if (!extDbJsonProps.has("is_psdb_migration_ok") && isPgDriver) {
            logger.info("Performing MSIdb data migration using Pg driver...")

            connProps.setProperty("driver", "org.postgresql.Driver")

            // Open a connection to MSIdb
            sbUrlMsi.append("jdbc:postgresql://").append(userHost).append("/").append(dataBaseName)
            var msiConn: Connection = null
            try {
              msiConn = DriverManager.getConnection(sbUrlMsi.toString, connProps)
            } catch {
              case e: Exception =>
                logger.error("--- Can't connect to MSI DB " + dataBaseName + ". No Migration will be done for this MSI.", e)
            }

            if (msiConn != null) {
              // Open a connection to PSdb
              sbUrlPs.append("jdbc:postgresql://").append(userHost).append("/").append("ps_db")

              val isMigrationOK = me._transferPsDbDataToMsiDb(dataBaseName, extDbJsonProps, sbUrlPs.toString, connProps, msiConn)
              if(isMigrationOK) // update MSIData only if transfer is OK
                me._upgradeMsiDbData(extDbId, msiConn, udsConn, DriverType.POSTGRESQL)

              // Close MSIdb connections
              try {
                msiConn.close()
              } catch {
                case e: Exception =>
                  logger.error("Error while trying to close MSIdb", e)
              }

              // Tag the updated MSIdb
              if (isMigrationOK) {
                extDbJsonProps.addProperty("is_psdb_migration_ok", true)
                updateExternalDbState.setString(1, extDbJsonProps.toString())
                updateExternalDbState.setString(2, dataBaseName)
                updateExternalDbState.executeUpdate()
                udsConn.commit() //Commit in case error occurs during other msiDb migration. This MSIdb Should not be migrated again
              } else allMigrationsOK = false

              System.gc()
            }

            // Else => assume it's H2
          } else if (!extDbJsonProps.has("is_psdb_migration_ok")) {
            logger.info("Performing MSIdb data migration using H2 driver...")

            connProps.setProperty("driver", "org.h2.Driver")
            sbUrlMsi.append("jdbc:h2://").append(userHost).append("/").append(dataBaseName)
            val msiConn = DriverManager.getConnection(sbUrlMsi.toString, connProps)

            me._upgradeMsiDbData(extDbId, msiConn, udsConn, DriverType.H2)
            try {
              msiConn.close()
            } catch {
              case e: Exception =>
                logger.error("Error while trying to close MSIdb", e)
            }
          } else
            logger.warn("--- The MSIdb won't be updated because it already has been updated! " + dataBaseName)
        } else
          logger.warn("--- The MSIdb won't be updated because some connection parameters are missing!")

      } //End go through MSidbs

      extDbRs.close()

      // Remove PSdb only if the current driver is PostgreSQL
      if (isPgDriver && allMigrationsOK) {
        deletePsDbRowStmt.executeUpdate
        udsConn.commit()
      } else
        logger.warn("won't remove PSdb for a database engine different than PostgreSQL")

      // STEP 2 --- Perform minor UDSdb data upgrades --- //
        
      // *** Fix quant label.number value
      val quantLabelRs = udsStmt.executeQuery("SELECT id, quant_method_id FROM quant_label ORDER BY quant_method_id, id")
      var quantLabelNumber = 1
      var curQuantMethodId = 0L
      while (quantLabelRs.next) {
        val quantLabelId = quantLabelRs.getLong(1)
        val quantMethodId = quantLabelRs.getLong(2)
        
        if (quantMethodId == curQuantMethodId) quantLabelNumber += 1
        else {
          curQuantMethodId = quantMethodId
          quantLabelNumber = 1
        }
        
        // Assign negative values to avoid unique constraint violation
        updateQuantLabelNumberStmt.setInt(1, -quantLabelNumber)
        updateQuantLabelNumberStmt.setLong(2, quantLabelId)
        updateQuantLabelNumberStmt.executeUpdate()
      }
      quantLabelRs.close()
      
      // Inverse quant_label numbers to have positive values
      udsStmt.executeUpdate("UPDATE quant_label SET number = -number")

      // *** Convert QuantConfig properties from V1 to V2 format.
      val quantConfigPropsRs = udsStmt.executeQuery("SELECT id, clob_data FROM  object_tree ot where ot.schema_name = 'quantitation.label_free_config'")
      while (quantConfigPropsRs.next) {
        val objectTreed = quantConfigPropsRs.getLong("id")
        val quantConfigAsStr  = quantConfigPropsRs.getString("clob_data")

        val quantConfigV2AsMap = LabelFreeQuantConfigConverter.convertFromV1(ProfiJson.deserialize[Map[String,Object]](quantConfigAsStr))
        val quantConfigV2AsStr = ProfiJson.serialize(quantConfigV2AsMap)

        // Store the object tree
        updateQuantPropObjectTreePropsStmt.setString(1,quantConfigV2AsStr)
        updateQuantPropObjectTreePropsStmt.setLong(2,objectTreed)
        updateQuantPropObjectTreePropsStmt.executeUpdate()
      }
      quantConfigPropsRs.close()

      // *** Put MasterQuantChannelProperties IDs in the master_quant_channel table
      // SpectralCountProperties => weightsRefRSMIds should be weightsRefRsmIds to have the correct serialization key
      // TODO: move spectralCountProperties from MasterQuantChannelProperties to dedicated object tree
      val mqcPropsRs = udsStmt.executeQuery("SELECT id, serialized_properties FROM master_quant_channel WHERE serialized_properties IS NOT NULL")
      var mqcId: Long = 0L
      var propsAsStr: String = null

      while (mqcPropsRs.next) {

        // Properties example:
        // {"ident_dataset_id":9266,"ident_result_summary_id":766,"spectral_count_properties":{"weights_ref_rsmids":[754,756]}}
        mqcId = mqcPropsRs.getLong("id")

        // Retrieve serialized properties as string
        propsAsStr = mqcPropsRs.getString("serialized_properties")

        // Parse serialized properties
        var propsAsJson: JsonObject = null
        try {
          propsAsJson = jsonParser.parse(propsAsStr).getAsJsonObject
        } catch {
          case e: Exception => {
            logger.warn("** Error while trying to parse to 'master quant channel' properties. May be null, assume empty")
            propsAsJson = jsonParser.parse("{}").getAsJsonObject
          }
        }

        // Copy ident_dataset_id property to table
        if (propsAsJson.has("ident_dataset_id")) {
          //Test if exist in UDS !
          val dsId =  propsAsJson.get("ident_dataset_id").getAsLong
          val resDsId = udsStmt2.executeQuery(s"SELECT count(id) FROM data_set WHERE id = $dsId")
          resDsId.next()
          val count = resDsId.getInt(1)
          if(count>0) {
            updateMqcPropsStmt.setLong(1,dsId)
            propsAsJson.remove("ident_dataset_id")
          }
          resDsId.close()
        } else
          updateMqcPropsStmt.setNull(1, java.sql.Types.BIGINT)

        // Copy ident_result_summary_id property to table
        if (propsAsJson.has("ident_result_summary_id")) {
          updateMqcPropsStmt.setLong(2, propsAsJson.get("ident_result_summary_id").getAsLong)
          propsAsJson.remove("ident_result_summary_id")
        } else
          updateMqcPropsStmt.setNull(2, java.sql.Types.BIGINT)

        // Rename "weights_ref_rsmids" property to "weights_ref_rsm_ids"
        if (propsAsJson.has("spectral_count_properties")) {
          val scProps = propsAsJson.get("spectral_count_properties").getAsJsonObject
          if (scProps.has("weights_ref_rsmids")) {
            val weights = scProps.get("weights_ref_rsmids")
            scProps.remove("weights_ref_rsmids")
            scProps.add("weights_ref_rsm_ids", weights)
          }
        }

        // Update JSON properties
        if (propsAsJson.entrySet.size == 0)
          updateMqcPropsStmt.setNull(3, java.sql.Types.CLOB)
        else
          updateMqcPropsStmt.setString(3, propsAsJson.toString)

        updateMqcPropsStmt.setLong(4, mqcId)
        updateMqcPropsStmt.executeUpdate

      } // ends while (mqcPropsRs.next())
      mqcPropsRs.close()
      udsStmt2.close()

    } finally {
      sbUrlMsi.setLength(0)
      sbUrlPs.setLength(0)
      
      // Close statements
      me._tryToCloseStatement(updateExternalDbState)
      me._tryToCloseStatement(deletePsDbRowStmt)
      me._tryToCloseStatement(updateQuantLabelNumberStmt)
      me._tryToCloseStatement(updateQuantPropObjectTreePropsStmt)
      me._tryToCloseStatement(updateExternalDbState)
      udsStmt.close()
      udsStmt2.close()
    }
  }

}

object V0_8__core_2_0_0_UDS_MSI_data_migration extends LazyLogging {

  private val mqComponentUpdateQuery = "UPDATE master_quant_component SET serialized_properties = ? WHERE id = ?"
  private val objTreeUpdateQuery = "UPDATE object_tree SET clob_data = ? WHERE object_tree.id IN " +
    "(SELECT object_tree_id FROM master_quant_component WHERE master_quant_component.id = ?)"
  
  private def _mkSqlQueryForSequenceValueSetting(sequenceName: String, tableName: String): String = {
    "SELECT setval('" + sequenceName + "', COALESCE((SELECT MAX(id)+1 FROM " + tableName + "), 1), false)"
  }

  // Define some to SQL queries used to fix the current value of Postgres PKs sequences
  private val sqlQuerySetValOfAtomLabelIdSeq: String = _mkSqlQueryForSequenceValueSetting("atom_label_id_seq", "atom_label")
  private val sqlQuerySetValOfPeptideIdSeq: String = _mkSqlQueryForSequenceValueSetting("peptide_id_seq", "peptide")
  private val sqlQuerySetValOfPeptidePtmIdSeq: String = _mkSqlQueryForSequenceValueSetting("peptide_ptm_id_seq", "peptide_ptm")
  private val sqlQuerySetValOfPtmClassifIdSeq: String = _mkSqlQueryForSequenceValueSetting("ptm_classification_id_seq", "ptm_classification")
  private val sqlQuerySetValOfPtmEvidenceIdSeq: String = _mkSqlQueryForSequenceValueSetting("ptm_evidence_id_seq", "ptm_evidence")
  private val sqlQuerySetValOfPtmIdSeq: String = _mkSqlQueryForSequenceValueSetting("ptm_id_seq", "ptm")
  private val sqlQuerySetValOfPtmSpecifIdSeq: String = _mkSqlQueryForSequenceValueSetting("ptm_specificity_id_seq", "ptm_specificity")

  @throws(classOf[SQLException])
  @throws(classOf[IOException])
  protected def _transferPsDbDataToMsiDb(
    dataBaseName: String,
    extDbJsonProps: JsonObject,
    psConnURL: String,
    connProps: Properties,
    msiConn: Connection
  ): Boolean = {

    // Important note: we create a fresh PSdb connection because TEMP tables will be created in the PSdb
    val psConn = DriverManager.getConnection(psConnURL, connProps)

    // Create statements
    val msiStmt = msiConn.createStatement
    val psStmt = psConn.createStatement

    // Create some prepared statements
    val msiInsertPtmClassifStmt = msiConn.prepareStatement("INSERT INTO ptm_classification (id,name) VALUES (?,?)")
    val msiInsertPtmSpecificityStmt = msiConn.prepareStatement("INSERT INTO ptm_specificity (id,location,residue,ptm_id,classification_id) VALUES (?,?,?,?,?)")
    val msiUpdatePtmSpecificityStmt = msiConn.prepareStatement("UPDATE ptm_specificity SET ptm_id = ?, classification_id = ? WHERE id = ?")
    val msiInsertPtmEvidenceStmt = msiConn.prepareStatement("INSERT INTO ptm_evidence (id,type,is_required,composition,mono_mass,average_mass,serialized_properties,specificity_id,ptm_id) VALUES (?,?,?,?,?,?,?,?,?)")

    // Instantiate Postgres CopyManager for MSI and PS databases
    val cpManagerMsi = msiConn.asInstanceOf[PGConnection].getCopyAPI
    val cpManagerPs = psConn.asInstanceOf[PGConnection].getCopyAPI

    // Declare some HashMaps
    val msiPeptideIds = new util.ArrayList[Long] // this list should be unique
    val msiPtmSpecIds = new util.HashSet[Long]

    val ptmCsvContent = new StringBuilder
    val ptmWithNoUnimodCsvContent = new StringBuilder

    val batchSize = 2000
    val fetchSize = 10000
    var recordCount: Int = 0
    var isMigrationOK = true

    try {
      logger.info(s"Upgrading MSIdb - $dataBaseName")

      // Set auto-commit to off (enable transactions)
      msiConn.setAutoCommit(false)
      psConn.setAutoCommit(false)

      // Retrieve PTM specificity ids and PTM ids
      val msiPtmSpecificityRs = msiStmt.executeQuery("SELECT id FROM ptm_specificity")
      msiPtmSpecificityRs.setFetchSize(fetchSize)
      while (msiPtmSpecificityRs.next) {
        val msiPtmSpecificityId = msiPtmSpecificityRs.getLong("id")
        if (msiPtmSpecificityId > 0)
          msiPtmSpecIds.add(msiPtmSpecificityId)
      }
      msiPtmSpecificityRs.close()

      // Retrieve the list of modified peptide ids
      val modifiedPeptideIdsRs = msiStmt.executeQuery("SELECT DISTINCT peptide_id FROM peptide_readable_ptm_string")
      modifiedPeptideIdsRs.setFetchSize(fetchSize)
      while (modifiedPeptideIdsRs.next) {
        val msiPeptideId = modifiedPeptideIdsRs.getLong("peptide_id")
        if (msiPeptideId > 0)
          msiPeptideIds.add(msiPeptideId)
      }
      modifiedPeptideIdsRs.close()

      logger.info("Number of peptides having PTMs in MSIdb: {}", msiPeptideIds.size)

      // To perform migration PSdb related tables must be cleaned
      msiStmt.executeUpdate("UPDATE ptm_specificity SET ptm_id=null;" +
        "DELETE FROM ptm;" +
        "DELETE FROM ptm_classification;" +
        "DELETE FROM ptm_evidence;" +
        "DELETE FROM peptide_ptm;")

      // Insert PTM records
      logger.info("Copying content of the 'ptm' table from PSdb to MSIdb...")

      val psPtmRs = psStmt.executeQuery("SELECT * FROM ptm")
      psPtmRs.setFetchSize(fetchSize)
      // Iterate PSdb PTMs and append the PTM data to the ptmCsvContent StringBuilder
      while (psPtmRs.next) {
        val ptmId = psPtmRs.getLong("id")
        if (ptmId > 0) {
          val ptmUnimodId = psPtmRs.getLong("unimod_id")
          val ptmFullName = psPtmRs.getString("full_name")
          val ptmShortName = psPtmRs.getString("short_name")
          if (ptmUnimodId > 0)
            ptmCsvContent.append(ptmId).append("|").append(ptmUnimodId).append("|").append(ptmFullName).append("|").append(ptmShortName).append("\n")
          else
            ptmWithNoUnimodCsvContent.append(ptmId).append("|").append(ptmFullName).append("|").append(ptmShortName).append("\n")
        }
      }

      //System.out.println(ptmCsvContent.toString());
      // Copy the content of StringBuilder to the 'ptm' table of the MSIdb
      // FIXME: use 'windows-1251' encoding because some of some special characters (i.e. ® icon) => it may not be cross-platform
      // Example: 950|1341|Native iodoacetyl Tandem Mass Tag®|iodoTMT
      cpManagerMsi.copyIn("COPY ptm(id,unimod_id,full_name,short_name) FROM STDIN WITH DELIMITER AS '|' CSV ENCODING 'windows-1251'", new ByteArrayInputStream(ptmCsvContent.toString.getBytes))
      if (ptmWithNoUnimodCsvContent.nonEmpty)
        cpManagerMsi.copyIn("COPY ptm(id,full_name,short_name) FROM STDIN WITH DELIMITER AS '|' CSV ENCODING 'windows-1251'", new ByteArrayInputStream(ptmWithNoUnimodCsvContent.toString.getBytes))

      // Clear the ptmCsvContent StringBuilder and close the psPtmRs
      ptmCsvContent.setLength(0)
      ptmWithNoUnimodCsvContent.setLength(0)
      psPtmRs.close()

      // Insert records in the MSIdb 'ptm_classification' table
      logger.info("Inserting records in the 'ptm_classification' table of the MSIdb...")

      val psPtmClassifRs = psStmt.executeQuery("SELECT * FROM ptm_classification")
      while (psPtmClassifRs.next) {
        val ptmClassifId = psPtmClassifRs.getLong("id")
        val ptmClassifName = psPtmClassifRs.getString("name")
        msiInsertPtmClassifStmt.setLong(1, ptmClassifId)
        msiInsertPtmClassifStmt.setString(2, ptmClassifName)
        msiInsertPtmClassifStmt.execute
      }
      // Close the psPtmClassifRs
      psPtmClassifRs.close()

      // Update the MSIdb 'ptm_specificity' table using a batch
      logger.info("Inserting records in the 'ptm_specificity' table of the MSIdb...")

      val psPtmSpecificityRs = psStmt.executeQuery("SELECT * FROM ptm_specificity")
      while (psPtmSpecificityRs.next) {
        val ptmSpeficityId = psPtmSpecificityRs.getLong("id")
        val location = psPtmSpecificityRs.getString("location")
        val residue = psPtmSpecificityRs.getString("residue")
        val ptmSpeficityPtmIdFK = psPtmSpecificityRs.getLong("ptm_id")
        val ptmSpeficityPtmClassifIdFK = psPtmSpecificityRs.getLong("classification_id")

        if (msiPtmSpecIds.contains(ptmSpeficityId)) {
          msiUpdatePtmSpecificityStmt.setLong(1, ptmSpeficityPtmIdFK)
          msiUpdatePtmSpecificityStmt.setLong(2, ptmSpeficityPtmClassifIdFK)
          msiUpdatePtmSpecificityStmt.setLong(3, ptmSpeficityId)
          msiUpdatePtmSpecificityStmt.addBatch()
        } else {
          msiInsertPtmSpecificityStmt.setLong(1, ptmSpeficityId)
          msiInsertPtmSpecificityStmt.setString(2, location)
          msiInsertPtmSpecificityStmt.setString(3, residue)
          msiInsertPtmSpecificityStmt.setLong(4, ptmSpeficityPtmIdFK)
          msiInsertPtmSpecificityStmt.setLong(5, ptmSpeficityPtmClassifIdFK)
          msiInsertPtmSpecificityStmt.addBatch()
        }
      }
      msiInsertPtmSpecificityStmt.executeBatch

      // Clear the msiInsertPtmSpecificityStmt batch and close the psPtmSpecificityRs
      msiInsertPtmSpecificityStmt.clearBatch()
      psPtmSpecificityRs.close()

      // Execute update of table 'ptm_specificity' if the table was not empty before the migration
      if (!msiPtmSpecIds.isEmpty) {
        msiUpdatePtmSpecificityStmt.executeBatch
        msiUpdatePtmSpecificityStmt.clearBatch()
      }

      // Insert records in the MSIdb MSIdb 'ptm_evidence' table
      logger.info("Updating the content of 'ptm_evidence' table of the MSIdb...")

      val psPtmEvidenceRs = psStmt.executeQuery("SELECT * FROM ptm_evidence")
      while (psPtmEvidenceRs.next) {
        val ptmEvidenceId = psPtmEvidenceRs.getLong("id")
        val ptmEvidenceType = psPtmEvidenceRs.getString("type")
        val ptmEvidenceIsRequired = psPtmEvidenceRs.getBoolean("is_required")
        val ptmEvidenceComposition = psPtmEvidenceRs.getString("composition")
        val ptmEvidenceMonoMass = psPtmEvidenceRs.getDouble("mono_mass")
        val ptmEvidenceAverageMass = psPtmEvidenceRs.getDouble("average_mass")
        val ptmEvidenceProps = psPtmEvidenceRs.getString("serialized_properties")
        val ptmEvidenceSpecificityIdFK = psPtmEvidenceRs.getLong("specificity_id")
        val ptmEvidencePtmIdFK = psPtmEvidenceRs.getLong("ptm_id")

        // Insert values into ptm_evidence statement
        msiInsertPtmEvidenceStmt.setLong(1, ptmEvidenceId)
        msiInsertPtmEvidenceStmt.setString(2, ptmEvidenceType)
        msiInsertPtmEvidenceStmt.setBoolean(3, ptmEvidenceIsRequired)
        msiInsertPtmEvidenceStmt.setString(4, ptmEvidenceComposition)
        msiInsertPtmEvidenceStmt.setDouble(5, ptmEvidenceMonoMass)
        msiInsertPtmEvidenceStmt.setDouble(6, ptmEvidenceAverageMass)
        msiInsertPtmEvidenceStmt.setString(7, ptmEvidenceProps)

        // Handle different cases of 'ptm_evidence' FKs nullity
        if (ptmEvidenceSpecificityIdFK > 0 && ptmEvidencePtmIdFK > 0) {
          msiInsertPtmEvidenceStmt.setLong(8, ptmEvidenceSpecificityIdFK)
          msiInsertPtmEvidenceStmt.setLong(9, ptmEvidencePtmIdFK)
        } else if (ptmEvidencePtmIdFK > 0 && ptmEvidenceSpecificityIdFK == 0L) {
          msiInsertPtmEvidenceStmt.setNull(8, java.sql.Types.BIGINT)
          msiInsertPtmEvidenceStmt.setLong(9, ptmEvidencePtmIdFK)
        } else if (ptmEvidenceSpecificityIdFK > 0 && ptmEvidencePtmIdFK == 0L) {
          msiInsertPtmEvidenceStmt.setLong(8, ptmEvidenceSpecificityIdFK)
          msiInsertPtmEvidenceStmt.setNull(9, java.sql.Types.BIGINT)
        }
        // Append current statement values to batch
        msiInsertPtmEvidenceStmt.addBatch()
      } //End go through ptm evidence

      msiInsertPtmEvidenceStmt.executeBatch
      msiInsertPtmEvidenceStmt.clearBatch()
      psPtmEvidenceRs.close()

      // Create temporary table tmp_msi_modified_peptide containing all MSIdb peptides having a PTM
      psStmt.executeUpdate("CREATE TEMP TABLE tmp_msi_modified_peptide (peptide_id bigint) ON COMMIT DELETE ROWS")
      logger.debug("CREATE TEMP TABLE LENGHT ...")
      val psTempCountRs = psStmt.executeQuery("SELECT count(*) FROM tmp_msi_modified_peptide ")
      if (psTempCountRs.next())
        logger.debug("** NBR records == " + psTempCountRs.getInt(1))
      else
        logger.debug("** UNKNOWN NBR records ")

      recordCount = 0
      import scala.collection.JavaConversions._
      for (msiPeptideId <- msiPeptideIds) {
        recordCount += 1
        ptmCsvContent.append(msiPeptideId).append("\n")
        if (recordCount % batchSize == 0) {
          this._copyTempMsiModifiedPeptideTableContent(cpManagerPs, ptmCsvContent)
          ptmCsvContent.setLength(0)
          recordCount = 0
        }
      }
      this._copyTempMsiModifiedPeptideTableContent(cpManagerPs, ptmCsvContent)
      ptmCsvContent.setLength(0)
      recordCount = 0

      // Fill peptide_ptm table in the MSIdb
      logger.info("Inserting records in the 'peptide_ptm' table of the MSIdb...")
      val psCountPeptidePtmRs = psStmt.executeQuery("SELECT count(*) FROM peptide_ptm " + "RIGHT JOIN tmp_msi_modified_peptide ON peptide_ptm.peptide_id = tmp_msi_modified_peptide.peptide_id")
      if (psCountPeptidePtmRs.next())
        logger.debug("** NBR records == " + psCountPeptidePtmRs.getInt(1))
      else
        logger.debug("** UNKNOWN NBR records ")

      val psPeptidePtmRs = psStmt.executeQuery("SELECT * FROM peptide_ptm " + "RIGHT JOIN tmp_msi_modified_peptide ON peptide_ptm.peptide_id = tmp_msi_modified_peptide.peptide_id")
      psPeptidePtmRs.setFetchSize(fetchSize)
      var totalNbr = 0
      while (psPeptidePtmRs.next) {
        val peptidePtmId = psPeptidePtmRs.getLong("id")
        val peptidePtmSeqPos = psPeptidePtmRs.getInt("seq_position")
        val peptidePtmMonoMass = psPeptidePtmRs.getDouble("mono_mass")
        val peptidePtmAverageMass = psPeptidePtmRs.getDouble("average_mass")
        val peptidePtmPeptideIdFK = psPeptidePtmRs.getLong("peptide_id")
        val peptidePtmSpecificityIdFK = psPeptidePtmRs.getLong("ptm_specificity_id")

        if (msiPeptideIds.contains(peptidePtmPeptideIdFK)) {
          recordCount += 1
          totalNbr += 1
          ptmCsvContent.append(peptidePtmId).append("|").append(peptidePtmSeqPos).append("|").append(peptidePtmMonoMass).append("|").append(peptidePtmAverageMass).append("|").append(peptidePtmPeptideIdFK).append("|").append(peptidePtmSpecificityIdFK).append("\n")
          if (recordCount % batchSize == 0) {
            logger.info(s"Processed $recordCount rows in current batch...")
            this._copyPeptidePtmTableContent(cpManagerMsi, ptmCsvContent)
            ptmCsvContent.setLength(0)
            recordCount = 0
          }
        }
      }

      logger.info(s"Processed a total of $totalNbr rows.")
      this._copyPeptidePtmTableContent(cpManagerMsi, ptmCsvContent)
      ptmCsvContent.setLength(0)
      ptmWithNoUnimodCsvContent.setLength(0)
      psPeptidePtmRs.close()

      // Commit MSIdb changes
      msiConn.commit()

      // Update sequence value of several tables where we inserted records using PgCopy
      logger.info("Updating value of Postgres sequences...")
      msiStmt.executeQuery(this.sqlQuerySetValOfAtomLabelIdSeq)
      msiStmt.executeQuery(this.sqlQuerySetValOfPeptideIdSeq)
      msiStmt.executeQuery(this.sqlQuerySetValOfPeptidePtmIdSeq)
      msiStmt.executeQuery(this.sqlQuerySetValOfPtmClassifIdSeq)
      msiStmt.executeQuery(this.sqlQuerySetValOfPtmEvidenceIdSeq)
      msiStmt.executeQuery(this.sqlQuerySetValOfPtmIdSeq)
      msiStmt.executeQuery(this.sqlQuerySetValOfPtmSpecifIdSeq)

    } catch {
      case t: Throwable => {
        isMigrationOK = false
        logger.error(t.getMessage)
      }
    } finally {
      // Clear buffers
      msiPeptideIds.clear()
      msiPtmSpecIds.clear()

      // Clear StringBuilder
      ptmCsvContent.setLength(0)
      ptmWithNoUnimodCsvContent.setLength(0)

      // Close prepared statements
      _tryToCloseStatement(msiUpdatePtmSpecificityStmt)
      _tryToCloseStatement(msiInsertPtmClassifStmt)
      _tryToCloseStatement(msiInsertPtmEvidenceStmt)
      _tryToCloseStatement(msiInsertPtmSpecificityStmt)

      // Close MSIdb & PSdb main statements
      _tryToCloseStatement(msiStmt)
      _tryToCloseStatement(psStmt)

      // Close PSdb connections
      try {
        psConn.close()
      } catch {
        case e: Exception =>
          logger.error("Error while trying to close PSdb", e)
      }
    }

    isMigrationOK
  }

  @throws[SQLException]
  @throws[IOException]
  protected def _copyPeptidePtmTableContent(cpm: CopyManager, csvContent: StringBuilder): Unit = {
    cpm.copyIn("COPY peptide_ptm (id,seq_position,mono_mass,average_mass,peptide_id,ptm_specificity_id) FROM STDIN WITH DELIMITER AS '|' CSV ", new ByteArrayInputStream(csvContent.toString.getBytes))
  }

  @throws[SQLException]
  @throws[IOException]
  protected def _copyTempMsiModifiedPeptideTableContent(cpm: CopyManager, csvContent: StringBuilder): Unit = {
    cpm.copyIn("COPY tmp_msi_modified_peptide (peptide_id) FROM STDOUT WITH DELIMITER AS '|' CSV  ", new ByteArrayInputStream(csvContent.toString.getBytes))
  }

  /**
   * Upgrade MSIdb data in order to:
   * - persist result summary relations for quantitations
   * - add "peptidesCount: Int" to the QuantProteinSet JSON model
   * - fill peptideMatchesCounts field in class MasterQuantProteinSetProfile
   * - TODO: ommssa ionSeries should not be stored in the PeptideMatch (use an object tree in the MSIdb or dedicated instrument config)
   */
  @throws[SQLException]
  @throws[IOException]
  protected def _upgradeMsiDbData(extDbId: Long, msiConn: Connection, udsConn: Connection, driverType: DriverType): Unit = {

    // Be sure that the connection is not closed when the DatabaseConnectionContext is closed
    val msiDbCtx = new MsiDbConnectionContext(msiConn,driverType)
    msiDbCtx.setClosableConnection(false)
    
    // Create some EzDBC helpers
    val udsEzDBC = fr.proline.core.dal.ProlineEzDBC(udsConn, driverType)
    val msiEzDBC = fr.proline.core.dal.ProlineEzDBC(msiDbCtx)
      
    // Create a cache using a NULL project id
    val peptideCache = PeptideCacheRegistry.getOrCreate(0L)

    // Statement used to insert ResultSummary Relation
    val insertRsmRelationStmt = msiConn.prepareStatement("INSERT INTO result_summary_relation (parent_result_summary_id,child_result_summary_id) VALUES (? , ?)")

    def insertRsmRelation(parentRsmId: Long, childRsmId: Long) {
      insertRsmRelationStmt.setLong(1, parentRsmId)
      insertRsmRelationStmt.setLong(2, childRsmId)
      insertRsmRelationStmt.execute
    }
    
    try {
      // Retrieve project ID
      val projectId = udsEzDBC.selectLong("SELECT project_id FROM project_db_map WHERE external_db_id =" + extDbId)
      
      // --- Persist result summary relations for quantitations --- //
      
      // Retrieve existing RSM relations (should only correspond to ident RSM in the current version of the database)
      val childRsmIdsByParentRsmId = new LongMap[ArrayBuffer[Long]]
      msiEzDBC.selectAndProcess("SELECT * FROM result_summary_relation") { r =>
        val parentRsmId = r.nextLong
        val childRsmId = r.nextLong
        childRsmIdsByParentRsmId.getOrElseUpdate(parentRsmId, new ArrayBuffer[Long]) += childRsmId
      }
      
      // Retrieve Ident/Quant RSM <-> MQC mapping
      val selectQuantRsmMqcMappingQuery = "SELECT id, ident_result_summary_id, quant_result_summary_id, serialized_properties FROM master_quant_channel "+
      s"WHERE quantitation_id IN (SELECT id FROM data_set WHERE project_id = $projectId and type = 'QUANTITATION')"

      val createdQuantRsmIdByMqcId = new LongMap[Long]
      val mqcIdByQuantRsmId = new LongMap[Long]
      udsEzDBC.selectAndProcess(selectQuantRsmMqcMappingQuery) { r =>
        val mqcId = r.nextLong
        var identRsmIdOpt = r.nextLongOption
        val quantRsmId = r.nextLong
        val propsAsStr = r.nextString
        
        mqcIdByQuantRsmId.put(quantRsmId, mqcId)

        // IdentRsm may be null. Have to get info from serialized properties !
        if (identRsmIdOpt.isEmpty) {
          //test if not in serialized props
          val jsonParser = new JsonParser
          var propsAsJson: Option[JsonObject] = None
          try {
            propsAsJson = Some(jsonParser.parse(propsAsStr).getAsJsonObject)
          } catch {
            case e: Exception => propsAsJson = None
          }
          if (propsAsJson.isDefined && propsAsJson.get.has("ident_result_summary_id")) {
            identRsmIdOpt = Some(propsAsJson.get.get("ident_result_summary_id").getAsLong)
          }
        }

        // Determine whether the quant RSM have been cloned from an indent RSM or not
        if (identRsmIdOpt.isEmpty) {
          if(quantRsmId != null && quantRsmId>0) {
            logger.debug(s"Updating relations of the quant RSM with ID=$quantRsmId...")
            createdQuantRsmIdByMqcId.put(mqcId, quantRsmId)
          } else {
            logger.error(s" MasterQuant Channel $mqcId don't reference RSMs !! ")
          }
        }
        else {
          // Update RSM relations of RSMs that were cloned from an ident RSM
          val parentRsmId = identRsmIdOpt.get
          val childRsmIds = childRsmIdsByParentRsmId.getOrElse(parentRsmId, new ArrayBuffer[Long]())
          
          logger.debug(s"Updating relations of the ident RSM with ID=$parentRsmId...")
          childRsmIds.foreach { childRsmId =>
            insertRsmRelation(quantRsmId, childRsmId)
          }
        }
      }

      // Retrieve child ident RSM <-> MQC mapping and update RSM relations of quant RSMs that were not cloned from an ident RSM
      val selectIdentRsmMqcMappingQuery = "SELECT id, master_quant_channel_id, ident_result_summary_id FROM quant_channel "+
      s"WHERE quantitation_id IN (SELECT id FROM data_set WHERE project_id = $projectId and type = 'QUANTITATION') " +
      "ORDER BY master_quant_channel_id ASC, number ASC"
      
      val sortedQcIdsByMqcId = new LongMap[ArrayBuffer[Long]]
      udsEzDBC.selectAndProcess(selectIdentRsmMqcMappingQuery) { r =>
        val qcId = r.nextLong
        val mqcId = r.nextLong
        val childIdentRsmId = r.nextLong
        
        sortedQcIdsByMqcId.getOrElseUpdate(mqcId, new ArrayBuffer[Long]) += qcId
        
        // Retrieve parent ident/quant RSM ids
        val parentQuantRsmIdOpt = createdQuantRsmIdByMqcId.get(mqcId)
        if (parentQuantRsmIdOpt.isDefined) {
          insertRsmRelation(parentQuantRsmIdOpt.get, childIdentRsmId)
        }
      }

      // --- Update MasterQuantProtSets entities --- //
      val mqPeptideProvider = new SQLMasterQuantPeptideProvider(msiDbCtx, peptideCache)
      val mqProtSetProvider = new SQLMasterQuantProteinSetProvider(msiDbCtx, peptideCache)
      
      val quantRsmIds = msiEzDBC.selectLongs("SELECT id FROM result_summary WHERE is_quantified = 't'")
      
      for (quantRsmId <- quantRsmIds) {
        val sortedQcIds : Array[Long] = if(mqcIdByQuantRsmId.contains(quantRsmId)) { //may have no Dataset/MQCh in UDS if was cleared from trash
          val mqcId = mqcIdByQuantRsmId(quantRsmId)
          sortedQcIdsByMqcId(mqcId).toArray
        } else {
          Array.empty[Long]
        }

        this._updateMqProtSets(msiDbCtx, quantRsmId, sortedQcIds, mqPeptideProvider, mqProtSetProvider)
      }

    } finally {
      _tryToCloseStatement(insertRsmRelationStmt)
      msiDbCtx.close()

      // Clear TMP peptide cache
      logger.info("Clearing TMP peptide cache used for data migration...")
      peptideCache.clear()
    }
  }

  protected def _updateMqProtSets(
    msiDbCtx: MsiDbConnectionContext,
    quantRsmId: Long,
    sortedQcIds: Array[Long],
    mqPeptideProvider: SQLMasterQuantPeptideProvider,
    mqProtSetProvider: SQLMasterQuantProteinSetProvider
  ) {

    val mqPeps = mqPeptideProvider.getQuantResultSummariesMQPeptides(Seq(quantRsmId))
    val mqPepById = mqPeps.mapByLong(_.id)
    val mqProtSets = mqProtSetProvider.getQuantResultSummariesMQProteinSets(Seq(quantRsmId), loadMQPeptides = false)
    val finalSortedQcIds = if(sortedQcIds.isEmpty) { //Should get information from masterQuantPeptideIons
      val qcIdSet = new LongMap[Unit]
      for (mqPep <- mqPeps; mqPepIon <- mqPep.masterQuantPeptideIons) {
        mqPepIon.properties.get.getBestPeptideMatchIdMap().keys.foreach { qcIdSet.put(_, ()) }
      }
      // we assume here that qcIds are sorted by number field...
      qcIdSet.keys.toArray.sorted
    } else
      sortedQcIds
    val qcIdCount = finalSortedQcIds.length

    this.logger.info(s"Updating MasterQuantProtSets of quant RSM with id=$quantRsmId...")

    DoJDBCWork.tryTransactionWithEzDBC(msiDbCtx) { ezDBC =>
      ezDBC.executeInBatch(mqComponentUpdateQuery) { mqComponentUpdateStmt =>
        ezDBC.executeInBatch(objTreeUpdateQuery) { objTreeUpdateStmt =>

          for (mqProtSet <- mqProtSets) {
            val mqProtSetProfilesOpt = mqProtSet.properties.flatMap(_.getMqProtSetProfilesByGroupSetupNumber)

            // --- Add "peptidesCount: Int" to the QuantProteinSet JSON model --- //
            val pepCountByQcId = new LongMap[Int]()
            for (
              mqPep <- mqProtSet.masterQuantPeptides;
              (qcId, qPep) <- mqPep.quantPeptideMap
            ) {
              val pepCount = pepCountByQcId.getOrElseUpdate(qcId, 0)
              pepCountByQcId(qcId) = pepCount + 1
            }

            for ((qcId, qProtSet) <- mqProtSet.quantProteinSetMap) {
              qProtSet.copy(peptidesCount = pepCountByQcId.get(qcId))
            }

            // --- Fill peptideMatchesCounts field in class MasterQuantProteinSetProfile --- //
            if (mqProtSetProfilesOpt.isDefined && !mqProtSetProfilesOpt.get.isEmpty) {
              val profiles = mqProtSetProfilesOpt.get.getOrElse(1, Array.empty[MasterQuantProteinSetProfile])

              for (profile <- profiles) {
                val mqPepIds = profile.getMqPeptideIds()
                val profileMqPeps = mqPepIds.map(mqPepById)

                val psmCountByQcId = new LongMap[Int]()
                for (mqPep <- profileMqPeps; (qcId, qPep) <- mqPep.quantPeptideMap) {
                  val psmCount = psmCountByQcId.getOrElseUpdate(qcId, 0)
                  psmCountByQcId(qcId) = psmCount + qPep.peptideMatchesCount
                }
                assert(psmCountByQcId.size <= qcIdCount, "Invalid number of QC ids")

                profile.peptideMatchesCounts = finalSortedQcIds.map(psmCountByQcId)
              }
            }

            // Update MasterQuantProtSets properties
            mqComponentUpdateStmt.executeWith(
              mqProtSet.properties.map(props => ProfiJson.serialize(props)),
              mqProtSet.getMasterQuantComponentId()
            )

            // Retrieve quant protein sets sorted by quant channel
            val quantProtSetMap = mqProtSet.quantProteinSetMap
            val quantProtSets = finalSortedQcIds.map { quantProtSetMap.getOrElse(_, null) }

            // Update MasterQuantProtSets object tree
            objTreeUpdateStmt.executeWith(
              ProfiJson.serialize(quantProtSets),
              mqProtSet.getMasterQuantComponentId()
            )
          } // ends for (mqProtSet <- mqProtSets)

        }
      }
    }

  }

  protected def _tryToCloseStatement(stmt: Statement): Unit = {
    try {
      if (stmt != null && !stmt.isClosed)
        stmt.close()
    } catch {
      case e: Exception =>
        logger.error("Error while trying to close Statement", e)
    }
  }

}
