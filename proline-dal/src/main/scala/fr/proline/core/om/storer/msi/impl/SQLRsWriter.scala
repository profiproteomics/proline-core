package fr.proline.core.om.storer.msi.impl

import fr.profi.jdbc.easy._
import fr.profi.util.StringUtils
import fr.profi.util.serialization._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi._
import fr.proline.core.orm.msi.{ObjectTreeSchema, PeptideReadablePtmString}

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

private[proline] object SQLRsWriter extends AbstractSQLRsWriter

abstract class AbstractSQLRsWriter() extends IRsWriter {
  
  val objTreeInsertQuery: String = MsiDbObjectTreeTable.mkInsertQuery((t, c) => c.filter(_ != t.ID) )
  
  object CustomSerializer extends ProfiJSMSerialization with CustomDoubleJacksonSerializer

  def fetchExistingPeptidesIdByUniqueKey(pepSequences: Seq[String], msiDbCtx: MsiDbConnectionContext): Map[String, Long] = {
    
    val peptideMapBuilder = scala.collection.immutable.Map.newBuilder[String, Long]

    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      // Iterate over peptide sequences to retrieve their identifiers
      pepSequences.grouped(msiEzDBC.getInExpressionCountLimit).foreach { tmpPepSeqs =>
        
        val quotedPepSeqs = tmpPepSeqs map { "'" + _ + "'" }        
        val sqlQuery = new SelectQueryBuilder1( MsiDbPeptideTable ).mkSelectQuery( (t,c) =>
          List(t.ID,t.SEQUENCE,t.PTM_STRING) ->
          "WHERE "~ t.SEQUENCE ~" IN("~ quotedPepSeqs.mkString(",") ~")"
        )
        
        msiEzDBC.selectAndProcess(sqlQuery) { r =>
  
          val pepId = r.nextLong
          val pepSeq = r.nextString
          val ptmString = r.nextStringOrElse("")
          val pepKeyBuilder = new StringBuilder()
            .append(pepSeq)
            .append('%')
            .append(ptmString)
  
          peptideMapBuilder += (pepKeyBuilder.result -> pepId)
        }
      }
    }

    peptideMapBuilder.result()
  }

  /*def insertNewPeptides(peptides: Seq[Peptide], peptideByUniqueKey: HashMap[String,Peptide], msiDbCtx: MsiDbConnectionContext): Unit = {
    
    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      msiEzDBC.executeInBatch(MsiDbPeptideTable.mkInsertQuery()) { stmt =>
  
        // Iterate over the array of peptides to store them in the MSI-DB
        for (peptide <- peptides) {
  
          // Store only peptides which don't exist in the MSI-DB  
          if (peptideByUniqueKey.contains(peptide.uniqueKey) == false) {
  
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
    }

  }*/

  def fetchProteinIdentifiers(accessions: Seq[String]): Array[Any] = null

  def fetchExistingProteins(protCRCs: Seq[String]): Array[Protein] = {
    new Array[Protein](0)
  }

  def insertNewProteins(proteins: Seq[Protein], proteinBySequence: HashMap[String,Protein], msiDbCtx: MsiDbConnectionContext): Array[Protein] = {

    val newProteins = new ArrayBuffer[Protein](proteins.length)

    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      msiEzDBC.executeInBatch(MsiDbBioSequenceTable.mkInsertQuery()) { stmt =>
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
    }

    newProteins.toArray
  }

  def insertSpecifiedRsReadablePtmStrings(rs: ResultSet, readablePtmStringByPepId: Map[Long, PeptideReadablePtmString], msiDbCtx: MsiDbConnectionContext): Int = {

    // Define some vars
    val rsId = rs.id
    var count = 0

    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>

      val ptmStringInsertQuery = MsiDbPeptideReadablePtmStringTable.mkInsertQuery()

      msiEzDBC.executeInBatch( ptmStringInsertQuery ) { stmt =>
        for ( peptide <- rs.peptides; if StringUtils.isNotEmpty(peptide.readablePtmString) ) {
          val pepReadablePtm =  if(readablePtmStringByPepId.contains(peptide.id)) readablePtmStringByPepId(peptide.id).getReadablePtmString else  peptide.readablePtmString
          count += stmt.executeWith(
            peptide.id,
            rsId,
            pepReadablePtm
          )
        }
      }
    }

    count
  }

  def insertRsReadablePtmStrings(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int = {
    insertSpecifiedRsReadablePtmStrings(rs, Map.empty[Long, PeptideReadablePtmString], msiDbCtx)
  }

  def insertRsPeptideMatches(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val scoringIdByType = new MsiDbHelper(msiDbCtx).getScoringIdByType()
      
      // Retrieve some vars
      val rsId = rs.id
      val isDecoy = rs.isDecoy
      val peptideMatches = rs.peptideMatches
  
      val pepMatchInsertQuery = MsiDbPeptideMatchTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared(pepMatchInsertQuery, generateKeys = true ) { stmt =>
        
        // Iterate over peptide matche to store them
        for (peptideMatch <- peptideMatches) {
          
          val scoreType = peptideMatch.scoreType
          val scoringId = scoringIdByType.get(scoreType.toString)
          require(scoringId.isDefined, s"can't find a scoring id for the score type '$scoreType'")
  
          val msQuery = peptideMatch.msQuery
          val bestChildId = peptideMatch.bestChildId
          val pmCharge = if(peptideMatch.properties.isDefined && peptideMatch.properties.get.getOmssaProperties.isDefined) {
            peptideMatch.properties.get.getOmssaProperties.get.getCorrectedCharge
          } else msQuery.charge
  
          stmt.executeWith(
            pmCharge,
            msQuery.moz,
            peptideMatch.score,
            peptideMatch.rank,
            peptideMatch.cdPrettyRank,
            peptideMatch.sdPrettyRank,
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
      msiEzDBC.executeInBatch(MsiDbPeptideMatchRelationTable.mkInsertQuery()) { stmt =>
        for (
          peptideMatch <- peptideMatches;
          pepMatchChildrenIds <- Option(peptideMatch.getChildrenIds);
          pepMatchChildId <- pepMatchChildrenIds
        ) {
          stmt.executeWith(peptideMatch.id, pepMatchChildId, rsId)
        }
      }
      peptideMatches.length
    }

  }
  
  def insertSpectrumMatch(peptideMatch: PeptideMatch, spectrumMatch: SpectrumMatch, msiDbCtx: MsiDbConnectionContext): Int = {
    
    val schemaName = ObjectTreeSchema.SchemaName.SPECTRUM_MATCH.toString
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      // Store spectrum match
      val spectrumMatchId = msiEzDBC.executePrepared(objTreeInsertQuery, generateKeys = true) { stmt =>
        stmt.executeWith(
          Option.empty[Long],
          CustomSerializer.serialize(spectrumMatch),
          Option.empty[String],
          schemaName
        )
        
        stmt.generatedLong 
      }

      // Link spectrum match to peptide match
      msiEzDBC.executePrepared("INSERT INTO peptide_match_object_tree_map VALUES (?,?,?)") { stmt =>
        stmt.executeWith(
          peptideMatch.id,
          spectrumMatchId,
          schemaName
        )
      }
    }

  }

  def insertIonSeries(peptideMatch: PeptideMatch, ionSeries: Array[String], msiDbCtx: MsiDbConnectionContext): Int = {

    val schemaName = ObjectTreeSchema.SchemaName.ION_SERIES.toString

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      // Store spectrum match
      val ionSeriesId = msiEzDBC.executePrepared(objTreeInsertQuery, generateKeys = true) { stmt =>
        stmt.executeWith(
          Option.empty[Long],
          CustomSerializer.serialize(ionSeries),
          Option.empty[String],
          schemaName
        )

        stmt.generatedLong
      }

      // Link spectrum match to peptide match
      msiEzDBC.executePrepared("INSERT INTO peptide_match_object_tree_map VALUES (?,?,?)") { stmt =>
        stmt.executeWith(
          peptideMatch.id,
          ionSeriesId,
          schemaName
        )
      }
    }

  }
  
  def insertRsSpectrumMatches(rs: ResultSet, rf: IRsContainer, msiDbCtx: MsiDbConnectionContext): Int = {
    
    val schemaName = ObjectTreeSchema.SchemaName.SPECTRUM_MATCH.toString
    val pepMatchIdByKey = Map() ++ rs.peptideMatches.map( pm => (pm.msQuery.initialId,pm.rank) -> pm.id )    
    var spectraCount = 0
    
    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>

      val spectrumMatchKeyById = new HashMap[Long,(Int,Int)]()
      
      // Store spectrum matches
      msiEzDBC.executePrepared(objTreeInsertQuery, generateKeys = true ) { stmt =>
        rf.eachSpectrumMatch(rs.isDecoy, { spectrumMatch =>

          stmt.executeWith(
            Option.empty[Long],//ScalaMessagePack.write(spectrumMatch),
            CustomSerializer.serialize(spectrumMatch),
            Option.empty[String],
            schemaName
          )
          
          spectrumMatchKeyById += stmt.generatedLong -> (spectrumMatch.msQueryInitialId,spectrumMatch.peptideMatchRank)
          
          spectraCount += 1
        })
      }
      
      // Link spectrum matches to peptide matches
      msiEzDBC.executeInBatch("INSERT INTO peptide_match_object_tree_map VALUES (?,?,?)" ) { stmt =>
        
        for( (spectrumMatchId,spectrumMatchKey) <- spectrumMatchKeyById ) {
          val pepMatchId = pepMatchIdByKey( spectrumMatchKey )
          
          stmt.executeWith(
            pepMatchId,
            spectrumMatchId,
            schemaName
          )
        }
      }
      
    }
    
    spectraCount
  }

  def insertRsProteinMatches(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int = {

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val scoringIdByType = new MsiDbHelper(msiDbCtx).getScoringIdByType()
      
      // Retrieve some vars
      val rsId = rs.id
      val proteinMatches = rs.proteinMatches
  
      val protMatchInsertQuery = MsiDbProteinMatchTable.mkInsertQuery { (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      logger.info(s"Inserting ${proteinMatches.length} protein matches..." )
      
      msiEzDBC.executePrepared( protMatchInsertQuery, generateKeys = true ) { stmt =>
        
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
    }

  }
  
  protected def linkProteinMatchesToSeqDatabases(rs: ResultSet, msiEzDBC: EasyDBC, proteinMatches: Array[ProteinMatch] ) {
    
    logger.info("Linking protein matches to seq databases...")
    
    val rsId = rs.id

    // Link protein matches to sequence databases
    msiEzDBC.executeInBatch(MsiDbProteinMatchSeqDatabaseMapTable.mkInsertQuery()) { stmt =>
      for (proteinMatch <- proteinMatches)
        if(proteinMatch.seqDatabaseIds == null)
          logger.trace("No seq databases for Protein Match "+proteinMatch.accession)
        else {
          for (seqDbId <- proteinMatch.seqDatabaseIds)
            stmt.executeWith(proteinMatch.id, seqDbId, rsId)
        }
    }
    
  }

  def insertRsSequenceMatches(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int = {

    // Retrieve some vars
    val rsId = rs.id
    val isDecoy = rs.isDecoy
    val proteinMatches = rs.proteinMatches

    var count = 0
    
    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      msiEzDBC.executeInBatch( MsiDbSequenceMatchTable.mkInsertQuery() ) { stmt =>
        
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
    }

    count
  }


  def updateSearchSettingsFragmentationRuleSet(searchSettingsId: Long, fragRuleSetId : Long, msiDbConCtxt : MsiDbConnectionContext): Boolean = {
    var nbRecords =0
    DoJDBCWork.withEzDBC(msiDbConCtxt) { msiEzDBC =>
      val updateQuery = "UPDATE "+MsiDbSearchSettingsTable.name+" SET "+MsiDbSearchSettingsTable.columns.FRAGMENTATION_RULE_SET_ID+" = "+fragRuleSetId+" WHERE id = "+searchSettingsId
      nbRecords = msiEzDBC.execute(updateQuery)
    }
    nbRecords.equals(1)
  }
}