package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.generate

import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection

import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.dal.tables.msi.{
  MsiDbPeptideTable,
  MsiDbPeptideMatchTable,
  MsiDbPeptideMatchRelationTable,
  MsiDbProteinMatchTable,
  MsiDbSequenceMatchTable
}
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi._
import fr.proline.util.sql.encodeRecordForPgCopy
import fr.proline.repository.DatabaseContext
import fr.proline.core.dal.helper.MsiDbHelper

private[msi] class PgRsWriter() extends SQLiteRsWriter() {

  // val bulkCopyManager = new CopyManager( msiDb1.ezDBC.connection.asInstanceOf[BaseConnection] )

  private val peptideTableCols = MsiDbPeptideTable.columnsAsStrList.mkString(",")
  private val pepMatchTableCols = MsiDbPeptideMatchTable.columnsAsStrList.filter(_ != "id").mkString(",")
  private val pepMatchRelTableCols = MsiDbPeptideMatchRelationTable.columnsAsStrList.mkString(",")
  private val protMatchTableCols = MsiDbProteinMatchTable.columnsAsStrList.filter(_ != "id").mkString(",")
  private val seqMatchTableCols = MsiDbSequenceMatchTable.columnsAsStrList.mkString(",")

  //def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ):  Map[String,Int] = null
  // TODO: insert peptides into a TMP table

  override def storeNewPeptides(peptides: Seq[Peptide], msiDb: DatabaseContext): Array[Peptide] = {

    val msiCon = msiDb.getConnection // MUST be in SQL (Postgres) mode
    val bulkCopyManager = new CopyManager(msiCon.asInstanceOf[BaseConnection])

    // Create TMP table
    val tmpPeptideTableName = "tmp_peptide_" + (scala.math.random * 1000000).toInt
    logger.info("creating temporary table '" + tmpPeptideTableName + "'...")

    val stmt = msiCon.createStatement();
    stmt.executeUpdate("CREATE TEMP TABLE " + tmpPeptideTableName + " (LIKE peptide)")

    // Bulk insert of peptides
    logger.info("BULK insert of peptides")

    val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpPeptideTableName + " ( " + peptideTableCols + " ) FROM STDIN")

    //val newPeptides = new ArrayBuffer[Peptide](0)

    // Iterate over peptides
    for (peptide <- peptides) {

      val ptmString = if (peptide.ptmString != null) peptide.ptmString else ""
      var peptideValues = List(peptide.id,
        peptide.sequence,
        ptmString,
        peptide.calculatedMass,
        "")

      // Store peptide
      val peptideBytes = encodeRecordForPgCopy(peptideValues)
      pgBulkLoader.writeToCopy(peptideBytes, 0, peptideBytes.length)

    }

    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()

    // Move TMP table content to MAIN table
    logger.info("move TMP table " + tmpPeptideTableName + " into MAIN peptide table")
    stmt.executeUpdate("INSERT into peptide (" + peptideTableCols + ") " +
      "SELECT " + peptideTableCols + " FROM " + tmpPeptideTableName)

    peptides.toArray
  }

  //def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = null

  //def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein] = null

  //def storeNewProteins( proteins: Seq[Protein] ): Array[Protein] = null

  override def storeRsPeptideMatches(rs: ResultSet, msiDb: DatabaseContext): Int = {

    val msiCon = msiDb.getConnection // MUST be in SQL (Postgres) mode
    val msiEzDbc = ProlineEzDBC(msiCon, msiDb.getDriverType)
    val scoringIdByType = new MsiDbHelper(msiEzDbc).getScoringIdByType

    /// Retrieve some vars
    val rsId = rs.id
    val peptideMatches = rs.peptideMatches

    // Create TMP table
    val tmpPepMatchTableName = "tmp_peptide_match_" + (scala.math.random * 1000000).toInt
    logger.info("creating temporary table '" + tmpPepMatchTableName + "'...")

    val stmt = msiCon.createStatement();
    stmt.executeUpdate("CREATE TEMP TABLE " + tmpPepMatchTableName + " (LIKE peptide_match)")

    // Bulk insert of peptide matches
    logger.info("BULK insert of peptide matches")

    val bulkCopyManager = new CopyManager(msiCon.asInstanceOf[BaseConnection])

    val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpPepMatchTableName + " ( id, " + pepMatchTableCols + " ) FROM STDIN")

    // Iterate over peptide matches to store them
    for (peptideMatch <- peptideMatches) {

      val peptide = peptideMatch.peptide
      val msQuery = peptideMatch.msQuery
      val scoreType = peptideMatch.scoreType

      val scoringId = scoringIdByType.get(scoreType)
      assert(scoringId != None, "can't find a scoring id for the score type '" + scoreType + "'")
      val pepMatchPropsAsJSON = if (peptideMatch.properties != None) generate(peptideMatch.properties.get) else ""
      val bestChildId = peptideMatch.getBestChildId

      // Build a row containing peptide match values
      val pepMatchValues = List(peptideMatch.id,
        msQuery.charge,
        msQuery.moz,
        peptideMatch.score,
        peptideMatch.rank,
        peptideMatch.deltaMoz,
        peptideMatch.missedCleavage,
        peptideMatch.fragmentMatchesCount,
        peptideMatch.isDecoy,
        pepMatchPropsAsJSON,
        peptide.id,
        peptideMatch.msQuery.id,
        if (bestChildId == 0) "" else bestChildId,
        scoringId.get,
        peptideMatch.resultSetId)

      // Store peptide match
      val pepMatchBytes = encodeRecordForPgCopy(pepMatchValues)
      pgBulkLoader.writeToCopy(pepMatchBytes, 0, pepMatchBytes.length)

    }

    // End of BULK copy
    val nbInsertedPepMatches = pgBulkLoader.endCopy()

    // Move TMP table content to MAIN table
    logger.info("move TMP table " + tmpPepMatchTableName + " into MAIN peptide_match table")
    stmt.executeUpdate("INSERT into peptide_match (" + pepMatchTableCols + ") " +
      "SELECT " + pepMatchTableCols + " FROM " + tmpPepMatchTableName)

    // Retrieve generated peptide match ids
    val pepMatchIdByKey = msiEzDbc.select(
      "SELECT ms_query_id, peptide_id, id FROM peptide_match WHERE result_set_id = " + rsId) { r =>
        (r.nextInt + "%" + r.nextInt -> r.nextInt)
      } toMap

    // Iterate over peptide matches to update them
    peptideMatches.foreach { pepMatch => pepMatch.id = pepMatchIdByKey(pepMatch.msQuery.id + "%" + pepMatch.peptide.id) }

    this._linkPeptideMatchesToChildren(peptideMatches, msiDb)

    nbInsertedPepMatches.toInt
  }

  private def _linkPeptideMatchesToChildren(peptideMatches: Seq[PeptideMatch], msiDb: DatabaseContext): Unit = {

    val bulkCopyManager = new CopyManager(msiDb.getConnection.asInstanceOf[BaseConnection]) // MUST be in SQL (Postgres) mode

    val pgBulkLoader = bulkCopyManager.copyIn("COPY peptide_match_relation ( " + pepMatchRelTableCols + " ) FROM STDIN")

    // Iterate over peptide matches to store them
    for (peptideMatch <- peptideMatches) {
      if (peptideMatch.children != null && peptideMatch.children != None) {
        for (pepMatchChild <- peptideMatch.children.get) {

          // Build a row containing peptide_match_relation values
          val pepMatchRelationValues = List(peptideMatch.id,
            pepMatchChild.id,
            peptideMatch.resultSetId)

          // Store peptide match
          val pepMatchRelationBytes = encodeRecordForPgCopy(pepMatchRelationValues)
          pgBulkLoader.writeToCopy(pepMatchRelationBytes, 0, pepMatchRelationBytes.length)
        }
      }
    }

    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()

  }

  override def storeRsProteinMatches(rs: ResultSet, msiDb: DatabaseContext): Int = {

    val msiCon = msiDb.getConnection // MUST be in SQL (Postgres) mode
    val msiEzDbc = ProlineEzDBC(msiCon, msiDb.getDriverType)
    val scoringIdByType = new MsiDbHelper(msiEzDbc).getScoringIdByType

    // Retrieve some vars
    val rsId = rs.id
    val proteinMatches = rs.proteinMatches

    // Create TMP table
    val tmpProtMatchTableName = "tmp_protein_match_" + (scala.math.random * 1000000).toInt
    logger.info("creating temporary table '" + tmpProtMatchTableName + "'...")

    val stmt = msiCon.createStatement()
    stmt.executeUpdate("CREATE TEMP TABLE " + tmpProtMatchTableName + " (LIKE protein_match)")

    // Bulk insert of protein matches
    logger.info("BULK insert of protein matches")

    val bulkCopyManager = new CopyManager(msiCon.asInstanceOf[BaseConnection])
    val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpProtMatchTableName + " ( id, " + protMatchTableCols + " ) FROM STDIN")

    // Iterate over protein matches to store them
    for (proteinMatch <- proteinMatches) {

      val scoreType = proteinMatch.scoreType

      val msiEzDbc = ProlineEzDBC(msiCon, msiDb.getDriverType) // MUST be in SQL mode

      val scoringId = scoringIdByType.get(scoreType)
      if (scoringId == None) throw new Exception("can't find a scoring id for the score type '" + scoreType + "'")
      //val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) generate(peptideMatch.properties.get) else ""

      // Build a row containing protein match values
      val protMatchValues = List(proteinMatch.id,
        proteinMatch.accession,
        proteinMatch.description,
        if (proteinMatch.geneName == null) "" else proteinMatch.geneName,
        proteinMatch.score,
        proteinMatch.coverage,
        proteinMatch.sequenceMatches.length,
        proteinMatch.peptideMatchesCount,
        proteinMatch.isDecoy,
        proteinMatch.isLastBioSequence,
        "",
        proteinMatch.taxonId,
        "", // proteinMatch.getProteinId
        scoringId.get,
        rsId)

      // Store protein match
      val protMatchBytes = encodeRecordForPgCopy(protMatchValues)
      pgBulkLoader.writeToCopy(protMatchBytes, 0, protMatchBytes.length)

    }

    // End of BULK copy
    val nbInsertedProtMatches = pgBulkLoader.endCopy()

    // Move TMP table content to MAIN table
    logger.info("move TMP table " + tmpProtMatchTableName + " into MAIN protein_match table")
    stmt.executeUpdate("INSERT into protein_match (" + protMatchTableCols + ") " +
      "SELECT " + protMatchTableCols + " FROM " + tmpProtMatchTableName)

    // Retrieve generated protein match ids
    val protMatchIdByAc = msiEzDbc.select(
      "SELECT accession, id FROM protein_match WHERE result_set_id = " + rsId) { r =>
        (r.nextString -> r.nextInt)
      } toMap

    // Iterate over protein matches to update them
    proteinMatches.foreach { protMatch => protMatch.id = protMatchIdByAc(protMatch.accession) }

    nbInsertedProtMatches.toInt
  }

  override def storeRsSequenceMatches(rs: ResultSet, msiDb: DatabaseContext): Int = {

    val msiCon = msiDb.getConnection // MUST be in SQL (Postgres) mode
    val bulkCopyManager = new CopyManager(msiCon.asInstanceOf[BaseConnection])

    // Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches

    // Retrieve primary db connection    

    // Create TMP table
    val tmpSeqMatchTableName = "tmp_sequence_match_" + (scala.math.random * 1000000).toInt
    logger.info("creating temporary table '" + tmpSeqMatchTableName + "'...")

    val stmt = msiCon.createStatement();
    stmt.executeUpdate("CREATE TEMP TABLE " + tmpSeqMatchTableName + " (LIKE sequence_match)")

    // Bulk insert of sequence matches
    logger.info("BULK insert of sequence matches")

    val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpSeqMatchTableName + " ( " + seqMatchTableCols + " ) FROM STDIN")

    // Iterate over protein matches
    for (proteinMatch <- proteinMatches) {

      val proteinMatchId = proteinMatch.id
      val proteinId = proteinMatch.getProteinId

      for (seqMatch <- proteinMatch.sequenceMatches) {

        var seqMatchValues = List(proteinMatchId,
          seqMatch.getPeptideId,
          seqMatch.start,
          seqMatch.end,
          seqMatch.residueBefore.toString(),
          seqMatch.residueAfter.toString(),
          isDecoy,
          "", //seqMatch.hasProperties ? encode_json( seqMatch.properties ) : undef,
          seqMatch.getBestPeptideMatchId,
          seqMatch.resultSetId)

        // Store sequence match
        val seqMatchBytes = encodeRecordForPgCopy(seqMatchValues)
        pgBulkLoader.writeToCopy(seqMatchBytes, 0, seqMatchBytes.length)
      }
    }

    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()

    // Move TMP table content to MAIN table
    logger.info("move TMP table " + tmpSeqMatchTableName + " into MAIN sequence_match table")
    stmt.executeUpdate("INSERT into sequence_match (" + seqMatchTableCols + ") " +
      "SELECT " + seqMatchTableCols + " FROM " + tmpSeqMatchTableName)

    nbInsertedRecords.toInt
  }

}