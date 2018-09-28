package fr.proline.core.om.builder

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object ProteinMatchBuilder extends LazyLogging {
  
  protected val ProtMatchCols = MsiDbProteinMatchColumns
  protected val ProtMatchSeqDbMapCols = MsiDbProteinMatchSeqDatabaseMapColumns
  protected val SeqMatchCols = MsiDbSequenceMatchColumns
  
  def buildProteinMatches(
    protMatchRecordSelector: (IValueContainer => Unit) => Unit,
    protMatchDbMapRecordSelector: (IValueContainer => Unit) => Unit,
    seqMatchRecords: Array[AnyMap],
    scoreTypeById: LongMap[String]
  ): Array[ProteinMatch] = {
    
    // Group seq match records by protein match id
    val seqMatchRecordsByProtMatchId = seqMatchRecords.groupBy( _.getLong(SeqMatchCols.PROTEIN_MATCH_ID) )
  
    // --- Execute SQL query to laod and map sequence database ids of each protein match ---
    val seqDbIdsByProtMatchId = new collection.mutable.LongMap[ArrayBuffer[Long]]     
    protMatchDbMapRecordSelector { r =>
      val proteinMatchId = r.getLong(ProtMatchSeqDbMapCols.PROTEIN_MATCH_ID)
      val seqDatabaseId = r.getLong(ProtMatchSeqDbMapCols.SEQ_DATABASE_ID)
      seqDbIdsByProtMatchId.getOrElseUpdate(proteinMatchId, new ArrayBuffer[Long](1) ) += seqDatabaseId
    }
    
    val protMatchIdSet = new HashSet[Long]
    val protMatches = new ArrayBuffer[ProteinMatch]
    
    // --- Execute SQL query to load protein match records ---
    protMatchRecordSelector { protMatchRecord =>
      
      val protMatchId = protMatchRecord.getLong(ProtMatchCols.ID)
      
      // Check this protein match has not been already loaded
      if( protMatchIdSet.contains(protMatchId) == false ) {
        protMatchIdSet += protMatchId
        
        val seqMatchesOpt = for ( seqMatchRecords <- seqMatchRecordsByProtMatchId.get(protMatchId) ) yield {
    
          val seqMatchesBuffer = new ArrayBuffer[SequenceMatch](seqMatchRecords.length)
          for (seqMatchRecord <- seqMatchRecords) {
            seqMatchesBuffer += this.buildSequenceMatch(seqMatchRecord)  
          }
    
          seqMatchesBuffer.toArray
        }
    
        // Retrieve sequence database ids
        val seqDatabaseIds = seqDbIdsByProtMatchId.getOrElse(protMatchId,ArrayBuffer.empty[Long]).toArray
  
        protMatches += this.buildProteinMatch(
          protMatchRecord,
          seqMatchesOpt.getOrElse(Array()),
          seqDatabaseIds,
          scoreTypeById
        )
        
      } else {
        logger.warn("protein match selector contains duplicated entries")
      }
  
    }
  
    protMatches.toArray
  }
  
  def buildProteinMatch(
    protMatchRecord: IValueContainer,
    seqMatches: Array[SequenceMatch],
    seqDbIds: Array[Long],
    scoreTypeById: LongMap[String]
  ): ProteinMatch = {
    
    // Build protein match object
    val bioSequenceId = protMatchRecord.getLongOrElse(ProtMatchCols.BIO_SEQUENCE_ID,0L)

    // Decode JSON properties
    val propertiesAsJSON = protMatchRecord.getStringOption(ProtMatchCols.SERIALIZED_PROPERTIES)
    val properties = propertiesAsJSON.map(ProfiJson.deserialize[ProteinMatchProperties](_))
    
    val protMatch = new ProteinMatch(
      id = protMatchRecord.getLong(ProtMatchCols.ID),
      accession = protMatchRecord.getString(ProtMatchCols.ACCESSION),
      description = protMatchRecord.getStringOrElse(ProtMatchCols.DESCRIPTION,""),
      geneName = protMatchRecord.getStringOrElse(ProtMatchCols.GENE_NAME,null),
      sequenceMatches = seqMatches,
      isDecoy = protMatchRecord.getBoolean(ProtMatchCols.IS_DECOY),
      isLastBioSequence = protMatchRecord.getBoolean(ProtMatchCols.IS_LAST_BIO_SEQUENCE),
      seqDatabaseIds = seqDbIds,
      proteinId = bioSequenceId,
      resultSetId = protMatchRecord.getLong(ProtMatchCols.RESULT_SET_ID),
      properties = properties
    )

    protMatchRecord.getFloatOption(ProtMatchCols.SCORE).map { score =>
      protMatch.score = score
      protMatch.scoreType = scoreTypeById(protMatchRecord.getLong(ProtMatchCols.SCORING_ID))
    }

    protMatchRecord.getIntOption(ProtMatchCols.PEPTIDE_MATCH_COUNT).map { pepMatchCount =>
      protMatch.peptideMatchesCount = pepMatchCount
    }

    protMatchRecord.getLongOption(ProtMatchCols.TAXON_ID).map { taxonId =>
      protMatch.taxonId = taxonId
    }
    
    protMatch
  }
  
  def buildSequenceMatch( seqMatchRecord: IValueContainer ): SequenceMatch = {
    
    // Retrieve sequence match attributes
    val resBeforeStr = seqMatchRecord.getString(SeqMatchCols.RESIDUE_BEFORE)
    val resBeforeChar = if (resBeforeStr != null) resBeforeStr.charAt(0) else '\0'
  
    val resAfterStr = seqMatchRecord.getString(SeqMatchCols.RESIDUE_AFTER)
    val resAfterChar = if (resAfterStr != null) resAfterStr.charAt(0) else '\0'
  
    // Decode JSON properties
    val propertiesAsJsonOpt = seqMatchRecord.getStringOption(SeqMatchCols.SERIALIZED_PROPERTIES)
    val properties = propertiesAsJsonOpt.map(ProfiJson.deserialize[SequenceMatchProperties](_))
  
    // Build sequence match
    new SequenceMatch(
      start = seqMatchRecord.getInt(SeqMatchCols.START),
      end = seqMatchRecord.getInt(SeqMatchCols.STOP),
      residueBefore = resBeforeChar,
      residueAfter = resAfterChar,
      isDecoy = seqMatchRecord.getBoolean(SeqMatchCols.IS_DECOY),
      peptideId = seqMatchRecord.getLong(SeqMatchCols.PEPTIDE_ID),
      bestPeptideMatchId = seqMatchRecord.getLong(SeqMatchCols.BEST_PEPTIDE_MATCH_ID),
      resultSetId = seqMatchRecord.getLong(SeqMatchCols.RESULT_SET_ID),
      properties = properties
    )
  }

}