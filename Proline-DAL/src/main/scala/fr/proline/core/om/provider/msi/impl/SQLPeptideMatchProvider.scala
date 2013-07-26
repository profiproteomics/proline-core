package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.{SelectQueryBuilder1,SelectQueryBuilder2}
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi.MsiDbPeptideMatchTable
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PeptideMatchProperties
import fr.proline.core.om.provider.msi.{IPeptideProvider,IPeptideMatchProvider,PeptideMatchFilter}
import fr.proline.util.sql.StringOrBoolAsBool._
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstancePeptideMatchMapTable
import fr.proline.util.primitives._
import fr.proline.core.om.model.msi.MsQuery

class SQLPeptideMatchProvider(
  val msiSqlCtx: DatabaseConnectionContext,
  var peptideProvider: IPeptideProvider
) extends IPeptideMatchProvider {
  
  def this(msiSqlCtx: DatabaseConnectionContext, psSqlCtx: DatabaseConnectionContext) = {
    this(msiSqlCtx, new SQLPeptideProvider(psSqlCtx) )
  }

  val PepMatchCols = MsiDbPeptideMatchTable.columns

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiSqlCtx)

  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById

  def getPeptideMatches(pepMatchIds: Seq[Long]): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
    
      // TODO: use max nb iterations
      val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepMatchIds.mkString(",") ~")"
      )
      val pmRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery)
      
      val rsIds = pmRecords.map { pm => toLong(pm(PepMatchCols.RESULT_SET_ID)) }.distinct
      this._buildPeptideMatches(rsIds, pmRecords)
    
    })
  }

  def getPeptideMatchesAsOptions(pepMatchIds: Seq[Long]): Array[Option[PeptideMatch]] = {

    val pepMatches = this.getPeptideMatches(pepMatchIds)
    val pepMatchById = pepMatches.map { pepMatch => pepMatch.id -> pepMatch } toMap

    pepMatchIds.map { pepMatchById.get(_) } toArray
  }

  def getResultSetsPeptideMatches(rsIds: Seq[Long], pepMatchFilter: Option[PeptideMatchFilter] = None): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
      
      val sqlPepMatchFilter = pepMatchFilter.map( _pepMatchFilterToSQLCondition(_) ).getOrElse("")
    
      val sqlQuery = new SelectQueryBuilder1(MsiDbPeptideMatchTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SET_ID ~" IN("~ rsIds.mkString(",") ~")"~ sqlPepMatchFilter
      )
      
      val pmRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery)
  
      this._buildPeptideMatches(rsIds, pmRecords)
    
    })

  }

  def getResultSummariesPeptideMatches(rsmIds: Seq[Long]): Array[PeptideMatch] = {
    
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
      
      //val sqlPepMatchFilter = pepMatchFilter.map( _pepMatchFilterToSQLCondition(_) ).getOrElse("")
  
      val sqb2 = new SelectQueryBuilder2(MsiDbPeptideMatchTable, MsiDbPeptideInstancePeptideMatchMapTable)
      
      val sqlQuery = sqb2.mkSelectQuery( (t1, c1, t2, c2) =>
        List(t1.*) ->
        " WHERE " ~ t1.ID ~ " = " ~ t2.PEPTIDE_MATCH_ID ~
        " AND " ~ t2.RESULT_SUMMARY_ID ~ " IN (" ~ rsmIds.mkString(",") ~ ")"
      )
      
      val pmRecords = msiEzDBC.selectAllRecordsAsMaps(sqlQuery)
      
      val rsIds = pmRecords.map( pm => toLong(pm(PepMatchCols.RESULT_SET_ID)) ).distinct
      this._buildPeptideMatches(rsIds, pmRecords)
    
    })

  }
  
  private def _pepMatchFilterToSQLCondition(pepMatchFilter: PeptideMatchFilter): String = {
    " AND " + PepMatchCols.RANK + " <= " + pepMatchFilter.maxRank
  }

  private def _buildPeptideMatches(rsIds: Seq[Long], pmRecords: Seq[Map[String, Any]]): Array[PeptideMatch] = {

    import fr.proline.util.primitives._

    // Load peptides
    val uniqPepIds = pmRecords map { v => toLong(v(PepMatchCols.PEPTIDE_ID)) } distinct
    val peptides = this.peptideProvider.getPeptides(uniqPepIds)

    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => (pep.id -> pep) }
    
    var msQueries :  Array[MsQuery] = null
     val msQProvider = new SQLMsQueryProvider(msiSqlCtx)
    
    // Load MS queries
    val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds(rsIds)
    if(msiSearchIds != null && ! msiSearchIds.isEmpty)  { 
    	msQueries= msQProvider.getMsiSearchesMsQueries(msiSearchIds) 
       
    } else {      
    	val uniqMsQueriesIds  = pmRecords map { v => toLong(v(PepMatchCols.MS_QUERY_ID)) } distinct
    	
    	// Previous empty line needed ^^     	
    	msQueries= msQProvider.getMsQueries(uniqMsQueriesIds) 
    }
    
    val msQueryById = Map() ++ msQueries.map { msq => (msq.id -> msq) }

    // Load peptide matches
    val pepMatches = new Array[PeptideMatch](pmRecords.length)

    for (pepMatchIdx <- 0 until pmRecords.length) {

      // Retrieve peptide match record
      val pepMatchRecord = pmRecords(pepMatchIdx)

      // Retrieve the corresponding peptide
      val pepId = toLong(pepMatchRecord(PepMatchCols.PEPTIDE_ID))
      if (!peptideById.contains(pepId)) {
        throw new Exception("undefined peptide with id ='" + pepId + "' ")
      }
      val peptide = peptideById(pepId)

      // Retrieve the corresponding MS query
      val msQueryOpt = msQueryById.get(toLong(pepMatchRecord(PepMatchCols.MS_QUERY_ID)))

      // Retrieve some vars
      val scoreType = scoreTypeById(toLong(pepMatchRecord(PepMatchCols.SCORING_ID)))

      // Decode JSON properties
      val propertiesAsJSON = pepMatchRecord(PepMatchCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
      val properties = if (propertiesAsJSON != null) Some(parse[PeptideMatchProperties](propertiesAsJSON)) else None

      val pepMatch = new PeptideMatch(
        id = toLong(pepMatchRecord(PepMatchCols.ID)),
        rank = pepMatchRecord(PepMatchCols.RANK).asInstanceOf[Int],
        score = toFloat(pepMatchRecord(PepMatchCols.SCORE)),
        scoreType = scoreType,
        deltaMoz = toFloat(pepMatchRecord(PepMatchCols.DELTA_MOZ)),
        isDecoy = pepMatchRecord(PepMatchCols.IS_DECOY),
        peptide = peptide,
        missedCleavage = pepMatchRecord(PepMatchCols.MISSED_CLEAVAGE).asInstanceOf[Int],
        fragmentMatchesCount = pepMatchRecord(PepMatchCols.FRAGMENT_MATCH_COUNT).asInstanceOf[Int],
        msQuery = msQueryOpt.getOrElse(null),
        resultSetId = toLong(pepMatchRecord(PepMatchCols.RESULT_SET_ID)),
        properties = properties
      )

      pepMatches(pepMatchIdx) = pepMatch

    }

    pepMatches

  }

}