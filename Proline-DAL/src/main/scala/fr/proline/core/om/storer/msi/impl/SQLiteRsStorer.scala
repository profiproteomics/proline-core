package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.utils.sql.BoolToSQLStr
import fr.proline.core.dal.MsiDb
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
    
    // Retrieve some vars
    val msiDbConn = msiDb1.connection
    val msiDbTx = msiDb1.getOrCreateTransaction()
    //val stmt = msiDbConn.prepareStatement(  )
    
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
                            Some(null)
                           )
                           
          newPeptides += peptide
          
          /*
          ////// TODO: store and retrieve atom label id
          
          ////// Store peptide ptms
          val locatedPtms = peptide.ptms
          for( locatedPtm <- locatedPtms ) {
            val ptmDefinitionId = locatedPtm.definition.id
            croak "store_peptides_using_rdb: undefined ptm definition id" if !defined ptmDefinitionId
            
            val rdbPeptidePtm = new Pairs::Msi::RDBO::PeptidePtm(
                                        seq_position = locatedPtm.seqPosition,
                                        mono_mass = locatedPtm.monoMass,
                                        average_mass = locatedPtm.averageMass,
                                        peptide_id = rdbPeptide.id,
                                        ptm_specificity_id = ptmDefinitionId,
                                        db = rdb,
                                        )
            rdbPeptidePtm.save()
            }
          
          push( storedRdbPeptides, rdbPeptide )*/
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
                  Some(null),
                  protein.alphabet,
                  protein.sequence,
                  protein.length,
                  protein.mass,
                  protein.pi,
                  protein.crc64,
                  Some(null)
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
    
    // TODO: store peptide match properties
    msiDb1.getOrCreateTransaction().executeBatch(PeptideMatchTable.getInsertQuery(), true ) { stmt =>
      
      // Iterate over peptide matche to store them
      for( peptideMatch <- peptideMatches ) {
        
        val peptide = peptideMatch.peptide
        val msQuery = peptideMatch.msQuery
        val scoreType = peptideMatch.scoreType
        val scoringId = scoringIdByType.get(scoreType)
        if( scoringId == None ) {
          throw new Exception( "can't find a scoring id for the score type '"+scoreType+"'" )
        }
        
        stmt.executeWith( 
                Some(null),
                peptideMatch.msQuery.charge,
                peptideMatch.msQuery.moz,
                Some(null),
                Some(null),
                peptideMatch.score,
                peptideMatch.rank,
                peptideMatch.deltaMoz,
                peptideMatch.missedCleavage,
                peptideMatch.fragmentMatchesCount,
                BoolToSQLStr( peptideMatch.isDecoy ),
                Some(null),
                peptideMatch.peptide.id,
                peptideMatch.msQuery.id,
                peptideMatch.getBestChildId,
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
    
    msiDb1.getOrCreateTransaction().executeBatch(ProteinMatchTable.getInsertQuery(), true ) { stmt =>
    
      // Iterate over protein matches to store them
      for( proteinMatch <- proteinMatches ) {
        
        val scoreType = proteinMatch.scoreType
        val scoringId = scoringIdByScoreType.get(scoreType)
        if( scoringId == None ) {
          throw new Exception( "can't find a scoring id for the score type '"+scoreType+"'" )
        }
        
        // TODO: store protein_match properties
        stmt.executeWith(
          Some(null),
          proteinMatch.accession,
          proteinMatch.description,
          Option(proteinMatch.geneName),
          proteinMatch.score,
          proteinMatch.coverage,
          proteinMatch.sequenceMatches.length,
          proteinMatch.peptideMatchesCount,
          BoolToSQLStr( proteinMatch.isDecoy ),
          Option(null),
          proteinMatch.taxonId,
          proteinMatch.getProteinId,
          scoringId.get,
          rsId
        )
        
        // Update protein match id
        proteinMatch.id = this.msiDb1.extractGeneratedInt( stmt.wrapped )
      } 
    }
    
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
    
    msiDbTx.executeBatch( SequenceMatchTable.getInsertQuery ) { stmt =>
      
      // Iterate over protein matches
      for( proteinMatch <- proteinMatches ) {
        
        val proteinMatchId = proteinMatch.id
        val proteinId = proteinMatch.getProteinId
        
        for( seqMatch <- proteinMatch.sequenceMatches ) {
          
          count += stmt.executeWith(  proteinMatchId, 
                                      seqMatch.getPeptideId,
                                      seqMatch.start,
                                      seqMatch.end,
                                      seqMatch.residueBefore,
                                      seqMatch.residueAfter,
                                      BoolToSQLStr( seqMatch.isDecoy ),
                                      Some(null),
                                      seqMatch.getBestPeptideMatchId,
                                      proteinId,
                                      rsId
                                      )
        }
      }
      
    }
    
    count
    
  }

}