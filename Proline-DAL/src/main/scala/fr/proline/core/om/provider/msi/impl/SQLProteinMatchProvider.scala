package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.tables.msi.{MsiDbProteinMatchTable,MsiDbSequenceMatchTable}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.{ProteinMatch,SequenceMatch}
import fr.proline.core.om.model.msi.{ProteinMatchProperties,SequenceMatchProperties}
import fr.proline.core.om.provider.msi.IProteinMatchProvider
import fr.proline.repository.DatabaseContext

class SQLProteinMatchProvider( val msiDbCtx: DatabaseContext, val msiSqlExec: SQLQueryExecution ) { //extends IProteinMatchProvider
  
  val ProtMatchCols = MsiDbProteinMatchTable.columns
  val SeqMatchCols = MsiDbSequenceMatchTable.columns
  
  def getResultSetsProteinMatches( rsIds: Seq[Int] ): Array[ProteinMatch] = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.primitives.DoubleOrFloatAsFloat._
    import fr.proline.util.sql.StringOrBoolAsBool._
    
    // Retrieve score type map
    val scoreTypeById = new MsiDbHelper( msiSqlExec ).getScoringTypeById
    
    // Execute SQL query to load protein match records
    //val seqMatchMapBuilder = scala.collection.immutable.Map.newBuilder[Int,SequenceMatch]
    var seqMatchColNames: Seq[String] = null
    val seqMatcheRecords = msiSqlExec.select( "SELECT * FROM sequence_match WHERE result_set_id IN (" +
                                           rsIds.mkString(",") +")" ) { r => 
      if( seqMatchColNames == null ) { seqMatchColNames = r.columnNames }
      seqMatchColNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
      
    }
    
    //val seqMatchById = seqMatchMapBuilder.result()
    val seqMatchRecordsByProtMatchId = seqMatcheRecords.groupBy( _.get(SeqMatchCols.PROTEIN_MATCH_ID).get.asInstanceOf[Int] )
    
    // Load and map sequence database ids of each protein match
    val seqDbIdsByProtMatchId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    
    msiSqlExec.selectAndProcess( "SELECT protein_match_id, seq_database_id FROM protein_match_seq_database_map" ) { r =>
      val( proteinMatchId, seqDatabaseId ) = (r.nextInt, r.nextInt)
      if( !seqDbIdsByProtMatchId.containsKey(proteinMatchId) ) {
        seqDbIdsByProtMatchId.put(proteinMatchId, new ArrayBuffer[Int](1) )
      }
      seqDbIdsByProtMatchId.get(proteinMatchId) += seqDatabaseId
      
    }
    
    // Execute SQL query to load protein match records
    var protMatchColNames: Seq[String] = null
    val protMatches = msiSqlExec.select( "SELECT * FROM protein_match WHERE result_set_id IN (" +
                 rsIds.mkString(",") +")" ) { r =>
              
        if( protMatchColNames == null ) { protMatchColNames = r.columnNames }
        val protMatchRecord = protMatchColNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
        
        val protMatchId: Int = protMatchRecord(ProtMatchCols.ID).asInstanceOf[AnyVal]
        
        var seqMatches: Array[SequenceMatch] = null
        
        var seqMatchRecordsOption = seqMatchRecordsByProtMatchId.get(protMatchId)
        if( seqMatchRecordsOption != None ) {
          
          var seqMatchRecords = seqMatchRecordsOption.get
          
          var seqMatchesBuffer = new ArrayBuffer[SequenceMatch](seqMatchRecords.length)
          for( seqMatchRecord <- seqMatchRecords ) {
            
            // Retrieve sequence match attributes
            val resBeforeStr = seqMatchRecord(SeqMatchCols.RESIDUE_BEFORE).asInstanceOf[String]
            val resBeforeChar = if( resBeforeStr != null ) resBeforeStr.charAt(0) else '\0'
              
            val resAfterStr = seqMatchRecord(SeqMatchCols.RESIDUE_AFTER).asInstanceOf[String]
            val resAfterChar = if( resAfterStr != null ) resAfterStr.charAt(0) else '\0'


            // Decode JSON properties
            val propertiesAsJSON = seqMatchRecord(SeqMatchCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
            var properties = Option.empty[SequenceMatchProperties]
            if( propertiesAsJSON != null ) {
              properties = Some( parse[SequenceMatchProperties](propertiesAsJSON) )
            }
            
            // Build sequence match
            val seqMatch = new SequenceMatch(
                                start = seqMatchRecord(SeqMatchCols.START).asInstanceOf[Int],
                                end = seqMatchRecord(SeqMatchCols.STOP).asInstanceOf[Int],
                                residueBefore = resBeforeChar,
                                residueAfter = resAfterChar,
                                isDecoy = seqMatchRecord(SeqMatchCols.IS_DECOY),
                                peptideId = seqMatchRecord(SeqMatchCols.PEPTIDE_ID).asInstanceOf[Int],
                                bestPeptideMatchId = seqMatchRecord(SeqMatchCols.BEST_PEPTIDE_MATCH_ID).asInstanceOf[Int],
                                resultSetId = seqMatchRecord(SeqMatchCols.RESULT_SET_ID).asInstanceOf[Int],
                                properties = properties
                              )
            
            seqMatchesBuffer += seqMatch
            
          }
          
          seqMatches = seqMatchesBuffer.toArray
          
        }
        
        // Retrieve sequence database ids
        var seqDatabaseIds: Array[Int] = null
        val seqDbIdBuffer = seqDbIdsByProtMatchId.get(protMatchId)
        if( seqDbIdBuffer != null ) {
          seqDatabaseIds = seqDbIdBuffer.toArray
        }
        
        // Build protein match object
        var bioSequenceId = if( protMatchRecord(ProtMatchCols.BIO_SEQUENCE_ID) == null ) { 0 }       
                            else { protMatchRecord(ProtMatchCols.BIO_SEQUENCE_ID).asInstanceOf[Int] }
        
        // Decode JSON properties
        val propertiesAsJSON = protMatchRecord(ProtMatchCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
        var properties = Option.empty[ProteinMatchProperties]
        if( propertiesAsJSON != null ) {
          properties = Some( parse[ProteinMatchProperties](propertiesAsJSON) )
        }
        
        val description = protMatchRecord(ProtMatchCols.DESCRIPTION)
        
        val protMatch = new ProteinMatch(
                              id = protMatchId,
                              accession = protMatchRecord(ProtMatchCols.ACCESSION).asInstanceOf[String],
                              description = if( description == null ) "" else description.asInstanceOf[String],
                              geneName = protMatchRecord(ProtMatchCols.GENE_NAME).asInstanceOf[String],
                              sequenceMatches = seqMatches,
                              isDecoy = protMatchRecord(ProtMatchCols.IS_DECOY),
                              isLastBioSequence = protMatchRecord(ProtMatchCols.IS_LAST_BIO_SEQUENCE),
                              seqDatabaseIds = seqDatabaseIds,
                              proteinId = bioSequenceId,
                              resultSetId = protMatchRecord(ProtMatchCols.RESULT_SET_ID).asInstanceOf[Int],
                              properties = properties
                            )
        
        if( protMatchRecord(ProtMatchCols.SCORE) != null ) {
          protMatch.score = protMatchRecord(ProtMatchCols.SCORE).asInstanceOf[AnyVal]
          protMatch.scoreType = scoreTypeById(protMatchRecord(ProtMatchCols.SCORING_ID).asInstanceOf[Int])
        }
        
        if( protMatchRecord(ProtMatchCols.COVERAGE) != null ) {
          protMatch.coverage = protMatchRecord(ProtMatchCols.COVERAGE).asInstanceOf[AnyVal]
        }
        
        if( protMatchRecord(ProtMatchCols.PEPTIDE_MATCH_COUNT) != null ) {
          protMatch.peptideMatchesCount = protMatchRecord(ProtMatchCols.PEPTIDE_MATCH_COUNT).asInstanceOf[Int]
        }
        
        if( protMatchRecord(ProtMatchCols.TAXON_ID) != null ) {
          protMatch.taxonId = protMatchRecord(ProtMatchCols.TAXON_ID).asInstanceOf[Int]          
        }
        
        protMatch
      }
    
    protMatches.toArray
    
  }
  
  // TODO: retrieve only validated protein matches
  def getResultSummariesProteinMatches( rsmIds: Seq[Int] ): Array[ProteinMatch] = {
    
    val rsIds = msiSqlExec.selectInts(
                  "SELECT result_set_id FROM result_summary WHERE id IN (" +rsmIds.mkString(",") +")"
                 )
    this.getResultSetsProteinMatches( rsIds )
  }
  
}

