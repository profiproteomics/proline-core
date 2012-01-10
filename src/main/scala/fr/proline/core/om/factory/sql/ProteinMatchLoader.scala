package fr.proline.core.om.factory.sql

import net.noerd.prequel.DatabaseConfig
import fr.proline.core.om.helper.MsiDbHelper

class ProteinMatchLoader( val msiDb: DatabaseConfig ) {
  
  import _root_.fr.proline.core.om.msi.ProteinClasses._
  
  import scala.collection.mutable.ArrayBuffer

  def getProteinMatches( rsIds: Seq[Int] ): Array[ProteinMatch] = {
    
    // Retrieve score type map
    val scoreTypeById = new MsiDbHelper( msiDb ).getScoreTypeById
    
    // Execute SQL query to load protein match records
    //val seqMatchMapBuilder = scala.collection.immutable.Map.newBuilder[Int,SequenceMatch]
    var seqMatchColNames: Seq[String] = null
    val seqMatcheRecords = msiDb.transaction { tx =>       
      tx.select( "SELECT * FROM sequence_match WHERE result_set_id IN (" +
                 rsIds.mkString(",") +")" ) { r => 
        if( seqMatchColNames == null ) { seqMatchColNames = r.columnNames }
        seqMatchColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        
      }
    }
    
    //val seqMatchById = seqMatchMapBuilder.result()
    val seqMatchRecordsByProtMatchId = seqMatcheRecords.groupBy( _.get("protein_match_id").get.asInstanceOf[Int] )
    
    // Load and map sequence database ids of each protein match
    val seqDbIdsByProtMatchId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    
    msiDb.transaction { tx =>
      tx.select( "SELECT protein_match_id, seq_database_id FROM protein_match_seq_database_map" ) { r =>
        val( proteinMatchId, seqDatabaseId ) = (r.nextInt.get, r.nextInt.get)
        if( !seqDbIdsByProtMatchId.containsKey(proteinMatchId) ) {
          seqDbIdsByProtMatchId.put(proteinMatchId, new ArrayBuffer[Int](1) )
        }
        seqDbIdsByProtMatchId.get(proteinMatchId) += seqDatabaseId
        
        ()
      }
    }
    
    // Execute SQL query to load protein match records
    var protMatchColNames: Seq[String] = null
    val protMatches = msiDb.transaction { tx =>       
      tx.select( "SELECT * FROM protein_match WHERE result_set_id IN (" +
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
            
            val resBeforeStr = seqMatchRecord("residue_before").asInstanceOf[String]
            val resBeforeChar = if( resBeforeStr != null ) resBeforeStr.charAt(0) else '\0'
              
            val resAfterStr = seqMatchRecord("residue_before").asInstanceOf[String]
            val resAfterChar = if( resAfterStr != null ) resAfterStr.charAt(0) else '\0'
              
            val isDecoy = if( seqMatchRecord("is_decoy").asInstanceOf[String] == "true" ) true else false
            
            // TODO: load properties
            val seqMatch = new SequenceMatch(
                                    start = seqMatchRecord("start").asInstanceOf[Int],
                                    end = seqMatchRecord("stop").asInstanceOf[Int],
                                    residueBefore = resBeforeChar,
                                    residueAfter = resAfterChar,
                                    isDecoy = isDecoy,
                                    peptideId = seqMatchRecord("peptide_id").asInstanceOf[Int],
                                    bestPeptideMatchId = seqMatchRecord("best_peptide_match_id").asInstanceOf[Int],
                                    resultSetId = seqMatchRecord("result_set_id").asInstanceOf[Int]
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
        val isDecoy = if( protMatchRecord("is_decoy").asInstanceOf[String] == "true" ) true else false
        val bioSequenceId = if( protMatchRecord("protein_id") == null ) { 0 }       
                            else { protMatchRecord("protein_id").asInstanceOf[Int] }
        
        // TODO: load properties
        
        val protMatch = new ProteinMatch(
                              id = protMatchRecord("id").asInstanceOf[Int],
                              accession = protMatchRecord("accession").asInstanceOf[String],
                              description = protMatchRecord("description").asInstanceOf[String],
                              sequenceMatches = seqMatches,
                              isDecoy = isDecoy,
                              seqDatabaseIds = seqDatabaseIds,
                              bioSequenceId = bioSequenceId,
                              resultSetId = protMatchRecord("result_set_id").asInstanceOf[Int]
                            )
        
        if( protMatchRecord("score") != null ) {
          protMatch.score = protMatchRecord("score").asInstanceOf[Double].toFloat
          protMatch.scoreType = scoreTypeById(protMatchRecord("scoring_id").asInstanceOf[Int])
        }
        
        if( protMatchRecord("coverage") != null ) {
          protMatch.coverage = protMatchRecord("coverage").asInstanceOf[Double].toFloat
        }
        
        if( protMatchRecord("peptide_match_count") != null ) {
          protMatch.peptideMatchesCount = protMatchRecord("peptide_match_count").asInstanceOf[Int]
        }
        
        if( protMatchRecord("taxon_id") != null ) {
          protMatch.taxonId = protMatchRecord("taxon_id").asInstanceOf[Int]          
        }
        
        protMatch
      }
      
    }
    
    protMatches.toArray
    
  }
  
}

