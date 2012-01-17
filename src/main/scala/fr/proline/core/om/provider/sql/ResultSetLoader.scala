package fr.proline.core.om.provider.sql

import net.noerd.prequel.DatabaseConfig

class ResultSetLoader( val msiDb: DatabaseConfig, val psDb: DatabaseConfig = null )  {
  
  import fr.proline.core.om.msi.ResultSetClasses._
  import fr.proline.core.om.helper.MsiDbHelper
  
  def getResultSet( rsId: Int ): ResultSet = { getResultSets( Array( rsId) )(0) }
  
  def getResultSets( rsIds: Seq[Int] ): Array[ResultSet] = {
    
    // Load protein matches
    val protMatchLoader = new ProteinMatchLoader( msiDb )
    val protMatches = protMatchLoader.getProteinMatches( rsIds )
    val protMatchesByRsId = protMatches.groupBy( _.resultSetId )
    
    // Load peptide matches
    val pepMatchLoader = new PeptideMatchLoader( msiDb, psDb )
    val pepMatches = pepMatchLoader.getPeptideMatches( rsIds )
    val pepMatchesByRsId = pepMatches.groupBy( _.resultSetId )
    
    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper( msiDb )
    
    // Execute SQL query to load result sets
    var rsColNames: Seq[String] = null
    val resultSets = msiDb.transaction { tx =>       
      tx.select( "SELECT * FROM result_set WHERE id IN (" +
                 rsIds.mkString(",") +")" ) { r =>
              
        if( rsColNames == null ) { rsColNames = r.columnNames }
        val resultSetRecord = rsColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        
        // Retrieve some vars
        val rsId = resultSetRecord("id").asInstanceOf[Int]
        val rsProtMatches = protMatchesByRsId(rsId)
        val rsPepMatches = pepMatchesByRsId(rsId)
        val rsPeptides = rsPepMatches map { _.peptide } distinct
        val rsType = resultSetRecord("type").asInstanceOf[String]
        val isDecoy = rsType matches "DECOY_SEARCH"
        val isNative = rsType matches "SEARCH"
        val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds( Array(rsId) )
        var decoyResultSetId: Int = 0
        if( resultSetRecord("decoy_result_set_id") != null ) decoyResultSetId = resultSetRecord("decoy_result_set_id").asInstanceOf[Int]
          
        new ResultSet(
              id = rsId,
              name = resultSetRecord("name").asInstanceOf[String],
              description = resultSetRecord("description").asInstanceOf[String],
              peptides = rsPeptides,
              peptideMatches = rsPepMatches,
              proteinMatches = rsProtMatches,
              isDecoy = isDecoy,
              isNative = isNative,
           //TODO   msiSearchIds = msiSearchIds,
              decoyResultSetId = decoyResultSetId
            )
                              
      }
    }
    
    resultSets.toArray

  }
  
  
  
}