package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.dal.{SQLQueryHelper,MsiDbResultSetTable}
import fr.proline.core.om.model.msi.{ProteinMatch,PeptideMatch,ResultSet}
import fr.proline.core.om.provider.msi.IResultSetProvider

trait SQLResultSetLoader {
  
  import fr.proline.core.dal.helper.MsiDbHelper  
  
  val msiDb: SQLQueryHelper  
  val RSCols = MsiDbResultSetTable.columns
  
  protected def getResultSet( rsId: Int,
                              pepMatches: Array[PeptideMatch],
                              protMatches: Array[ProteinMatch]
                             ): ResultSet = {
    this.getResultSets( Array(rsId), pepMatches, protMatches )(0)
  }
  
  protected def getResultSets( rsIds: Seq[Int],
                               pepMatches: Array[PeptideMatch],
                               protMatches: Array[ProteinMatch]
                             ): Array[ResultSet] = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    // Build some maps
    val pepMatchesByRsId = pepMatches.groupBy( _.resultSetId )
    val protMatchesByRsId = protMatches.groupBy( _.resultSetId )    
    
    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper( msiDb )
    
    // Execute SQL query to load result sets
    var rsColNames: Seq[String] = null
    val msiDbTx = msiDb.getOrCreateTransaction()
    val resultSets = msiDbTx.select( "SELECT * FROM result_set WHERE id IN ("+ rsIds.mkString(",") +")" ) { r =>
              
      if( rsColNames == null ) { rsColNames = r.columnNames }
      val resultSetRecord = rsColNames.map( colName => ( colName -> r.nextObject.getOrElse(null) ) ).toMap
      
      // Retrieve some vars
      val rsId: Int = resultSetRecord(RSCols.id).asInstanceOf[AnyVal]
      val rsProtMatches = protMatchesByRsId(rsId)
      val rsPepMatches = pepMatchesByRsId(rsId)
      val rsPeptides = rsPepMatches map { _.peptide } distinct
      val rsType = resultSetRecord(RSCols.`type`).asInstanceOf[String]
      val isDecoy = rsType matches "DECOY_SEARCH"
      val isNative = rsType matches "SEARCH"
      //val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds( Array(rsId) )
      var decoyRsId: Int = 0
      if( resultSetRecord(RSCols.decoyResultSetId) != null )
        decoyRsId = resultSetRecord(RSCols.decoyResultSetId).asInstanceOf[Int]
      
      // TODO: load MSI search
      // TODO: parse properties
      new ResultSet(
            id = rsId,
            name = resultSetRecord(RSCols.name).asInstanceOf[String],
            description = resultSetRecord(RSCols.description).asInstanceOf[String],
            peptides = rsPeptides,
            peptideMatches = rsPepMatches,
            proteinMatches = rsProtMatches,
            isDecoy = isDecoy,
            isNative = isNative,
         //TODO   msiSearchIds = msiSearchIds,
            decoyResultSetId = decoyRsId
          )
                            
    }
    
    resultSets.toArray

  }

}

class SQLResultSetProvider( val msiDb: SQLQueryHelper,
                            val psDb: SQLQueryHelper = null ) extends SQLResultSetLoader with IResultSetProvider {
  
  def getResultSets( rsIds: Seq[Int] ): Array[ResultSet] = {
    
    // Load peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider( msiDb, psDb )
    val pepMatches = pepMatchProvider.getResultSetsPeptideMatches( rsIds )    
    
    // Load protein matches
    val protMatchProvider = new SQLProteinMatchProvider( msiDb )
    val protMatches = protMatchProvider.getResultSetsProteinMatches( rsIds )    

    this.getResultSets( rsIds, pepMatches, protMatches )

  }
  
  def getResultSetsAsOptions( resultSetIds: Seq[Int] ): Array[Option[ResultSet]] = {
    
    val resultSets = this.getResultSets( resultSetIds )
    val resultSetById = resultSets.map { rs => rs.id -> rs } toMap
    
    resultSetIds.map { resultSetById.get( _ ) } toArray
    
  }
  
  
  

  
}