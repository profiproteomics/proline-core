package fr.proline.core.om.storer.msi.impl

import org.apache.commons.lang3.StringUtils.isEmpty
import com.codahale.jerkson.Json.generate
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection

import fr.proline.core.dal.MsiDb
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi._
import fr.proline.core.utils.sql.BoolToSQLStr

private[msi] class PgRsStorer( override val msiDb1: MsiDb // Main DB connection                        
                             ) extends SQLiteRsStorer( msiDb1 ) {
  
  val bulkCopyManager = new CopyManager( msiDb1.connection.asInstanceOf[BaseConnection] )
  
  //def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ):  Map[String,Int] = null
  // TODO: insert peptides into a TMP table
  
  //def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide] = null
  
  //def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = null
  
  //def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein] = null
  
  //def storeNewProteins( proteins: Seq[Protein] ): Array[Protein] = null
  
  override def storeRsPeptideMatches( rs: ResultSet ): Int = {
    
    /// Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val peptideMatches = rs.peptideMatches
    val scoringIdByScoreType = this.scoringIdByType
    
    // Retrieve primary db connection    
    val conn = this.msiDb1.connection
    
    // Create TMP table
    val tmpPepMatchTableName = "tmp_peptide_match_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpPepMatchTableName +"'..." )
    
    val stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TEMP TABLE "+tmpPepMatchTableName+" (LIKE peptide_match)")    
    
    // Bulk insert of peptide matches
    logger.info( "BULK insert of peptide matches" )
    
    val pepMatchTableCols = "charge, experimental_moz, score, rank, delta_moz, " +
                            " missed_cleavage, fragment_match_count, is_decoy, serialized_properties, " +
                            " peptide_id, ms_query_id, best_child_id, scoring_id, result_set_id"
    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY "+ tmpPepMatchTableName +" ( id, "+ pepMatchTableCols + " ) FROM STDIN" )
    
    // Iterate over peptide matches to store them
    for( peptideMatch <- peptideMatches ) {
      
      val peptide = peptideMatch.peptide
      val msQuery = peptideMatch.msQuery
      val scoreType = peptideMatch.scoreType
      val scoringId = scoringIdByScoreType.get(scoreType)
      if( scoringId == None ) throw new Exception("can't find a scoring id for the score type '"+scoreType+"'")
      val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) generate(peptideMatch.properties.get) else ""
      val bestChildId = peptideMatch.getBestChildId
      
      // Build a row containing peptide match values
      val pepMatchValues = List( peptideMatch.id,
                                 msQuery.charge,
                                 msQuery.moz,
                                 peptideMatch.score,
                                 peptideMatch.rank,
                                 peptideMatch.deltaMoz,
                                 peptideMatch.missedCleavage,
                                 peptideMatch.fragmentMatchesCount,
                                 BoolToSQLStr( peptideMatch.isDecoy ),
                                 pepMatchPropsAsJSON,
                                 peptide.id,
                                 peptideMatch.msQuery.id,
                                 if( bestChildId == 0 ) "" else bestChildId,
                                 scoringId.get,
                                 peptideMatch.resultSetId
                              )
      
      // Store peptide match
      val pepMatchBytes = this.encodeRecord( pepMatchValues )
      pgBulkLoader.writeToCopy( pepMatchBytes, 0, pepMatchBytes.length )  
      
    }
    
    // End of BULK copy
    val nbInsertedPepMatches = pgBulkLoader.endCopy()
    
    // Move TMP table content to MAIN table
    logger.info( "move TMP table "+ tmpPepMatchTableName +" into MAIN peptide_match table" )
    stmt.executeUpdate("INSERT into peptide_match ("+pepMatchTableCols+") "+
                       "SELECT "+pepMatchTableCols+" FROM "+tmpPepMatchTableName )
    
    // Retrieve generated peptide match ids
    val pepMatchIdByKey = msiDb1.getOrCreateTransaction.select(
                           "SELECT ms_query_id, peptide_id, id FROM peptide_match WHERE result_set_id = " + rsId ) { r => 
                             (r.nextInt.get +"%"+ r.nextInt.get -> r.nextInt.get)
                           } toMap
    
    // Iterate over peptide matches to update them
    peptideMatches.foreach { pepMatch => pepMatch.id = pepMatchIdByKey( pepMatch.msQuery.id + "%" + pepMatch.peptide.id ) }
    
    this._linkPeptideMatchesToChildren( peptideMatches )
    
    nbInsertedPepMatches.toInt
  }
  
  private def _linkPeptideMatchesToChildren( peptideMatches: Seq[PeptideMatch] ): Unit = {
    
    val pepMatchRelTableCols = "parent_peptide_match_id, child_peptide_match_id, parent_result_set_id"    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY peptide_match_relation ( "+ pepMatchRelTableCols + " ) FROM STDIN" )
    
    // Iterate over peptide matches to store them
    for( peptideMatch <- peptideMatches ) {
      if( peptideMatch.children != null && peptideMatch.children != None ) {
        for( pepMatchChild <- peptideMatch.children.get ) {
          
          // Build a row containing peptide_match_relation values
          val pepMatchRelationValues = List( peptideMatch.id,
                                             pepMatchChild.id,
                                             peptideMatch.resultSetId
                                           )
          
          // Store peptide match
          val pepMatchRelationBytes = this.encodeRecord( pepMatchRelationValues )
          pgBulkLoader.writeToCopy( pepMatchRelationBytes, 0, pepMatchRelationBytes.length )
        }
      }
    }
    
    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
    
  }
  
  //def storeRsProteinMatches( rs: ResultSet ): Int = 0
  
  override def storeRsSequenceMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches
    
    // Retrieve primary db connection    
    val conn = this.msiDb1.connection
    
    // Create TMP table
    val tmpSeqMatchTableName = "tmp_sequence_match_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpSeqMatchTableName +"'..." )
    
    val stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TEMP TABLE "+tmpSeqMatchTableName+" (LIKE sequence_match)")
    
    // Bulk insert of sequence matches
    logger.info( "BULK insert of sequence matches" )

    val seqMatchTableCols = "protein_match_id, peptide_id, start, stop, residue_before, residue_after, is_decoy, " +
                            "serialized_properties, best_peptide_match_id, bio_sequence_id, result_set_id"
    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY "+ tmpSeqMatchTableName +" ( "+ seqMatchTableCols + " ) FROM STDIN" )

    // Iterate over protein matches
    for ( proteinMatch <- proteinMatches ) {
      
      val proteinMatchId = proteinMatch.id
      val proteinId = proteinMatch.getProteinId
      
      for ( seqMatch <- proteinMatch.sequenceMatches ) {
        
        var seqMatchValues = List(  proteinMatchId,
                                    seqMatch.getPeptideId,
                                    seqMatch.start,
                                    seqMatch.end,
                                    seqMatch.residueBefore.toString(),
                                    seqMatch.residueAfter.toString(),
                                    BoolToSQLStr( isDecoy ),
                                    "" , //seqMatch.hasProperties ? encode_json( seqMatch.properties ) : undef,
                                    seqMatch.getBestPeptideMatchId,
                                    "", //proteinId
                                    seqMatch.resultSetId
                                 )
        
        // Store sequence match
        val seqMatchBytes = this.encodeRecord( seqMatchValues )
        pgBulkLoader.writeToCopy( seqMatchBytes, 0, seqMatchBytes.length )        
      }
    }
    
    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
    
    // Move TMP table content to MAIN table
    logger.info( "move TMP table "+ tmpSeqMatchTableName +" into MAIN sequence_match table" )
    stmt.executeUpdate("INSERT into sequence_match ("+seqMatchTableCols+") "+
                       "SELECT "+seqMatchTableCols+" FROM "+tmpSeqMatchTableName )
    
    nbInsertedRecords.toInt
  }
  
  /**
   * Replace empty strings by the '\N' character and convert the record to a byte array.
   * Note: by default '\N' means NULL value for the postgres COPY function
   */
  private def encodeRecord( record: List[Any] ): Array[Byte] = {
    val recordStrings = record map { _.toString() } map { str => if( isEmpty(str) ) "\\N" else str } 
    (recordStrings.mkString("\t") + "\n").getBytes("UTF-8")
  }
  
}