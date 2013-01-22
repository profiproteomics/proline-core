package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbPeptideMatchTable
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PeptideMatchProperties
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.provider.msi.IPeptideMatchProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.repository.DatabaseContext

class SQLPeptideMatchProvider( val msiDbCtx: DatabaseContext,                               
                               val msiSqlExec: SQLQueryExecution,
                               val psDbCtx: DatabaseContext = null,
                               val psSqlExec: SQLQueryExecution = null,
                               var peptideProvider: Option[IPeptideProvider] = None ) extends IPeptideMatchProvider {
  
  import fr.proline.core.dal.helper.MsiDbHelper
  
  val PepMatchCols = MsiDbPeptideMatchTable.columns
  
  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper( this.msiSqlExec )
  
  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById
  
  private def _getPeptideProvider(): IPeptideProvider = {
    if( this.peptideProvider != None ) this.peptideProvider.get
    else new SQLPeptideProvider(psDbCtx,psSqlExec)
  }
  
  def getPeptideMatches( pepMatchIds: Seq[Int] ): Array[PeptideMatch] = {
    val pmRecords = _getPepMatchRecords( pepMatchIds )
    val rsIds = pmRecords.map { _(PepMatchCols.RESULT_SET_ID).asInstanceOf[Int] } .distinct
    this._buildPeptideMatches( rsIds, pmRecords )
  }
  
  def getPeptideMatchesAsOptions( pepMatchIds: Seq[Int] ): Array[Option[PeptideMatch]] = {
    
    val pepMatches = this.getPeptideMatches( pepMatchIds )
    val pepMatchById = pepMatches.map { pepMatch => pepMatch.id -> pepMatch } toMap
    
    pepMatchIds.map { pepMatchById.get( _ ) } toArray
  }
  
  def getResultSetsPeptideMatches( rsIds: Seq[Int] ): Array[PeptideMatch] = {
    
    val pmRecords = _getResultSetsPepMatchRecords( rsIds )
    this._buildPeptideMatches( rsIds, pmRecords )
    
  }
  
  def getResultSummariesPeptideMatches( rsmIds: Seq[Int] ): Array[PeptideMatch] = {
    
    val pmRecords = _getResultSummariesPepMatchRecords( rsmIds )
    val rsIds = pmRecords.map( _("result_set_id").asInstanceOf[Int] ).distinct
    this._buildPeptideMatches( rsIds, pmRecords )
    
  }
  
  /*def getPeptideMatches( rsIds: Seq[Int], peptides: Array[Peptide] ): Array[PeptideMatch] = {
    
    val pmRecords = _getResultSetsPepMatchRecords( rsIds )
    this._buildPeptideMatches( rsIds, pmRecords, peptides )
  }*/
  
  private def _getResultSetsPepMatchRecords( rsIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiSqlExec.selectAllRecordsAsMaps("SELECT * FROM peptide_match WHERE result_set_id IN (" + rsIds.mkString(",") +")")
  }
  
  private def _getResultSummariesPepMatchRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiSqlExec.selectAllRecordsAsMaps(
      "SELECT peptide_match.* FROM peptide_match, peptide_instance_peptide_match_map "+
      "WHERE peptide_match.id = peptide_instance_peptide_match_map.peptide_match_id "+
      "AND result_summary_id IN (" + rsmIds.mkString(",") +")"
    )
  }
  
  private def _getPepMatchRecords( pepMatchIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiSqlExec.selectAllRecordsAsMaps("SELECT * FROM peptide_match WHERE id IN (" + pepMatchIds.mkString(",") +")")    
  }
  
  private def _buildPeptideMatches( rsIds: Seq[Int], pmRecords: Seq[Map[String,Any]] ): Array[PeptideMatch] = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.primitives.DoubleOrFloatAsFloat._
    import fr.proline.util.sql.StringOrBoolAsBool._
   
    
    // Load peptides
    val uniqPepIds = pmRecords map { _(PepMatchCols.PEPTIDE_ID).asInstanceOf[Int] } distinct
    val peptides = this._getPeptideProvider().getPeptides(uniqPepIds)
    
    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => ( pep.id -> pep ) }
    
    // Load MS queries
    val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds( rsIds )
    val msQueries = new SQLMsQueryProvider( msiDbCtx, msiSqlExec ).getMsiSearchesMsQueries( msiSearchIds )
    val msQueryById = Map() ++ msQueries.map { msq => ( msq.id -> msq ) }
    
    // Load peptide matches
    val pepMatches = new Array[PeptideMatch](pmRecords.length)
    
    for( pepMatchIdx <- 0 until pmRecords.length ) {
      
      // Retrieve peptide match record
      val pepMatchRecord = pmRecords(pepMatchIdx)
      
      // Retrieve the corresponding peptide
      val pepId = pepMatchRecord(PepMatchCols.PEPTIDE_ID).asInstanceOf[Int]      
      if( ! peptideById.contains(pepId) ) {
        throw new Exception("undefined peptide with id ='"+pepId+"' "+
            "nb peps=" + peptides.length + 
            "nb pm="+pmRecords.length + "" +
        	" count= " + this.psSqlExec.selectInt("SELECT count(*) FROM peptide") )
      }
      val peptide = peptideById(pepId)
      
      // Retrieve the corresponding MS query
      val msQuery = msQueryById( pepMatchRecord(PepMatchCols.MS_QUERY_ID).asInstanceOf[Int] )
      
      // Retrieve some vars
      val scoreType = scoreTypeById( pepMatchRecord(PepMatchCols.SCORING_ID).asInstanceOf[Int] )
      
      // Decode JSON properties
      val propertiesAsJSON = pepMatchRecord(PepMatchCols.SERIALIZED_PROPERTIES).asInstanceOf[String]
      var properties = Option.empty[PeptideMatchProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[PeptideMatchProperties](propertiesAsJSON) )
      }
      
      val pepMatch = new PeptideMatch( id = pepMatchRecord(PepMatchCols.ID).asInstanceOf[AnyVal],
                                       rank = pepMatchRecord(PepMatchCols.RANK).asInstanceOf[Int],
                                       score = pepMatchRecord(PepMatchCols.SCORE).asInstanceOf[AnyVal],
                                       scoreType = scoreType,
                                       deltaMoz = pepMatchRecord(PepMatchCols.DELTA_MOZ).asInstanceOf[AnyVal],
                                       isDecoy = pepMatchRecord(PepMatchCols.IS_DECOY),
                                       peptide = peptide,
                                       missedCleavage = pepMatchRecord(PepMatchCols.MISSED_CLEAVAGE).asInstanceOf[Int],
                                       fragmentMatchesCount = pepMatchRecord(PepMatchCols.FRAGMENT_MATCH_COUNT).asInstanceOf[Int],
                                       msQuery = msQuery,
                                       resultSetId = pepMatchRecord(PepMatchCols.RESULT_SET_ID).asInstanceOf[Int],
                                       properties = properties
                                      )
      
      pepMatches(pepMatchIdx) = pepMatch
      
    }
    
    pepMatches
    
  }
  
}