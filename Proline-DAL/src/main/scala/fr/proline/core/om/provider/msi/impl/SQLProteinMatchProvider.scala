package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.easy.EasyDBC
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
  
  def getResultSetsProteinMatches(rsIds: Seq[Int]): Array[ProteinMatch] = {    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      this._getProteinMatches(msiEzDBC,rsIds)
    })    
  }
  
  def getResultSummariesProteinMatches(rsmIds: Seq[Int]): Array[ProteinMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>

      val rsIdQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
        List(t.RESULT_SET_ID) -> "WHERE "~ t.ID ~" IN("~ rsmIds.mkString(",") ~")"
      )    
      val rsIds = msiEzDBC.selectInts(rsIdQuery)
      
      this._getProteinMatches(msiEzDBC,rsIds,Some(rsmIds))
    
    })
  }

  private def _getProteinMatches(msiEzDBC: EasyDBC, rsIds: Seq[Int], rsmIds: Option[Seq[Int]] = None ): Array[ProteinMatch] = {

    import fr.proline.util.primitives._
    import fr.proline.util.sql.StringOrBoolAsBool._

    // Retrieve score type map
    val scoreTypeById = new MsiDbHelper(msiDbCtx).getScoringTypeById

    // Execute SQL query to load sequence match records
    //val seqMatchMapBuilder = scala.collection.immutable.Map.newBuilder[Int,SequenceMatch]
    
    val rsIdsAsStr = rsIds.mkString(",")
    
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )
    val seqMatchRecords = msiEzDBC.selectAllRecordsAsMaps(seqMatchQuery)
    val seqMatchRecordsByProtMatchId = seqMatchRecords.groupBy(_(SeqMatchCols.PROTEIN_MATCH_ID).asInstanceOf[Int])

    // Load and map sequence database ids of each protein match
    val seqDbIdsByProtMatchId = new collection.mutable.HashMap[Int, ArrayBuffer[Int]]

    val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )
    
    msiEzDBC.selectAndProcess(protMatchDbMapQuery) { r =>
      val (proteinMatchId, seqDatabaseId) = (r.nextInt, r.nextInt)
      seqDbIdsByProtMatchId.getOrElseUpdate(proteinMatchId, new ArrayBuffer[Int](1) ) += seqDatabaseId
    }

    // Execute SQL query to load protein match records
    val protMatchQuery = if( rsmIds == None ) {
      new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
      )
    }
    else {
      val rsmIdsAsStr = rsmIds.get.mkString(",")
      new SelectQueryBuilder2(MsiDbProteinMatchTable,MsiDbProteinSetProteinMatchItemTable).mkSelectQuery( (t1,c1,t2,c2) => 
        List(t1.*) ->
        "WHERE "~ t2.RESULT_SUMMARY_ID ~" IN("~ rsmIdsAsStr ~") "~
        "AND "~ t1.ID ~"="~ t2.PROTEIN_MATCH_ID
      )
    }
    
    var protMatchColNames: Seq[String] = null
    val protMatches = msiEzDBC.select(protMatchQuery) { r =>

      if (protMatchColNames == null) { protMatchColNames = r.columnNames }
      val protMatchRecord = protMatchColNames.map(colName => (colName -> r.nextAnyRefOrElse(null))).toMap

      val protMatchId = toInt(protMatchRecord(ProtMatchCols.ID))

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
          val properties = if (propertiesAsJSON != null) Some(parse[SequenceMatchProperties](propertiesAsJSON)) else None

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
      val seqDatabaseIds = seqDbIdsByProtMatchId.getOrElse(protMatchId,ArrayBuffer.empty[Int]).toArray

      // Build protein match object
      val bioSequenceId = if (protMatchRecord(ProtMatchCols.BIO_SEQUENCE_ID) == null) { 0 }
      else { protMatchRecord(ProtMatchCols.BIO_SEQUENCE_ID).asInstanceOf[Int] }

      // Decode JSON properties
      val propertiesAsJSON = protMatchRecord(ProtMatchCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
      val properties = if (propertiesAsJSON != null) Some(parse[ProteinMatchProperties](propertiesAsJSON)) else None

      val description = protMatchRecord(ProtMatchCols.DESCRIPTION)

      val protMatch = new ProteinMatch(
        id = protMatchId,
        accession = protMatchRecord(ProtMatchCols.ACCESSION).asInstanceOf[String],
        description = if (description == null) "" else description.asInstanceOf[String],
        geneName = protMatchRecord(ProtMatchCols.GENE_NAME).asInstanceOf[String],
        sequenceMatches = seqMatches,
        isDecoy = protMatchRecord(ProtMatchCols.IS_DECOY),
        isLastBioSequence = protMatchRecord(ProtMatchCols.IS_LAST_BIO_SEQUENCE),
        seqDatabaseIds = seqDatabaseIds,
        proteinId = bioSequenceId,
        resultSetId = protMatchRecord(ProtMatchCols.RESULT_SET_ID).asInstanceOf[Int],
        properties = properties
      )

      if (protMatchRecord(ProtMatchCols.SCORE) != null) {
        protMatch.score = toFloat(protMatchRecord(ProtMatchCols.SCORE))
        protMatch.scoreType = scoreTypeById(protMatchRecord(ProtMatchCols.SCORING_ID).asInstanceOf[Int])
      }

      if (protMatchRecord(ProtMatchCols.COVERAGE) != null) {
        protMatch.coverage = toFloat(protMatchRecord(ProtMatchCols.COVERAGE))
      }

      if (protMatchRecord(ProtMatchCols.PEPTIDE_MATCH_COUNT) != null) {
        protMatch.peptideMatchesCount = protMatchRecord(ProtMatchCols.PEPTIDE_MATCH_COUNT).asInstanceOf[Int]
      }

      if (protMatchRecord(ProtMatchCols.TAXON_ID) != null) {
        protMatch.taxonId = protMatchRecord(ProtMatchCols.TAXON_ID).asInstanceOf[Int]
      }

      protMatch
    }

    protMatches.toArray

  }

}

