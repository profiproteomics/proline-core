package fr.proline.core.om.provider.msi.impl

import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.{SelectQueryBuilder1,SelectQueryBuilder2}
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstancePeptideMatchMapTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideMatchColumns
import fr.proline.core.dal.tables.msi.MsiDbPeptideMatchTable
import fr.proline.core.om.builder.PeptideMatchBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi._
import fr.proline.repository.ProlineDatabaseType

class SQLPeptideMatchProvider(
  val msiDbCtx: DatabaseConnectionContext,
  var peptideProvider: IPeptideProvider
) extends IPeptideMatchProvider {
  
  val PepMatchCols = MsiDbPeptideMatchColumns
  val msQProvider = new SQLMsQueryProvider(msiDbCtx)
  
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  def this(msiDbCtx: DatabaseConnectionContext, psSqlCtx: DatabaseConnectionContext) = {
    this(msiDbCtx, new SQLPeptideProvider(psSqlCtx) )
  }

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById

  def getPeptideMatches(pepMatchIds: Seq[Long]): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      // TODO: use max nb iterations
      val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepMatchIds.mkString(",") ~")"
      )
      val pmRecords = msiEzDBC.selectAllRecords(sqlQuery)
      
      val msQueries = this._loadMsQueries(pmRecords)
      
      PeptideMatchBuilder.buildPeptideMatches(pmRecords,msQueries,scoreTypeById,peptideProvider)
    })
  }

  def getPeptideMatchesAsOptions(pepMatchIds: Seq[Long]): Array[Option[PeptideMatch]] = {

    val pepMatches = this.getPeptideMatches(pepMatchIds)
    val pepMatchById = pepMatches.map { pepMatch => pepMatch.id -> pepMatch } toMap

    pepMatchIds.map { pepMatchById.get(_) } toArray
  }
  
  def getPeptideMatchesByPeptideIds(peptideIds: Seq[Long]): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    
      // TODO: use max nb iterations
      val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.PEPTIDE_ID ~" IN("~ peptideIds.mkString(",") ~")"
      )
      val pmRecords = msiEzDBC.selectAllRecords(sqlQuery)
      
      val msQueries = this._loadMsQueries(pmRecords)
      
      PeptideMatchBuilder.buildPeptideMatches(pmRecords,msQueries,scoreTypeById,peptideProvider)
    })
  }

  def getResultSetsPeptideMatches(rsIds: Seq[Long], pepMatchFilter: Option[PeptideMatchFilter] = None): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val sqlPepMatchFilter = pepMatchFilter.map( _pepMatchFilterToSQLCondition(_) ).getOrElse("")
    
      val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIds.mkString(",") ~")"~ sqlPepMatchFilter
      )
      
      val pmRecords = msiEzDBC.selectAllRecords(sqlQuery)      
      val msQueries = this._loadResulSetMsQueries(rsIds.toArray, pmRecords)
      
      PeptideMatchBuilder.buildPeptideMatches(pmRecords,msQueries,scoreTypeById,peptideProvider)
    })

  }

  def getResultSummariesPeptideMatches(rsmIds: Seq[Long]): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      //val sqlPepMatchFilter = pepMatchFilter.map( _pepMatchFilterToSQLCondition(_) ).getOrElse("")
  
      val sqb2 = new SelectQueryBuilder2(MsiDbPeptideMatchTable, MsiDbPeptideInstancePeptideMatchMapTable)
      
      val sqlQuery = sqb2.mkSelectQuery( (t1, c1, t2, c2) =>
        List(t1.*) ->
        " WHERE " ~ t1.ID ~ " = " ~ t2.PEPTIDE_MATCH_ID ~
        " AND " ~ t2.RESULT_SUMMARY_ID ~ " IN (" ~ rsmIds.mkString(",") ~ ")"
      )
      
      val pmRecords = msiEzDBC.selectAllRecords(sqlQuery)
      
      val rsIds = pmRecords.map( _.getLong(PepMatchCols.RESULT_SET_ID) ).distinct
      val msQueries = this._loadResulSetMsQueries(rsIds, pmRecords)
      
      PeptideMatchBuilder.buildPeptideMatches(pmRecords,msQueries,scoreTypeById,peptideProvider)
    })

  }
  
  private def _pepMatchFilterToSQLCondition(pepMatchFilter: PeptideMatchFilter): String = {
    " AND " + PepMatchCols.RANK + " <= " + pepMatchFilter.maxRank
  }

  private def _loadResulSetMsQueries( rsIds: Array[Long], pmRecords: Seq[IValueContainer] ): Array[MsQuery] = {
    
    // Load MS queries
    val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds(rsIds)
    if(msiSearchIds != null && ! msiSearchIds.isEmpty)  { 
      msQProvider.getMsiSearchesMsQueries(msiSearchIds) 
    }
    else this._loadMsQueries(pmRecords)
    
  }
  
  private def _loadMsQueries( pmRecords: Seq[IValueContainer] ): Array[MsQuery] = {    
    val uniqMsQueriesIds = pmRecords.map( _.getLong(PepMatchCols.MS_QUERY_ID) ).distinct
    msQProvider.getMsQueries(uniqMsQueriesIds) 
  }

}