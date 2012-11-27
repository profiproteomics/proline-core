package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.generate
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbPeptideTable,MsiDbPeptideMatchTable,MsiDbPeptideMatchRelationTable,
                            MsiDbProteinMatchTable,MsiDbSequenceMatchTable}
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi._
import fr.proline.util.sql.{BoolToSQLStr,encodeRecordForPgCopy}

private[msi] class PgRsWriter( override val msiDb1: MsiDb // Main DB connection                        
                             ) extends SQLiteRsWriter( msiDb1 ) {
  
  val bulkCopyManager = new CopyManager( msiDb1.connection.asInstanceOf[BaseConnection] )
  
  private val peptideTableCols = MsiDbPeptideTable.getColumnsAsStrList().mkString(",")
  private val pepMatchTableCols = MsiDbPeptideMatchTable.getColumnsAsStrList().filter( _ != "id" ).mkString(",")
  private val pepMatchRelTableCols = MsiDbPeptideMatchRelationTable.getColumnsAsStrList().mkString(",")
  private val protMatchTableCols = MsiDbProteinMatchTable.getColumnsAsStrList().filter( _ != "id" ).mkString(",")
  private val seqMatchTableCols = MsiDbSequenceMatchTable.getColumnsAsStrList().mkString(",")
  
  //def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ):  Map[String,Int] = null
  // TODO: insert peptides into a TMP table
  
  override def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide] = {
    
    // Create a new transaction using secondary MsiDb connection
    this.msiDb2.newTransaction()
    
    // Instantiate a copy manager using secondary MsiDb connection
    val bulkCopyManager2 = new CopyManager( this.msiDb2.connection.asInstanceOf[BaseConnection] )
    
    // Create TMP table
    val tmpPeptideTableName = "tmp_peptide_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpPeptideTableName +"'..." )
    
    val stmt = this.msiDb2.connection.createStatement();
    stmt.executeUpdate("CREATE TEMP TABLE "+tmpPeptideTableName+" (LIKE peptide)")
    
    // Bulk insert of peptides
    logger.info( "BULK insert of peptides" )
    
    val pgBulkLoader = bulkCopyManager2.copyIn("COPY "+ tmpPeptideTableName +" ( "+ peptideTableCols + " ) FROM STDIN" )

    //val newPeptides = new ArrayBuffer[Peptide](0)
    
    // Iterate over peptides
    for ( peptide <- peptides ) {
      
      val ptmString = if( peptide.ptmString != null ) peptide.ptmString else ""              
      var peptideValues = List(  peptide.id,
                                 peptide.sequence,
                                 ptmString, 
                                 peptide.calculatedMass,
                                 ""
                               )
      
      // Store peptide
      val peptideBytes = encodeRecordForPgCopy( peptideValues )
      pgBulkLoader.writeToCopy( peptideBytes, 0, peptideBytes.length )        
      
    }
    
    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
    
    // Move TMP table content to MAIN table
    logger.info( "move TMP table "+ tmpPeptideTableName +" into MAIN peptide table" )
    stmt.executeUpdate("INSERT into peptide ("+peptideTableCols+") "+
                       "SELECT "+peptideTableCols+" FROM "+tmpPeptideTableName )
    
    // Commit transaction
    this.msiDb2.connection.commit()
                       
    peptides.toArray
  }
  
  //def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = null
  
  //def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein] = null
  
  //def storeNewProteins( proteins: Seq[Protein] ): Array[Protein] = null
  
  override def storeRsPeptideMatches( rs: ResultSet ): Int = {
    
    /// Retrieve some vars
    val rsId = rs.id
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
    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY "+ tmpPepMatchTableName +" ( id, "+ pepMatchTableCols + " ) FROM STDIN" )
    
    // Iterate over peptide matches to store them
    for( peptideMatch <- peptideMatches ) {
      
      val peptide = peptideMatch.peptide
      val msQuery = peptideMatch.msQuery
      val scoreType = peptideMatch.scoreType
      val scoringId = scoringIdByScoreType.get(scoreType)
      assert( scoringId != None, "can't find a scoring id for the score type '"+scoreType+"'" )
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
      val pepMatchBytes = encodeRecordForPgCopy( pepMatchValues )
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
          val pepMatchRelationBytes = encodeRecordForPgCopy( pepMatchRelationValues )
          pgBulkLoader.writeToCopy( pepMatchRelationBytes, 0, pepMatchRelationBytes.length )
        }
      }
    }
    
    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
    
  }
  
  override def storeRsProteinMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val rsId = rs.id
    val proteinMatches = rs.proteinMatches
    val scoringIdByScoreType = this.scoringIdByType
    
    // Retrieve primary db connection    
    val conn = this.msiDb1.connection
    
    // Create TMP table
    val tmpProtMatchTableName = "tmp_protein_match_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpProtMatchTableName +"'..." )
    
    val stmt = conn.createStatement()
    stmt.executeUpdate("CREATE TEMP TABLE "+tmpProtMatchTableName+" (LIKE protein_match)")    
    
    // Bulk insert of protein matches
    logger.info( "BULK insert of protein matches" )
    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY "+ tmpProtMatchTableName +" ( id, "+ protMatchTableCols + " ) FROM STDIN" )
    
    // Iterate over protein matches to store them
    for( proteinMatch <- proteinMatches ) {
      
      val scoreType = proteinMatch.scoreType
      val scoringId = scoringIdByScoreType.get(scoreType)
      if( scoringId == None ) throw new Exception("can't find a scoring id for the score type '"+scoreType+"'")
      //val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) generate(peptideMatch.properties.get) else ""
      
      // Build a row containing protein match values
      val protMatchValues = List( proteinMatch.id,
                                  proteinMatch.accession,
                                  proteinMatch.description,
                                  if(proteinMatch.geneName == null) "" else proteinMatch.geneName,
                                  proteinMatch.score,
                                  proteinMatch.coverage,
                                  proteinMatch.sequenceMatches.length,
                                  proteinMatch.peptideMatchesCount,
                                  BoolToSQLStr( proteinMatch.isDecoy ),
                                  BoolToSQLStr(proteinMatch.isLastBioSequence), 
                                  "",
                                  proteinMatch.taxonId,
                                  "",// proteinMatch.getProteinId
                                  scoringId.get,
                                  rsId
                              )
      
      // Store protein match
      val protMatchBytes = encodeRecordForPgCopy( protMatchValues )
      pgBulkLoader.writeToCopy( protMatchBytes, 0, protMatchBytes.length )  
      
    }
    
    // End of BULK copy
    val nbInsertedProtMatches = pgBulkLoader.endCopy()
    
    // Move TMP table content to MAIN table
    logger.info( "move TMP table "+ tmpProtMatchTableName +" into MAIN protein_match table" )
    stmt.executeUpdate("INSERT into protein_match ("+protMatchTableCols+") "+
                       "SELECT "+protMatchTableCols+" FROM "+tmpProtMatchTableName )
    
    // Retrieve generated protein match ids
    val protMatchIdByAc = msiDb1.getOrCreateTransaction.select(
                           "SELECT accession, id FROM protein_match WHERE result_set_id = " + rsId ) { r => 
                             (r.nextString.get -> r.nextInt.get)
                           } toMap
    
    // Iterate over protein matches to update them
    proteinMatches.foreach { protMatch => protMatch.id = protMatchIdByAc( protMatch.accession ) }
    
    nbInsertedProtMatches.toInt
  }
  
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
                                    seqMatch.resultSetId
                                 )
        
        // Store sequence match
        val seqMatchBytes = encodeRecordForPgCopy( seqMatchValues )
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
  

  
}