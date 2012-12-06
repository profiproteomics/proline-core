package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse
import fr.proline.core.dal.{SQLQueryHelper,MsiDbResultSummaryTable}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ResultSummaryProperties
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.util.sql.SQLStrToBool

class SQLResultSummaryProvider( val msiDb: SQLQueryHelper, val psDb: SQLQueryHelper ) extends SQLResultSetLoader with IResultSummaryProvider {
  
  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper( msiDb )
  
  val RSMCols = MsiDbResultSummaryTable.columns
  
  def getResultSummaries( rsmIds: Seq[Int], loadResultSet: Boolean ): Array[ResultSummary] = {
  
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    // Load peptide sets
    val pepSetProvider = new SQLPeptideSetProvider( msiDb, psDb )
    val pepSets = pepSetProvider.getResultSummariesPeptideSets( rsmIds )    
    val inMemPepSetProvider = new InMemoryPeptideSetProvider( pepSets )
    
    // Load protein sets
    val protSetProvider = new SQLProteinSetProvider( msiDb, psDb, Some(pepSetProvider) )
    val protSets = protSetProvider.getResultSummariesProteinSets( rsmIds )
    val protSetsByRsmId = protSets.groupBy( _.resultSummaryId )
    
    // Execute SQL query to load result sets
    var rsmColNames: Seq[String] = null
    val msiDbTx = msiDb.getOrCreateTransaction()
    val rsms = msiDbTx.select( "SELECT * FROM result_summary WHERE id IN ("+ rsmIds.mkString(",") +")" ) { r =>
              
      if( rsmColNames == null ) { rsmColNames = r.columnNames }
      val rsmRecord = rsmColNames.map( colName => ( colName -> r.nextObject.getOrElse(null) ) ).toMap
      
      // Retrieve some vars
      val rsmId: Int = rsmRecord(RSMCols.id).asInstanceOf[AnyVal]
      val rsmPepSets = inMemPepSetProvider.getResultSummaryPeptideSets(rsmId)
      val rsmPepInsts = rsmPepSets.flatMap { _.getPeptideInstances }      
      val rsmProtSets = protSetsByRsmId.getOrElse(rsmId, Array.empty[ProteinSet] )
            
      val decoyRsmId = rsmRecord.getOrElse(RSMCols.decoyResultSummaryId,0).asInstanceOf[Int]
      //val decoyRsmId = if( decoyRsmIdField != null ) decoyRsmIdField.asInstanceOf[Int] else 0

      val isQuantifiedField = rsmRecord(RSMCols.isQuantified)        
      val isQuantified = if( isQuantifiedField != null ) SQLStrToBool(isQuantifiedField.asInstanceOf[String]) else false

      // Decode JSON properties
      val propertiesAsJSON = rsmRecord(RSMCols.serializedProperties).asInstanceOf[String]
      var properties = Option.empty[ResultSummaryProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[ResultSummaryProperties](propertiesAsJSON) )
      }
      
      val rsId = rsmRecord(RSMCols.resultSetId).asInstanceOf[Int]
      var rsAsOpt = Option.empty[ResultSet]
      if( loadResultSet ) {
        
        val pepMatchProvider = new SQLPeptideMatchProvider( msiDb, psDb )
        val protMatchProvider = new SQLProteinMatchProvider( msiDb )
        
        val pepMatches = pepMatchProvider.getResultSummaryPeptideMatches( rsmId )
        val protMatches = protMatchProvider.getResultSummariesProteinMatches( Array(rsmId) )
        
        // Remove objects which are not linked to result summary
        // TODO: remove when the provider is fully implemented
        val protMatchIdSet = rsmPepSets.flatMap( _.proteinMatchIds ) toSet
        val rsmProtMatches = protMatches.filter { p => protMatchIdSet.contains(p.id) }
        
        rsAsOpt = Some( this.getResultSet( rsId, pepMatches, rsmProtMatches ) )
        
      }
      
      new ResultSummary(
            id = rsmId,
            description = rsmRecord(RSMCols.description).asInstanceOf[String],
            isQuantified = isQuantified,
            modificationTimestamp = new java.util.Date, // TODO: retrieve the date
            peptideInstances = rsmPepInsts,
            peptideSets = rsmPepSets,
            proteinSets = rsmProtSets,
            resultSetId = rsId,
            resultSet = rsAsOpt,
            decoyResultSummaryId = decoyRsmId,
            properties = properties
          )
                            
    }
    
    rsms.toArray
  
  }
    
  def getResultSummariesAsOptions( rsmIds: Seq[Int], loadResultSet: Boolean ): Array[Option[ResultSummary]] = {    
    val rsms = this.getResultSummaries( rsmIds, loadResultSet )
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;    
    rsmIds.map { rsmById.get( _ ) } toArray
  }
  
  def getResultSetsResultSummaries( rsIds: Seq[Int], loadResultSet: Boolean ): Array[ResultSummary] = {
    throw new Exception("NYI")
    null
  }
  
}