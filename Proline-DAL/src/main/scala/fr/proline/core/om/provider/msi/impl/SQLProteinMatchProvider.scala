package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse
import fr.proline.core.dal.{MsiDb,MsiDbProteinMatchTable,MsiDbSequenceMatchTable}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.utils.sql.SQLStrToBool
import fr.proline.core.om.model.msi.{ProteinMatch,SequenceMatch}
import fr.proline.core.om.model.msi.{ProteinMatchProperties,SequenceMatchProperties}
import fr.proline.core.om.provider.msi.IProteinMatchProvider

class SQLProteinMatchProvider( val msiDb: MsiDb ) { //extends IProteinMatchProvider
  
  val ProtMatchCols = MsiDbProteinMatchTable.columns
  val SeqMatchCols = MsiDbSequenceMatchTable.columns
  
  def getResultSetsProteinMatches( rsIds: Seq[Int] ): Array[ProteinMatch] = {
    
    val msiDbTx = msiDb.getOrCreateTransaction()
    
    // Retrieve score type map
    val scoreTypeById = new MsiDbHelper( msiDb ).getScoringTypeById
    
    // Execute SQL query to load protein match records
    //val seqMatchMapBuilder = scala.collection.immutable.Map.newBuilder[Int,SequenceMatch]
    var seqMatchColNames: Seq[String] = null
    val seqMatcheRecords = msiDbTx.select( "SELECT * FROM sequence_match WHERE result_set_id IN (" +
                                           rsIds.mkString(",") +")" ) { r => 
      if( seqMatchColNames == null ) { seqMatchColNames = r.columnNames }
      seqMatchColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      
    }
    
    //val seqMatchById = seqMatchMapBuilder.result()
    val seqMatchRecordsByProtMatchId = seqMatcheRecords.groupBy( _.get(SeqMatchCols.proteinMatchId).get.asInstanceOf[Int] )
    
    // Load and map sequence database ids of each protein match
    val seqDbIdsByProtMatchId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    
    msiDbTx.selectAndProcess( "SELECT protein_match_id, seq_database_id FROM protein_match_seq_database_map" ) { r =>
      val( proteinMatchId, seqDatabaseId ) = (r.nextInt.get, r.nextInt.get)
      if( !seqDbIdsByProtMatchId.containsKey(proteinMatchId) ) {
        seqDbIdsByProtMatchId.put(proteinMatchId, new ArrayBuffer[Int](1) )
      }
      seqDbIdsByProtMatchId.get(proteinMatchId) += seqDatabaseId
      
    }
    
    // Execute SQL query to load protein match records
    var protMatchColNames: Seq[String] = null
    val protMatches = msiDbTx.select( "SELECT * FROM protein_match WHERE result_set_id IN (" +
                 rsIds.mkString(",") +")" ) { r =>
              
        if( protMatchColNames == null ) { protMatchColNames = r.columnNames }
        val protMatchRecord = protMatchColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        
        val protMatchId = protMatchRecord("id").asInstanceOf[Int]
        
        var seqMatches: Array[SequenceMatch] = null
        
        var seqMatchRecordsOption = seqMatchRecordsByProtMatchId.get(protMatchId)
        if( seqMatchRecordsOption != None ) {
          
          var seqMatchRecords = seqMatchRecordsOption.get
          
          var seqMatchesBuffer = new ArrayBuffer[SequenceMatch](seqMatchRecords.length)
          for( seqMatchRecord <- seqMatchRecords ) {
            
            // Retrieve sequence match attributes
            val resBeforeStr = seqMatchRecord(SeqMatchCols.residueBefore).asInstanceOf[String]
            val resBeforeChar = if( resBeforeStr != null ) resBeforeStr.charAt(0) else '\0'
              
            val resAfterStr = seqMatchRecord(SeqMatchCols.residueAfter).asInstanceOf[String]
            val resAfterChar = if( resAfterStr != null ) resAfterStr.charAt(0) else '\0'
              
            val isDecoy = SQLStrToBool( seqMatchRecord(SeqMatchCols.isDecoy).asInstanceOf[String] )
      
            // Decode JSON properties
            val propertiesAsJSON = seqMatchRecord(SeqMatchCols.serializedProperties).asInstanceOf[String]
            var properties = Option.empty[SequenceMatchProperties]
            if( propertiesAsJSON != null ) {
              properties = Some( parse[SequenceMatchProperties](propertiesAsJSON) )
            }
            
            // Build sequence match
            val seqMatch = new SequenceMatch(
                                start = seqMatchRecord(SeqMatchCols.start).asInstanceOf[Int],
                                end = seqMatchRecord(SeqMatchCols.stop).asInstanceOf[Int],
                                residueBefore = resBeforeChar,
                                residueAfter = resAfterChar,
                                isDecoy = isDecoy,
                                peptideId = seqMatchRecord(SeqMatchCols.peptideId).asInstanceOf[Int],
                                bestPeptideMatchId = seqMatchRecord(SeqMatchCols.bestPeptideMatchId).asInstanceOf[Int],
                                resultSetId = seqMatchRecord(SeqMatchCols.resultSetId).asInstanceOf[Int],
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
        val isDecoy = SQLStrToBool( protMatchRecord(ProtMatchCols.isDecoy).asInstanceOf[String] )
        var bioSequenceId = if( protMatchRecord(ProtMatchCols.bioSequenceId) == null ) { 0 }       
                            else { protMatchRecord(ProtMatchCols.bioSequenceId).asInstanceOf[Int] }
        
        // Decode JSON properties
        val propertiesAsJSON = protMatchRecord(ProtMatchCols.serializedProperties).asInstanceOf[String]
        var properties = Option.empty[ProteinMatchProperties]
        if( propertiesAsJSON != null ) {
          properties = Some( parse[ProteinMatchProperties](propertiesAsJSON) )
        }
        
        val protMatch = new ProteinMatch(
                              id = protMatchRecord(ProtMatchCols.id).asInstanceOf[Int],
                              accession = protMatchRecord(ProtMatchCols.accession).asInstanceOf[String],
                              description = protMatchRecord(ProtMatchCols.description).asInstanceOf[String],
                              geneName = protMatchRecord(ProtMatchCols.geneName).asInstanceOf[String],
                              sequenceMatches = seqMatches,
                              isDecoy = isDecoy,
                              seqDatabaseIds = seqDatabaseIds,
                              proteinId = bioSequenceId,
                              resultSetId = protMatchRecord(ProtMatchCols.resultSetId).asInstanceOf[Int],
                              properties = properties
                            )
        
        if( protMatchRecord(ProtMatchCols.score) != null ) {
          protMatch.score = protMatchRecord(ProtMatchCols.score).asInstanceOf[Double].toFloat
          protMatch.scoreType = scoreTypeById(protMatchRecord(ProtMatchCols.scoringId).asInstanceOf[Int])
        }
        
        if( protMatchRecord(ProtMatchCols.coverage) != null ) {
          protMatch.coverage = protMatchRecord(ProtMatchCols.coverage).asInstanceOf[Double].toFloat
        }
        
        if( protMatchRecord(ProtMatchCols.peptideMatchCount) != null ) {
          protMatch.peptideMatchesCount = protMatchRecord(ProtMatchCols.peptideMatchCount).asInstanceOf[Int]
        }
        
        if( protMatchRecord(ProtMatchCols.taxonId) != null ) {
          protMatch.taxonId = protMatchRecord(ProtMatchCols.taxonId).asInstanceOf[Int]          
        }
        
        protMatch
      }
    
    protMatches.toArray
    
  }
  
}

