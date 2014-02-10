package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1,SelectQueryBuilder2}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.{ ProteinMatch, SequenceMatch,ProteinMatchProperties, SequenceMatchProperties }
import fr.proline.core.om.provider.msi.IProteinMatchProvider
import fr.proline.context.DatabaseConnectionContext

class SQLProteinMatchProvider(val msiDbCtx: DatabaseConnectionContext) { //extends IProteinMatchProvider

  val ProtMatchCols = MsiDbProteinMatchTable.columns
  val SeqMatchCols = MsiDbSequenceMatchTable.columns
  
  def getProteinMatches(protMatchIds: Seq[Long]): Array[ProteinMatch] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val protMatchIdsAsStr = protMatchIds.mkString(",")
      
      // --- Build SQL query to load protein match records ---
      val protMatchQuery = new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ protMatchIdsAsStr ~")"
      )
      
      // --- Build SQL query to load and map sequence database ids of each protein match ---
      val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
        List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ t.PROTEIN_MATCH_ID ~" IN("~ protMatchIdsAsStr ~")"
      )
      
      // --- Build SQL query to load sequence match records ---
      val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.PROTEIN_MATCH_ID ~" IN("~ protMatchIdsAsStr ~")"
      )
      
      this._getProteinMatches(msiEzDBC,protMatchQuery,protMatchDbMapQuery,seqMatchQuery)
    })
  }
  
  def getResultSetsProteinMatches(rsIds: Seq[Long]): Array[ProteinMatch] = {    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      this._getProteinMatches(msiEzDBC,rsIds)
    })
  }
  
  def getResultSummariesProteinMatches(rsmIds: Seq[Long]): Array[ProteinMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val rsIdQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
        List(t.RESULT_SET_ID) -> "WHERE "~ t.ID ~" IN("~ rsmIds.mkString(",") ~")"
      )
      val rsIds = msiEzDBC.selectLongs(rsIdQuery)
      
      this._getProteinMatches(msiEzDBC,rsIds,Some(rsmIds))
      
    })
  }

  private def _getProteinMatches(msiEzDBC: EasyDBC, rsIds: Seq[Long], rsmIds: Option[Seq[Long]] = None ): Array[ProteinMatch] = {
    
    val rsIdsAsStr = rsIds.mkString(",")
    
    // --- Build SQL query to load protein match records ---
    val protMatchQuery = if( rsmIds.isEmpty ) {
      new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
      )
    }
    else {
      val rsmIdsAsStr = rsmIds.get.mkString(",")
      // Retrieve only protein matches involved in a result summary
      new SelectQueryBuilder2(MsiDbProteinMatchTable,MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t1,c1,t2,c2) => 
        List(t1.*) ->
        "WHERE "~ t2.RESULT_SUMMARY_ID ~" IN("~ rsmIdsAsStr ~") "~
        "AND "~ t1.ID ~"="~ t2.PROTEIN_MATCH_ID
      )
    }

    // --- Build SQL query to load and map sequence database ids of each protein match ---
    val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )    

    // --- Build SQL query to load sequence match records ---
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )

    this._getProteinMatches(msiEzDBC,protMatchQuery,protMatchDbMapQuery,seqMatchQuery)
  }
  
   private def _getProteinMatches(
     msiEzDBC: EasyDBC,
     protMatchQuery: String,
     protMatchDbMapQuery: String,
     seqMatchQuery: String
   ): Array[ProteinMatch] = {
     
    import fr.proline.util.primitives.toLong
    import fr.proline.util.sql.StringOrBoolAsBool._
    
    // Retrieve score type map
    val scoreTypeById = new MsiDbHelper(msiDbCtx).getScoringTypeById
    
    // --- Execute SQL query to load sequence match records ---
    val seqMatchRecords = msiEzDBC.selectAllRecordsAsMaps(seqMatchQuery)
    val seqMatchRecordsByProtMatchId = seqMatchRecords.groupBy(v => toLong(v(SeqMatchCols.PROTEIN_MATCH_ID)))

    // --- Execute SQL query to laod and map sequence database ids of each protein match ---
    val seqDbIdsByProtMatchId = new collection.mutable.HashMap[Long, ArrayBuffer[Long]]     
    msiEzDBC.selectAndProcess(protMatchDbMapQuery) { r =>
      val (proteinMatchId, seqDatabaseId) = (r.nextLong, r.nextLong)
      seqDbIdsByProtMatchId.getOrElseUpdate(proteinMatchId, new ArrayBuffer[Long](1) ) += seqDatabaseId
    }
    
    //var protMatchColNames: Seq[String] = null
    var protMatchIdSet = new HashSet[Long]
    val protMatches = new ArrayBuffer[ProteinMatch]
    
    // --- Execute SQL query to load protein match records ---
    msiEzDBC.selectAndProcess(protMatchQuery) { protMatchRecord =>

      //if (protMatchColNames == null) { protMatchColNames = r.columnNames }
      //val protMatchRecord = protMatchColNames.map(colName => (colName -> r.nextAnyRefOrElse(null))).toMap

      val protMatchId = protMatchRecord.getLong(ProtMatchCols.ID)
      
      // Check this protein match has not been already loaded
      if( protMatchIdSet.contains(protMatchId) == false ) {
        protMatchIdSet += protMatchId
        
        var seqMatches: Array[SequenceMatch] = null
        for ( seqMatchRecords <- seqMatchRecordsByProtMatchId.get(protMatchId) ) {
  
          val seqMatchesBuffer = new ArrayBuffer[SequenceMatch](seqMatchRecords.length)
          for (seqMatchRecord <- seqMatchRecords) {
  
            // Retrieve sequence match attributes
            val resBeforeStr = seqMatchRecord(SeqMatchCols.RESIDUE_BEFORE).asInstanceOf[String]
            val resBeforeChar = if (resBeforeStr != null) resBeforeStr.charAt(0) else '\0'
  
            val resAfterStr = seqMatchRecord(SeqMatchCols.RESIDUE_AFTER).asInstanceOf[String]
            val resAfterChar = if (resAfterStr != null) resAfterStr.charAt(0) else '\0'
  
            // Decode JSON properties
            val propertiesAsJSON = seqMatchRecord(SeqMatchCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
            val properties = if (propertiesAsJSON != null) Some(ProfiJson.deserialize[SequenceMatchProperties](propertiesAsJSON)) else None
  
            // Build sequence match
            val seqMatch = new SequenceMatch(
              start = seqMatchRecord(SeqMatchCols.START).asInstanceOf[Int],
              end = seqMatchRecord(SeqMatchCols.STOP).asInstanceOf[Int],
              residueBefore = resBeforeChar,
              residueAfter = resAfterChar,
              isDecoy = seqMatchRecord(SeqMatchCols.IS_DECOY),
              peptideId = toLong(seqMatchRecord(SeqMatchCols.PEPTIDE_ID)),
              bestPeptideMatchId = toLong(seqMatchRecord(SeqMatchCols.BEST_PEPTIDE_MATCH_ID)),
              resultSetId = toLong(seqMatchRecord(SeqMatchCols.RESULT_SET_ID)),
              properties = properties
            )
  
            seqMatchesBuffer += seqMatch
  
          }
  
          seqMatches = seqMatchesBuffer.toArray
  
        }
  
        // Retrieve sequence database ids
        val seqDatabaseIds = seqDbIdsByProtMatchId.getOrElse(protMatchId,ArrayBuffer.empty[Long]).toArray
  
        // Build protein match object
        val bioSequenceId = protMatchRecord.getLongOrElse(ProtMatchCols.BIO_SEQUENCE_ID,0L)
  
        // Decode JSON properties
        val propertiesAsJSON = protMatchRecord.getStringOption(ProtMatchCols.SERIALIZED_PROPERTIES)
        val properties = propertiesAsJSON.map(ProfiJson.deserialize[ProteinMatchProperties](_))
        
        val protMatch = new ProteinMatch(
          id = protMatchId,
          accession = protMatchRecord.getString(ProtMatchCols.ACCESSION),
          description = protMatchRecord.getStringOrElse(ProtMatchCols.DESCRIPTION,""),
          geneName = protMatchRecord.getString(ProtMatchCols.GENE_NAME),
          sequenceMatches = seqMatches,
          isDecoy = protMatchRecord.getBoolean(ProtMatchCols.IS_DECOY),
          isLastBioSequence = protMatchRecord.getBoolean(ProtMatchCols.IS_LAST_BIO_SEQUENCE),
          seqDatabaseIds = seqDatabaseIds,
          proteinId = bioSequenceId,
          resultSetId = protMatchRecord.getLong(ProtMatchCols.RESULT_SET_ID),
          properties = properties
        )
  
        protMatchRecord.getFloatOption(ProtMatchCols.SCORE).map { score =>
          protMatch.score = score
          protMatch.scoreType = scoreTypeById(protMatchRecord.getLong(ProtMatchCols.SCORING_ID))
        }
  
        protMatchRecord.getFloatOption(ProtMatchCols.COVERAGE).map { coverage =>
          protMatch.coverage = coverage
        }
  
        protMatchRecord.getIntOption(ProtMatchCols.PEPTIDE_MATCH_COUNT).map { pepMatchCount =>
          protMatch.peptideMatchesCount = pepMatchCount
        }
  
        protMatchRecord.getLongOption(ProtMatchCols.TAXON_ID).map { taxonId =>
          protMatch.taxonId = taxonId
        }
  
        protMatches += protMatch
      }

    }

    protMatches.toArray
  }

}

