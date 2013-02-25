package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.generate
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import fr.profi.jdbc.easy._
import fr.proline.core.dal._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
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
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.repository.util.PostgresUtils

private[msi] class PgRsWriter() extends SQLiteRsWriter() {

  // val bulkCopyManager = new CopyManager( msiDb1.ezDBC.connection.asInstanceOf[BaseConnection] )

  private val peptideTableCols = MsiDbPeptideTable.columnsAsStrList.mkString(",")
  private val pepMatchTableCols = MsiDbPeptideMatchTable.columnsAsStrList.filter(_ != "id").mkString(",")
  private val pepMatchRelTableCols = MsiDbPeptideMatchRelationTable.columnsAsStrList.mkString(",")
  private val protMatchTableCols = MsiDbProteinMatchTable.columnsAsStrList.filter(_ != "id").mkString(",")
  private val seqMatchTableCols = MsiDbSequenceMatchTable.columnsAsStrList.mkString(",")

  //"SELECT ms_query_id, peptide_id, id FROM peptide_match WHERE result_set_id = " + rsId
  
  private val pepMatchUniqueFKsQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) => 
    List(t.MS_QUERY_ID,t.PEPTIDE_ID,t.ID) -> "WHERE "~ t.RESULT_SET_ID ~" = ?"
  )
  private val protMatchUniqueFKQuery = new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) => 
    List(t.ACCESSION,t.ID) -> "WHERE "~ t.RESULT_SET_ID ~" = ?"
  )
  
  //def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ):  Map[String,Int] = null
  // TODO: insert peptides into a TMP table

  override def storeNewPeptides(peptides: Seq[Peptide], msiDbCtx: DatabaseConnectionContext): Unit = {

    DoJDBCWork.withConnection( msiDbCtx, { msiCon =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(msiCon)
  
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
          peptide.properties.map(generate(_))
        )
  
        // Store peptide
        val peptideBytes = encodeRecordForPgCopy(peptideValues)
        pgBulkLoader.writeToCopy(peptideBytes, 0, peptideBytes.length)
      }
  
      // End of BULK copy
      val nbInsertedRecords = pgBulkLoader.endCopy()
  
      // Move TMP table content to MAIN table
      logger.info("move TMP table " + tmpPeptideTableName + " into MAIN peptide table")
      stmt.executeUpdate("INSERT into peptide (" + peptideTableCols + ") " +
        "SELECT " + peptideTableCols + " FROM " + tmpPeptideTableName
      )
    },true)
    
  }

  //def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = null

  //def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein] = null

  //def storeNewProteins( proteins: Seq[Protein] ): Array[Protein] = null

  override def storeRsPeptideMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {

    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val msiCon = msiEzDBC.connection
      val scoringIdByType = new MsiDbHelper(msiDbCtx).getScoringIdByType
      
      // Retrieve some vars
      val rsId = rs.id
      val peptideMatches = rs.peptideMatches
  
      // Create TMP table
      val tmpPepMatchTableName = "tmp_peptide_match_" + (scala.math.random * 1000000).toInt
      logger.info("creating temporary table '" + tmpPepMatchTableName + "'...")
  
      val stmt = msiCon.createStatement();
      stmt.executeUpdate("CREATE TEMP TABLE " + tmpPepMatchTableName + " (LIKE peptide_match)")
  
      // Bulk insert of peptide matches
      logger.info("BULK insert of peptide matches")
  
      val bulkCopyManager = PostgresUtils.getCopyManager(msiCon)
  
      val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpPepMatchTableName + " ( id, " + pepMatchTableCols + " ) FROM STDIN")
  
      // Iterate over peptide matches to store them
      for (peptideMatch <- peptideMatches) {
        
        val scoreType = peptideMatch.scoreType  
        val scoringId = scoringIdByType.get(scoreType)
        assert(scoringId != None, "can't find a scoring id for the score type '" + scoreType + "'")
        
        val msQuery = peptideMatch.msQuery
        val bestChildId = peptideMatch.getBestChildId
  
        // Build a row containing peptide match values
        val pepMatchValues = List(
          peptideMatch.id,
          msQuery.charge,
          msQuery.moz,
          peptideMatch.score,
          peptideMatch.rank,
          peptideMatch.deltaMoz,
          peptideMatch.missedCleavage,
          peptideMatch.fragmentMatchesCount,
          peptideMatch.isDecoy,
          peptideMatch.properties.map(generate(_)),
          peptideMatch.peptide.id,
          msQuery.id,
          if (bestChildId == 0) None else Some(bestChildId),
          scoringId.get,
          peptideMatch.resultSetId
        )
  
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
      val pepMatchIdByKey = msiEzDBC.select( pepMatchUniqueFKsQuery, rsId) { r =>
          (r.nextInt + "%" + r.nextInt -> r.nextInt)
        } toMap
  
      // Iterate over peptide matches to update them
      peptideMatches.foreach { pepMatch => pepMatch.id = pepMatchIdByKey(pepMatch.msQuery.id + "%" + pepMatch.peptide.id) }
  
      this._linkPeptideMatchesToChildren(peptideMatches, bulkCopyManager)
  
      nbInsertedPepMatches.toInt
      
    }, true)
    
  }

  private def _linkPeptideMatchesToChildren(peptideMatches: Seq[PeptideMatch], bulkCopyManager: CopyManager): Unit = {

    val pgBulkLoader = bulkCopyManager.copyIn("COPY peptide_match_relation ( " + pepMatchRelTableCols + " ) FROM STDIN")

    // Iterate over peptide matches to store them
    for (peptideMatch <- peptideMatches) {
      if (peptideMatch.children != null && peptideMatch.children != None) {
        for (pepMatchChild <- peptideMatch.children.get) {

          // Build a row containing peptide_match_relation values
          val pepMatchRelationValues = List(
            peptideMatch.id,
            pepMatchChild.id,
            peptideMatch.resultSetId
          )

          // Store peptide match
          val pepMatchRelationBytes = encodeRecordForPgCopy(pepMatchRelationValues)
          pgBulkLoader.writeToCopy(pepMatchRelationBytes, 0, pepMatchRelationBytes.length)
        }
      }
    }

    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()

  }

  override def storeRsProteinMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {

    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val msiCon = msiEzDBC.connection
      
      // TODO: retrieve this only once
      val scoringIdByType = new MsiDbHelper(msiDbCtx).getScoringIdByType
      
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
  
      val bulkCopyManager = PostgresUtils.getCopyManager(msiCon)
      val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpProtMatchTableName + " ( id, " + protMatchTableCols + " ) FROM STDIN")
  
      // Iterate over protein matches to store them
      for (proteinMatch <- proteinMatches) {
  
        val scoreType = proteinMatch.scoreType  
        val scoringId = scoringIdByType.get(scoreType)
        assert(scoringId != None,"can't find a scoring id for the score type '" + scoreType + "'")
        //val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) generate(peptideMatch.properties.get) else ""
  
        val proteinId = proteinMatch.getProteinId
        
        // Build a row containing protein match values
        val protMatchValues = List(
          proteinMatch.id,
          proteinMatch.accession,
          proteinMatch.description,
          Option(proteinMatch.geneName),
          proteinMatch.score,
          proteinMatch.coverage,
          proteinMatch.sequenceMatches.length,
          proteinMatch.peptideMatchesCount,
          proteinMatch.isDecoy,
          proteinMatch.isLastBioSequence,
          proteinMatch.properties.map(generate(_)),
          proteinMatch.taxonId,
          if (proteinId > 0) Some(proteinId) else None,
          scoringId.get,
          rsId
        )
  
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
      val protMatchIdByAc = msiEzDBC.select( protMatchUniqueFKQuery, rsId) { r =>
        (r.nextString -> r.nextInt)
      } toMap
  
      // Iterate over protein matches to update them
      proteinMatches.foreach { protMatch => protMatch.id = protMatchIdByAc(protMatch.accession) }
      
      nbInsertedProtMatches.toInt
      
    },true)
    
  }

  override def storeRsSequenceMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {

    DoJDBCReturningWork.withConnection( msiDbCtx, { msiCon =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(msiCon)
  
      // Retrieve some vars
      val rsId = rs.id
      val isDecoy = rs.isDecoy
      val proteinMatches = rs.proteinMatches
  
      // Retrieve primary db connection    
  
      // Create TMP table
      val tmpSeqMatchTableName = "tmp_sequence_match_" + (scala.math.random * 1000000).toInt
      logger.info("creating temporary table '" + tmpSeqMatchTableName + "'...")
  
      val stmt = msiCon.createStatement()
      stmt.executeUpdate("CREATE TEMP TABLE " + tmpSeqMatchTableName + " (LIKE sequence_match)")
      
      // Bulk insert of sequence matches
      logger.info("BULK insert of sequence matches")
      
      val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpSeqMatchTableName + " ( " + seqMatchTableCols + " ) FROM STDIN")
      
      // Iterate over protein matches
      for (proteinMatch <- proteinMatches) {
  
        val proteinMatchId = proteinMatch.id
        val proteinId = proteinMatch.getProteinId
  
        for (seqMatch <- proteinMatch.sequenceMatches) {
  
          var seqMatchValues = List(
            proteinMatchId,
            seqMatch.getPeptideId,
            seqMatch.start,
            seqMatch.end,
            seqMatch.residueBefore.toString(),
            seqMatch.residueAfter.toString(),
            isDecoy,
            seqMatch.properties.map(generate(_)),
            seqMatch.getBestPeptideMatchId,
            seqMatch.resultSetId
          )
  
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
      
    }, true )
    
  }

}