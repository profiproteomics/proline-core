package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.generate
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._

import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.utils.sql.BoolToSQLStr
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbPeptideMatchTable,MsiDbProteinMatchTable,MsiDbSequenceMatchTable}
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi._

private[msi] class SQLiteRsStorer( val msiDb1: MsiDb // Main DB connection                        
                                 ) extends IRsStorer {

  def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ): Map[String,Int] = {
    
    // Retrieve some vars
    val msiDbTx = msiDb1.getOrCreateTransaction
    val peptideMapBuilder = scala.collection.immutable.Map.newBuilder[String,Int]
    
    // Iterate over peptide sequences to retrieve their identifiers
    pepSequences.grouped(msiDb1.maxVariableNumber).foreach { tmpPepSeqs =>
      val quotedPepSeqs = tmpPepSeqs map { "'"+ _ + "'" }
      val sqlQuery = "SELECT id,sequence,ptm_string FROM peptide WHERE peptide.sequence IN ("+quotedPepSeqs.mkString(",")+")"
      msiDbTx.selectAndProcess( sqlQuery ) { r =>
        
        val pepId = r.nextInt.get
        val pepSeq = r.nextString.get
        var ptmString = r.nextString.getOrElse("")
        
        peptideMapBuilder += ( pepSeq + "%" + ptmString -> pepId )        
      }
    }
    
    peptideMapBuilder.result()

  }
  
  def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide] = {
    
    // Create a transaction using secondary MsiDb connection
    val msiDbTx = this.msiDb1.getOrCreateTransaction()
    
    val newPeptides = new ArrayBuffer[Peptide](0)
    msiDbTx.executeBatch( "INSERT INTO peptide VALUES (?,?,?,?,?)" ) { stmt =>
    
      // Iterate over the array of peptides to store them in the MSI-DB
      for( peptide <- peptides ) {
        
        // Store only peptides which don't exist in the MSI-DB  
        if( ! peptideByUniqueKey.contains( peptide.uniqueKey ) ) {
          
          stmt.executeWith( peptide.id,
                            peptide.sequence,
                            Option(peptide.ptmString), 
                            peptide.calculatedMass,
                            Option(null)
                           )
                           
          newPeptides += peptide
          
        }
      }
    
    }
    
    newPeptides.toArray

  }
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = null
  
  def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein] = {
    new Array[Protein](0)
  }
  
  def storeNewProteins( proteins: Seq[Protein] ): Array[Protein] = {
    
    val newProteins = new ArrayBuffer[Protein](0)
    
    msiDb1.getOrCreateTransaction().executeBatch("INSERT INTO bio_sequence VALUES ("+"?," *8 +"?)") { stmt =>
      for( protein <- proteins ) {
        
        // Store only proteins which don't exist in the MSI-DB  
        if( ! this.proteinBySequence.contains(protein.sequence) ) {
          
          // Store new protein
          stmt.executeWith(
                  protein.id,
                  Option(null),
                  protein.alphabet,
                  protein.sequence,
                  protein.length,
                  protein.mass,
                  protein.pi,
                  protein.crc64,
                  Option(null)
                )
          
          newProteins += protein
          
        }
      }
    }

    
    newProteins.toArray

  }
  
  def storeRsPeptideMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val msiDbConn = msiDb1.connection
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val peptideMatches = rs.peptideMatches
    val scoringIdByType = this.scoringIdByType
    
    val pepMatchColsList = MsiDbPeptideMatchTable.getColumnsAsStrList().filter { _ != "id" }
    val pepMatchInsertQuery = MsiDbPeptideMatchTable.makeInsertQuery( pepMatchColsList )
    
    msiDb1.getOrCreateTransaction().executeBatch(pepMatchInsertQuery, true ) { stmt =>
      
      // Iterate over peptide matche to store them
      for( peptideMatch <- peptideMatches ) {
        
        val peptide = peptideMatch.peptide
        val msQuery = peptideMatch.msQuery
        val scoreType = peptideMatch.scoreType
        val scoringId = scoringIdByType.get(scoreType)
        if( scoringId == None ) {
          throw new Exception( "can't find a scoring id for the score type '"+scoreType+"'" )
        }
        val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) Some(generate(peptideMatch.properties.get)) else None
        val bestChildId = if( peptideMatch.getBestChildId == 0 ) Option.empty[Int] else Some(peptideMatch.getBestChildId)
        
        stmt.executeWith(
                peptideMatch.msQuery.charge,
                peptideMatch.msQuery.moz,
                peptideMatch.score,
                peptideMatch.rank,
                peptideMatch.deltaMoz,
                peptideMatch.missedCleavage,
                peptideMatch.fragmentMatchesCount,
                BoolToSQLStr( peptideMatch.isDecoy ),
                pepMatchPropsAsJSON,
                peptideMatch.peptide.id,
                peptideMatch.msQuery.id,
                bestChildId,
                scoringId,
                rsId
                )
        
        // Update peptide match id
        peptideMatch.id = this.msiDb1.extractGeneratedInt( stmt.wrapped )
        
      }      
    }
    
    // Link peptide matches to their children
    msiDb1.getOrCreateTransaction.executeBatch( "INSERT INTO peptide_match_relation VALUES (?,?,?)" ) { stmt =>
      for( peptideMatch <- peptideMatches )    
        if( peptideMatch.children != null && peptideMatch.children != None )
          for( pepMatchChild <- peptideMatch.children.get )
            stmt.executeWith( peptideMatch.id, pepMatchChild.id, rsId )
    }
    
    peptideMatches.length

  }
  
  def storeRsProteinMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val msiDbConn = msiDb1.connection
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches
    val scoringIdByScoreType = this.scoringIdByType
    
    val protMatchColsList = MsiDbProteinMatchTable.getColumnsAsStrList().filter { _ != "id" }
    val protMatchInsertQuery = MsiDbProteinMatchTable.makeInsertQuery( protMatchColsList )
    
    logger.info( "protein matches are going to be inserted..." )
    msiDb1.getOrCreateTransaction().executeBatch( protMatchInsertQuery, true ) { stmt =>
      
      // Iterate over protein matches to store them
      for( proteinMatch <- proteinMatches ) {
        
        val scoreType = proteinMatch.scoreType
        val scoringId = scoringIdByScoreType.get(scoreType)
        if( scoringId == None ) {
          throw new Exception( "can't find a scoring id for the score type '"+scoreType+"'" )
        }
        
        // TODO: store protein_match properties
        stmt.executeWith(
          proteinMatch.accession,
          proteinMatch.description,
          Option(proteinMatch.geneName),
          proteinMatch.score,
          proteinMatch.coverage,
          proteinMatch.sequenceMatches.length,
          proteinMatch.peptideMatchesCount,
          proteinMatch.isDecoy, // BoolToSQLStr( proteinMatch.isDecoy )
          false,
          Option(null),
          proteinMatch.taxonId,
          1, // proteinMatch.getProteinId
          scoringId.get,
          rsId
        )
        
        // Update protein match id
        proteinMatch.id = this.msiDb1.extractGeneratedInt( stmt.wrapped )
      }
    }
    
    logger.info( "protein matches are going to be linked to seq databases..." )
    
    // Link protein matches to sequence databases
    msiDb1.getOrCreateTransaction.executeBatch( "INSERT INTO protein_match_seq_database_map VALUES (?,?,?)" ) { stmt =>      
      for( proteinMatch <- proteinMatches )
        for( seqDbId <- proteinMatch.seqDatabaseIds )
          stmt.executeWith( proteinMatch.id, seqDbId, rsId )
    }
    
    proteinMatches.length
    
  }
  
  def storeRsSequenceMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val msiDbTx = msiDb1.getOrCreateTransaction
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches
    
    var count = 0
    
    msiDbTx.executeBatch( MsiDbSequenceMatchTable.makeInsertQuery ) { stmt =>
      
      // Iterate over protein matches
      for( proteinMatch <- proteinMatches ) {
        
        val proteinMatchId = proteinMatch.id
        
        for( seqMatch <- proteinMatch.sequenceMatches ) {
          
          count += stmt.executeWith(  proteinMatchId, 
                                      seqMatch.getPeptideId,
                                      seqMatch.start,
                                      seqMatch.end,
                                      seqMatch.residueBefore,
                                      seqMatch.residueAfter,
                                      BoolToSQLStr( seqMatch.isDecoy ),
                                      Option(null),
                                      seqMatch.getBestPeptideMatchId,
                                      rsId
                                      )
          
        }
      }
      
    }
    
    count
    
  }

}