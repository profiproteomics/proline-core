package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.generate

import fr.profi.jdbc.easy._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.dal.tables.msi.{MsiDbPeptideMatchTable,MsiDbProteinMatchTable,MsiDbSequenceMatchTable}
import fr.proline.core.om.storer.msi.IRsWriter
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi._

private[msi] class SQLiteRsWriter( val msiSqlHelper: SQLQueryHelper // Main DB connection                        
                                 ) extends IRsWriter {
  
  //val msiDbConnection: java.sql.Connection // Main MSI db connection
  //val msiDbDriver: DriverType
  //lazy val msiDb2: MsiDb = new MsiDb( msiDb1.config, maxVariableNumber = 10000 ) // Secondary MSI db connection
  
  val ezDBC = msiSqlHelper.ezDBC
  val scoringIdByType = new MsiDbHelper( ezDBC ).getScoringIdByType

  def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ): Map[String,Int] = {
    
    // Retrieve some vars
    val peptideMapBuilder = scala.collection.immutable.Map.newBuilder[String,Int]
    
    // Iterate over peptide sequences to retrieve their identifiers
    pepSequences.grouped(msiSqlHelper.ezDBC.getInExpressionCountLimit).foreach { tmpPepSeqs =>
      val quotedPepSeqs = tmpPepSeqs map { "'"+ _ + "'" }
      val sqlQuery = "SELECT id,sequence,ptm_string FROM peptide WHERE peptide.sequence IN ("+quotedPepSeqs.mkString(",")+")"
      ezDBC.selectAndProcess( sqlQuery ) { r =>
        
        val pepId = r.nextInt
        val pepSeq = r.nextString
        var ptmString = r.nextStringOrElse("")
        
        peptideMapBuilder += ( pepSeq + "%" + ptmString -> pepId )        
      }
    }
    
    peptideMapBuilder.result()

  }
  
  def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide] = {
    
    val newPeptides = new ArrayBuffer[Peptide](0)
    ezDBC.executePrepared( "INSERT INTO peptide VALUES (?,?,?,?,?)" ) { stmt =>
    
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
    
    ezDBC.executePrepared("INSERT INTO bio_sequence VALUES ("+"?," *8 +"?)") { stmt =>
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
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val peptideMatches = rs.peptideMatches
    val scoringIdByType = this.scoringIdByType
    
    val pepMatchInsertQuery = MsiDbPeptideMatchTable.mkInsertQuery{ (c,colsList) => 
                                colsList.filter( _ != c.id)
                              }
    
    ezDBC.executePrepared(pepMatchInsertQuery, true ) { stmt =>
      
      // Iterate over peptide matche to store them
      for( peptideMatch <- peptideMatches ) {
        
        val peptide = peptideMatch.peptide
        val msQuery = peptideMatch.msQuery
        val scoreType = peptideMatch.scoreType
        val scoringId = scoringIdByType.get(scoreType)
        assert( scoringId != None, "can't find a scoring id for the score type '"+scoreType+"'" )
        
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
                peptideMatch.isDecoy,
                pepMatchPropsAsJSON,
                peptideMatch.peptide.id,
                peptideMatch.msQuery.id,
                bestChildId,
                scoringId,
                rsId
                )
        
        // Update peptide match id
        peptideMatch.id = stmt.generatedInt
        
      }      
    }
    
    // Link peptide matches to their children
    ezDBC.executePrepared( "INSERT INTO peptide_match_relation VALUES (?,?,?)" ) { stmt =>
      for( peptideMatch <- peptideMatches )    
        if( peptideMatch.children != null && peptideMatch.children != None )
          for( pepMatchChild <- peptideMatch.children.get )
            stmt.executeWith( peptideMatch.id, pepMatchChild.id, rsId )
    }
    
    peptideMatches.length

  }
  
  def storeRsProteinMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches
    val scoringIdByScoreType = this.scoringIdByType
    
    val protMatchInsertQuery = MsiDbProteinMatchTable.mkInsertQuery { (c,colsList) => 
                                 colsList.filter( _ != c.id)
                               }
    
    logger.info( "protein matches are going to be inserted..." )
    ezDBC.executePrepared( protMatchInsertQuery, true ) { stmt =>
      
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
          proteinMatch.isLastBioSequence,
          Option(null),
          if( proteinMatch.taxonId > 0 ) Some(proteinMatch.taxonId) else Option(null),
          if( proteinMatch.getProteinId > 0 ) Some(proteinMatch.getProteinId) else Option(null),
          scoringId.get,
          rsId
        )
        
        // Update protein match id
        proteinMatch.id = stmt.generatedInt
      }
    }
    
    logger.info( "protein matches are going to be linked to seq databases..." )
    
    // Link protein matches to sequence databases
    ezDBC.executePrepared( "INSERT INTO protein_match_seq_database_map VALUES (?,?,?)" ) { stmt =>      
      for( proteinMatch <- proteinMatches )
        for( seqDbId <- proteinMatch.seqDatabaseIds )
          stmt.executeWith( proteinMatch.id, seqDbId, rsId )
    }
    
    proteinMatches.length
    
  }
  
  def storeRsSequenceMatches( rs: ResultSet ): Int = {
    
    // Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches
    
    var count = 0
    
    ezDBC.executePrepared( MsiDbSequenceMatchTable.mkInsertQuery ) { stmt =>
      
      // Iterate over protein matches
      for( proteinMatch <- proteinMatches ) {
        
        val proteinMatchId = proteinMatch.id
        
        for( seqMatch <- proteinMatch.sequenceMatches ) {
          
          count += stmt.executeWith(  proteinMatchId, 
                                      seqMatch.getPeptideId,
                                      seqMatch.start,
                                      seqMatch.end,
                                      seqMatch.residueBefore.toString,
                                      seqMatch.residueAfter.toString,
                                      seqMatch.isDecoy,
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