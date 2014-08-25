package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.om.builder.ProteinMatchBuilder
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1,SelectQueryBuilder2}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.{ ProteinMatch, SequenceMatch,ProteinMatchProperties, SequenceMatchProperties }
import fr.proline.core.om.provider.msi.IProteinMatchProvider
import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.ProlineDatabaseType

class SQLProteinMatchProvider(val msiDbCtx: DatabaseConnectionContext) { //extends IProteinMatchProvider
  
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  // Retrieve score type map
  val scoreTypeById = new MsiDbHelper(msiDbCtx).getScoringTypeById
  
  def getProteinMatches(protMatchIds: Seq[Long]): Array[ProteinMatch] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val protMatchIdArray = protMatchIds.toArray
      
      ProteinMatchBuilder.buildProteinMatches(
        SQLProteinMatchProvider.selectProteinMatchRecords(msiEzDBC, protMatchIdArray),
        SQLProteinMatchProvider.selectProtMatchSeqDbMapRecords(msiEzDBC, protMatchIdArray),
        SQLProteinMatchProvider.selectAllProtMatchSeqMatchRecords(msiEzDBC, protMatchIdArray),
        scoreTypeById
      )
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
    
    // --- Build SQL query to load protein match records ---
    val protMatchSelector = if( rsmIds.isEmpty ) {
      SQLProteinMatchProvider.selectResultSetProteinMatchRecords(msiEzDBC, rsIds.toArray)
    }
    else {
      SQLProteinMatchProvider.selectResultSummaryProteinMatchRecords(msiEzDBC, rsmIds.get.toArray)
    }
    
    val rsIdsAsStr = rsIds.mkString(",")

    // --- Build SQL query to load and map sequence database ids of each protein match ---
    val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )

    // --- Build SQL query to load sequence match records ---
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )

    ProteinMatchBuilder.buildProteinMatches(
      protMatchSelector,
      msiEzDBC.selectAndProcess(protMatchDbMapQuery),
      msiEzDBC.selectAllRecords(seqMatchQuery),
      scoreTypeById
    )
  }
  
}

object SQLProteinMatchProvider {
  
  def selectProteinMatchRecords( msiEzDBC: EasyDBC, protMatchIds: Array[Long] ): (IValueContainer => Unit) => Unit = {
    
    val protMatchIdsAsStr = protMatchIds.mkString(",")
    
    // --- Build SQL query to load protein match records ---
    val protMatchQuery = new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ protMatchIdsAsStr ~")"
    )
    
    msiEzDBC.selectAndProcess(protMatchQuery)
  }
  
  def selectResultSetProteinMatchRecords( msiEzDBC: EasyDBC, rsIds: Array[Long] ): (IValueContainer => Unit) => Unit = {
   
    val rsIdsAsStr = rsIds.mkString(",")
   
    // Retrieve protein matches belonging to some result sets
    val protMatchQuery = new SelectQueryBuilder1(MsiDbProteinMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIdsAsStr ~")"
    )
    
    msiEzDBC.selectAndProcess(protMatchQuery)
  }
  
  def selectResultSummaryProteinMatchRecords( msiEzDBC: EasyDBC, rsmIds: Array[Long] ): (IValueContainer => Unit) => Unit = {
   
    val rsmIdsAsStr = rsmIds.mkString(",")
    
    // Retrieve protein matches belonging to some result summaries
    val protMatchQuery = new SelectQueryBuilder2(MsiDbProteinMatchTable,MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t1,c1,t2,c2) => 
      List(t1.*) ->
      "WHERE "~ t2.RESULT_SUMMARY_ID ~" IN("~ rsmIdsAsStr ~") "~
      "AND "~ t1.ID ~"="~ t2.PROTEIN_MATCH_ID
    )
    
    msiEzDBC.selectAndProcess(protMatchQuery)
  }
  
  def selectProtMatchSeqDbMapRecords( msiEzDBC: EasyDBC, protMatchIds: Array[Long] ): (IValueContainer => Unit) => Unit = {
    
    val protMatchIdsAsStr = protMatchIds.mkString(",")
    
    // --- Build SQL query to load and map sequence database ids of each protein match ---
    val protMatchDbMapQuery = new SelectQueryBuilder1(MsiDbProteinMatchSeqDatabaseMapTable).mkSelectQuery( (t,c) =>
      List(t.PROTEIN_MATCH_ID,t.SEQ_DATABASE_ID) -> "WHERE "~ t.PROTEIN_MATCH_ID ~" IN("~ protMatchIdsAsStr ~")"
    )
    
    msiEzDBC.selectAndProcess(protMatchDbMapQuery)
  }
  
  def selectAllProtMatchSeqMatchRecords( msiEzDBC: EasyDBC, protMatchIds: Array[Long] ): Array[AnyMap] = {
    
    val protMatchIdsAsStr = protMatchIds.mkString(",")
    
    // --- Build SQL query to load sequence match records ---
    val seqMatchQuery = new SelectQueryBuilder1(MsiDbSequenceMatchTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PROTEIN_MATCH_ID ~" IN("~ protMatchIdsAsStr ~")"
    )
    
    // --- Execute SQL query to load sequence match records ---
    msiEzDBC.selectAllRecords(seqMatchQuery)
  }
  
}

