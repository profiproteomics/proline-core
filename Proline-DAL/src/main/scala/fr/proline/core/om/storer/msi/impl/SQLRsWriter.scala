package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.profi.jdbc.easy._
import fr.profi.util.serialization._
import fr.proline.core.dal._
import fr.proline.core.dal.tables._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.{MsiDbObjectTreeTable,MsiDbPeptideMatchTable,MsiDbProteinMatchTable,MsiDbSequenceMatchTable}
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbPeptideTable
import fr.proline.core.dal.tables.msi.MsiDbBioSequenceTable
import fr.proline.core.dal.tables.msi.MsiDbPeaklistRelationTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideReadablePtmStringTable
import fr.proline.core.dal.tables.msi.MsiDbProteinMatchSeqDatabaseMapTable
import fr.proline.util.StringUtils
import fr.proline.util.primitives._

private[core] object SQLRsWriter extends AbstractSQLRsWriter

abstract class AbstractSQLRsWriter() extends IRsWriter {
  
  val objTreeInsertQuery = MsiDbObjectTreeTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID) )
  object CustomSerializer extends ProfiJsonSerialization with CustomDoubleJacksonSerializer

  def fetchExistingPeptidesIdByUniqueKey(pepSequences: Seq[String], msiDbCtx: DatabaseConnectionContext): Map[String, Long] = {
    
    val peptideMapBuilder = scala.collection.immutable.Map.newBuilder[String, Long]

    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      // Iterate over peptide sequences to retrieve their identifiers
      pepSequences.grouped(msiEzDBC.getInExpressionCountLimit).foreach { tmpPepSeqs =>
        
        val quotedPepSeqs = tmpPepSeqs map { "'" + _ + "'" }        
        val sqlQuery = new SelectQueryBuilder1( MsiDbPeptideTable ).mkSelectQuery( (t,c) =>
          List(t.ID,t.SEQUENCE,t.PTM_STRING) ->
          "WHERE "~ t.SEQUENCE ~" IN("~ quotedPepSeqs.mkString(",") ~")"
        )
        
        msiEzDBC.selectAndProcess(sqlQuery) { r =>
  
          val pepId = toLong(r.nextAny)
          val pepSeq = r.nextString
          var ptmString = r.nextStringOrElse("")
  
          peptideMapBuilder += (pepSeq + "%" + ptmString -> pepId)
        }
      }
    })

    peptideMapBuilder.result()
  }

  def insertNewPeptides(peptides: Seq[Peptide], peptideByUniqueKey: HashMap[String,Peptide], msiDbCtx: DatabaseConnectionContext): Unit = {
    
    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      msiEzDBC.executePrepared(MsiDbPeptideTable.mkInsertQuery()) { stmt =>
  
        // Iterate over the array of peptides to store them in the MSI-DB
        for (peptide <- peptides) {
  
          // Store only peptides which don't exist in the MSI-DB  
          if (!peptideByUniqueKey.contains(peptide.uniqueKey)) {
  
            stmt.executeWith(
              peptide.id,
              peptide.sequence,
              Option(peptide.ptmString),
              peptide.calculatedMass,
              peptide.properties.map(ProfiJson.serialize(_))
            )
  
          }
        }
      }
    })

  }

  def fetchProteinIdentifiers(accessions: Seq[String]): Array[Any] = null

  def fetchExistingProteins(protCRCs: Seq[String]): Array[Protein] = {
    new Array[Protein](0)
  }

  def insertNewProteins(proteins: Seq[Protein], proteinBySequence: HashMap[String,Protein], msiDbCtx: DatabaseConnectionContext): Array[Protein] = {

    val newProteins = new ArrayBuffer[Protein](0)

    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      msiEzDBC.executePrepared(MsiDbBioSequenceTable.mkInsertQuery()) { stmt =>
        for (protein <- proteins) {
  
          // Store only proteins which don't exist in the MSI-DB  
          if (!proteinBySequence.contains(protein.sequence)) {
  
            // Store new protein
            stmt.executeWith(
              protein.id,
              protein.alphabet,
              protein.sequence,
              protein.length,
              protein.mass,
              protein.pi,
              protein.crc64,
              protein.properties.map(ProfiJson.serialize(_))
            )
  
            newProteins += protein
  
          }
        }
      }
    })

    newProteins.toArray

  }
  
  def insertRsReadablePtmStrings(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {
    
    // Define some vars
    val rsId = rs.id
    var count = 0
    
    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val ptmStringInsertQuery = MsiDbPeptideReadablePtmStringTable.mkInsertQuery
      
      msiEzDBC.executePrepared( ptmStringInsertQuery ) { stmt =>
        for ( peptide <- rs.peptides; if StringUtils.isNotEmpty(peptide.readablePtmString) ) {
          count += stmt.executeWith(
            peptide.id,
            rsId,
            peptide.readablePtmString
          )
        }
      }
    })

    count
  }

  def insertRsPeptideMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {
    
    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val scoringIdByType = new MsiDbHelper(msiDbCtx).getScoringIdByType
      
      // Retrieve some vars
      val rsId = rs.id
      val isDecoy = rs.isDecoy
      val peptideMatches = rs.peptideMatches
  
      val pepMatchInsertQuery = MsiDbPeptideMatchTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared(pepMatchInsertQuery, true ) { stmt =>
        
        // Iterate over peptide matche to store them
        for (peptideMatch <- peptideMatches) {
          
          val scoreType = peptideMatch.scoreType
          val scoringId = scoringIdByType.get(scoreType)
          require(scoringId.isDefined, "can't find a scoring id for the score type '" + scoreType + "'")
  
          val msQuery = peptideMatch.msQuery
          val bestChildId = peptideMatch.getBestChildId
          var pmCharge = msQuery.charge
          if(peptideMatch.properties.isDefined && peptideMatch.properties.get.getOmssaProperties.isDefined) {
            pmCharge = peptideMatch.properties.get.getOmssaProperties.get.getCorrectedCharge
          }
  
          stmt.executeWith(
            pmCharge,
            msQuery.moz,
            peptideMatch.score,
            peptideMatch.rank,
            peptideMatch.deltaMoz,
            peptideMatch.missedCleavage,
            peptideMatch.fragmentMatchesCount,
            peptideMatch.isDecoy,
            peptideMatch.properties.map(ProfiJson.serialize(_)),
            peptideMatch.peptide.id,
            msQuery.id,
            if (bestChildId == 0) Option.empty[Long] else Some(bestChildId),
            scoringId,
            rsId
          )
  
          // Update peptide match id
          peptideMatch.id = stmt.generatedLong
        }
      }
  
      // Link peptide matches to their children
      msiEzDBC.executePrepared(MsiDbPeaklistRelationTable.mkInsertQuery()) { stmt =>
        for (peptideMatch <- peptideMatches)
          if (peptideMatch.children != null && peptideMatch.children.isDefined)
            for (pepMatchChild <- peptideMatch.children.get)
              stmt.executeWith(peptideMatch.id, pepMatchChild.id, rsId)
      }
      
      peptideMatches.length
    })

  }
  
  def insertRsSpectrumMatches(rs: ResultSet, rf: IResultFile, msiDbCtx: DatabaseConnectionContext): Int = {
    
    import fr.proline.core.utils.serialization.ProlineJson
    //import org.msgpack.ScalaMessagePack
    
    // TODO: create a schema name enumeration
    val schemaName = "peptide_match.spectrum_match"
    val pepMatchIdByKey = Map() ++ rs.peptideMatches.map( pm => Pair(pm.msQuery.initialId,pm.rank) -> pm.id )    
    var spectrumCount = 0
    
    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>

      val spectrumMatchKeyById = new HashMap[Long,Pair[Int,Int]]()
      
      // Store spectrum matches
      msiEzDBC.executePrepared(objTreeInsertQuery, true ) { stmt =>
        rf.eachSpectrumMatch(rs.isDecoy, { spectrumMatch =>

          stmt.executeWith(
            Option(null),//ScalaMessagePack.write(spectrumMatch),
            CustomSerializer.serialize(spectrumMatch),
            Option.empty[String],
            schemaName
          )
          
          spectrumMatchKeyById += stmt.generatedLong -> Pair(spectrumMatch.msQueryInitialId,spectrumMatch.peptideMatchRank)
          
          spectrumCount += 1
        })
      }
      
      // Link spectrum matches to peptide matches
      msiEzDBC.executePrepared("INSERT INTO peptide_match_object_tree_map VALUES (?,?,?)" ) { stmt =>
        
        for( (spectrumMatchId,spectrumMatchKey) <- spectrumMatchKeyById ) {
          val pepMatchId = pepMatchIdByKey( spectrumMatchKey )
          
          stmt.executeWith(
            pepMatchId,
            spectrumMatchId,
            schemaName
          )
        }
      }
      
    })
    
    spectrumCount

  }

  def insertRsProteinMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {

    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val scoringIdByType = new MsiDbHelper(msiDbCtx).getScoringIdByType
      
      // Retrieve some vars
      val rsId = rs.id
      val isDecoy = rs.isDecoy
      val proteinMatches = rs.proteinMatches
  
      val protMatchInsertQuery = MsiDbProteinMatchTable.mkInsertQuery { (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      logger.info(proteinMatches.length + " ProteinMatches are going to be inserted..." )
      
      msiEzDBC.executePrepared( protMatchInsertQuery, true ) { stmt =>
        
        // Iterate over protein matches to store them
        for (proteinMatch <- proteinMatches) {
  
          val scoreType = proteinMatch.scoreType
          val scoringId = scoringIdByType.get(scoreType)
          require(scoringId.isDefined, "can't find a scoring id for the score type '" + scoreType + "'")
          
          stmt.executeWith(
            proteinMatch.accession,
            proteinMatch.description,
            Option(proteinMatch.geneName),
            proteinMatch.score,
            proteinMatch.coverage,
            proteinMatch.sequenceMatches.length,
            proteinMatch.peptideMatchesCount,
            proteinMatch.isDecoy,
            proteinMatch.isLastBioSequence,
            proteinMatch.properties.map(ProfiJson.serialize(_)),
            if (proteinMatch.taxonId > 0) Some(proteinMatch.taxonId) else Option.empty[Long],
            if (proteinMatch.getProteinId > 0) Some(proteinMatch.getProteinId) else Option.empty[Long],
            scoringId.get,
            rsId
          )
  
          // Update protein match id
          proteinMatch.id = stmt.generatedLong
        }
      }
  
      // Link protein matches to seq databases
      this.linkProteinMatchesToSeqDatabases(rs, msiEzDBC,proteinMatches)
  
      proteinMatches.length
    })

  }
  
  protected def linkProteinMatchesToSeqDatabases(rs: ResultSet, msiEzDBC: EasyDBC, proteinMatches: Array[ProteinMatch] ) {
    
    logger.info("ProteinMatches are going to be linked to seq databases...")
    
    val rsId = rs.id

    // Link protein matches to sequence databases
    msiEzDBC.executePrepared(MsiDbProteinMatchSeqDatabaseMapTable.mkInsertQuery()) { stmt =>
      for (proteinMatch <- proteinMatches)
        for (seqDbId <- proteinMatch.seqDatabaseIds)
          stmt.executeWith(proteinMatch.id, seqDbId, rsId)
    }
    
  }

  def insertRsSequenceMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int = {

    // Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches

    var count = 0
    
    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      msiEzDBC.executePrepared( MsiDbSequenceMatchTable.mkInsertQuery ) { stmt =>
        
        // Iterate over protein matches
        for (proteinMatch <- proteinMatches) {
  
          val proteinMatchId = proteinMatch.id
  
          for (seqMatch <- proteinMatch.sequenceMatches) {
  
            count += stmt.executeWith(
              proteinMatchId,
              seqMatch.getPeptideId,
              seqMatch.start,
              seqMatch.end,
              seqMatch.residueBefore.toString,
              seqMatch.residueAfter.toString,
              seqMatch.isDecoy,
              seqMatch.properties.map(ProfiJson.serialize(_)),
              seqMatch.getBestPeptideMatchId,
              rsId
            )
  
          }
        }
      }
    })

    count
    
  }

}