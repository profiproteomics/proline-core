package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.ResultSetRow
import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.om.builder.ProteinMatchBuilder
import fr.proline.core.om.model.msi.ProteinMatch

import scala.collection.mutable.ArrayBuffer

class SQLProteinMatchProvider(val msiDbCtx: MsiDbConnectionContext) { //extends IProteinMatchProvider
  
  // Retrieve score type map
  val scoreTypeById = new MsiDbHelper(msiDbCtx).getScoringTypeById
  
  def getProteinMatches(protMatchIds: Seq[Long]): Array[ProteinMatch] = {
    if (protMatchIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val protMatchIdArray = protMatchIds.toArray
      
      ProteinMatchBuilder.buildProteinMatches(
        SQLProteinMatchProvider.selectProteinMatchRecords(msiEzDBC, protMatchIdArray),
        SQLProteinMatchProvider.selectProtMatchSeqDbMapRecords(msiEzDBC, protMatchIdArray),
        SQLProteinMatchProvider.selectAllProtMatchSeqMatchRecords(msiEzDBC, protMatchIdArray),
        scoreTypeById
      )
    }
  }
  
  def getResultSetsProteinMatches(rsIds: Seq[Long]): Array[ProteinMatch] = {
    if (rsIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      this._getProteinMatches(msiEzDBC,rsIds)
    }
  }
  
  def getResultSummariesProteinMatches(rsmIds: Seq[Long]): Array[ProteinMatch] = {
    if (rsmIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      val rsIdQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
        List(t.RESULT_SET_ID) -> "WHERE "~ t.ID ~" IN ("~ rsmIds.mkString(",") ~")"
      )
      val rsIds = msiEzDBC.selectLongs(rsIdQuery)
      
      this._getProteinMatches(msiEzDBC,rsIds,Some(rsmIds))
      
    }
  }

  private def _getProteinMatches(msiEzDBC: EasyDBC, rsIds: Seq[Long], rsmIds: Option[Seq[Long]] = None ): Array[ProteinMatch] = {
    
    // --- Build SQL queries to load protein match and equence match records ---
    val( protMatchSelector, seqMatchSelector ) = if( rsmIds.isEmpty ) {
      SQLProteinMatchProvider.selectResultSetProteinMatchRecords(msiEzDBC, rsIds) ->
      SQLProteinMatchProvider.selectResultSetSequenceMatchRecords(msiEzDBC, rsIds)
    }
    else {
      SQLProteinMatchProvider.selectResultSummaryProteinMatchRecords(msiEzDBC, rsmIds.get) ->
      SQLProteinMatchProvider.selectResultSummarySequenceMatchRecords(msiEzDBC, rsmIds.get)
    }
    
    // --- Build SQL query to load and map sequence database ids of each protein match ---
    val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ rsIds.map(id => "" ~ t.RESULT_SET_ID ~ s"=$id").mkString(" OR ")
    )
    
    val seqMatchRecords = new ArrayBuffer[AnyMap]()
    seqMatchSelector { r => 
      seqMatchRecords += r.asInstanceOf[ResultSetRow].toAnyMap()
    }

    ProteinMatchBuilder.buildProteinMatches(
      protMatchSelector,
      msiEzDBC.selectAndProcess(protMatchDbMapQuery),
      seqMatchRecords.toArray,
      scoreTypeById
    )
  }
  
}

object SQLProteinMatchProvider {
  
  def selectProteinMatchRecords( msiEzDBC: EasyDBC, protMatchIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
    
    // --- Build SQL query to load protein match records ---
    val protMatchQuery = new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ protMatchIds.mkString(",") ~")"
    )
    
    msiEzDBC.selectAndProcess(protMatchQuery)
  }
  
  def selectResultSetProteinMatchRecords( msiEzDBC: EasyDBC, rsIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
   
    // Retrieve protein matches belonging to some result sets
    val protMatchQuery = new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ rsIds.map(id => "" ~ t.RESULT_SET_ID ~ s"=$id").mkString(" OR ")
    )
    
    msiEzDBC.selectAndProcess(protMatchQuery)
  }
  
  def selectResultSummaryProteinMatchRecords( msiEzDBC: EasyDBC, rsmIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
   
    // Retrieve protein matches belonging to some result summaries
    val protMatchQuery = new SelectQueryBuilder2(MsiDbProteinMatchTable,MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t1,c1,t2,c2) => 
      List(t1.*) ->
      "WHERE ("~ rsmIds.map(id => "" ~ t2.RESULT_SUMMARY_ID ~ s"=$id").mkString(" OR ") ~") " ~
      "AND "~ t1.ID ~"="~ t2.PROTEIN_MATCH_ID
    )
    
    msiEzDBC.selectAndProcess(protMatchQuery)
  }
  
  def selectResultSetSequenceMatchRecords( msiEzDBC: EasyDBC, rsIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
   
    // Retrieve sequence matches belonging to some result sets
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ rsIds.map(id => "" ~ t.RESULT_SET_ID ~ s"=$id").mkString(" OR ")
    )
    
    msiEzDBC.selectAndProcess(seqMatchQuery)
  }
  
  def selectResultSummarySequenceMatchRecords( msiEzDBC: EasyDBC, rsmIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
   
    // Retrieve peptide ids belonging to some result summaries
    val pepIdQuery = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) => 
      List(t.PEPTIDE_ID) -> "WHERE "~ rsmIds.map(id => "" ~ t.RESULT_SUMMARY_ID ~ s"=$id").mkString(" OR ")
    )
    val peptideIdSet = msiEzDBC.selectLongs(pepIdQuery).toSet
    
    // Retrieve result set ids corresponding to these result summaries
    val rsIds = msiEzDBC.selectLongs( "SELECT result_set_id FROM result_summary WHERE id IN (" + rsmIds.mkString(",") + ")" )
    
    // Retrieve sequence matches belonging to some result summaries
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) => 
      List(t.*) -> "WHERE "~ rsIds.map(id => "" ~ t.RESULT_SET_ID ~ s"=$id").mkString(" OR ")
    )
    
    def filterSequenceMatches(): (IValueContainer => Unit) => Unit = { consumer =>
      
      msiEzDBC.selectAndProcess(seqMatchQuery) { r =>
        val seqMatchPeptideId = r.getLong(MsiDbSequenceMatchColumns.PEPTIDE_ID)
        if( peptideIdSet.contains(seqMatchPeptideId) ) {
          consumer(r)
        }
      }
      
      ()
    }

    filterSequenceMatches()
  }
  
  def selectProtMatchSeqDbMapRecords( msiEzDBC: EasyDBC, protMatchIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
    
    // --- Build SQL query to load and map sequence database ids of each protein match ---
    val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ t.PROTEIN_MATCH_ID ~" IN ("~ protMatchIds.mkString(",") ~")"
    )
    
    msiEzDBC.selectAndProcess(protMatchDbMapQuery)
  }
  
  def selectAllProtMatchSeqMatchRecords( msiEzDBC: EasyDBC, protMatchIds: Seq[Long] ): Array[AnyMap] = {
    
    // --- Build SQL query to load sequence match records ---
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PROTEIN_MATCH_ID ~" IN ("~ protMatchIds.mkString(",") ~")"
    )
    
    // --- Execute SQL query to load sequence match records ---
    msiEzDBC.selectAllRecords(seqMatchQuery)
  }
  
}

