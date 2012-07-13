package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse
import fr.proline.core.dal.{MsiDb,PsDb,MsiDbResultSummaryTable}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ResultSummaryProperties
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.utils.sql.SQLStrToBool

class SQLResultSummaryProvider( val msiDb: MsiDb, val psDb: PsDb ) extends IResultSummaryProvider {
  
  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper( msiDb )
  
  val RSMCols = MsiDbResultSummaryTable.columns
  
  def getResultSummaries( rsmIds: Seq[Int], loadResultSet: Boolean ): Array[ResultSummary] = {
  
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
      val rsmRecord = rsmColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      
      // Retrieve some vars
      val rsmId = rsmRecord(RSMCols.id).asInstanceOf[Int]
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
      
      val rsId = rsmRecord(RSMCols.description).asInstanceOf[Int]
      var resultSet = Option.empty[ResultSet]
      if( loadResultSet ) {
        val rsProvider = new SQLResultSetProvider( msiDb, psDb )
        resultSet = rsProvider.getResultSet(rsId)
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
            resultSet = resultSet,
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