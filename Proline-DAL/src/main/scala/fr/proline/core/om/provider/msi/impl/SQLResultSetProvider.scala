package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.dal.{MsiDb,PsDb,MsiDbResultSetTable}
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.provider.msi.IResultSetProvider

class SQLResultSetProvider( val msiDb: MsiDb,
                            val psDb: PsDb = null ) extends IResultSetProvider {
  
  import fr.proline.core.dal.helper.MsiDbHelper
  
  val RSCols = MsiDbResultSetTable.columns
  
  //def getResultSet( rsId: Int ): ResultSet = { getResultSets( Array( rsId) )(0) }
  
  def getResultSets( rsIds: Seq[Int] ): Array[ResultSet] = {
    
    // Load protein matches
    val protMatchProvider = new SQLProteinMatchProvider( msiDb )
    val protMatches = protMatchProvider.getResultSetsProteinMatches( rsIds )
    val protMatchesByRsId = protMatches.groupBy( _.resultSetId )
    
    // Load peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider( msiDb, psDb )
    val pepMatches = pepMatchProvider.getResultSetsPeptideMatches( rsIds )
    val pepMatchesByRsId = pepMatches.groupBy( _.resultSetId )
    
    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper( msiDb )
    
    // Execute SQL query to load result sets
    var rsColNames: Seq[String] = null
    val msiDbTx = msiDb.getOrCreateTransaction()
    val resultSets = msiDbTx.select( "SELECT * FROM result_set WHERE id IN ("+ rsIds.mkString(",") +")" ) { r =>
              
      if( rsColNames == null ) { rsColNames = r.columnNames }
      val resultSetRecord = rsColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      
      // Retrieve some vars
      val rsId = resultSetRecord(RSCols.id).asInstanceOf[Int]
      val rsProtMatches = protMatchesByRsId(rsId)
      val rsPepMatches = pepMatchesByRsId(rsId)
      val rsPeptides = rsPepMatches map { _.peptide } distinct
      val rsType = resultSetRecord(RSCols.`type`).asInstanceOf[String]
      val isDecoy = rsType matches "DECOY_SEARCH"
      val isNative = rsType matches "SEARCH"
      //val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds( Array(rsId) )
      var decoyResultSetId: Int = 0
      if( resultSetRecord(RSCols.decoyResultSetId) != null )
        decoyResultSetId = resultSetRecord(RSCols.decoyResultSetId).asInstanceOf[Int]
      
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
            decoyResultSetId = decoyResultSetId
          )
                            
    }
    
    resultSets.toArray

  }
  
  def getResultSetsAsOptions( resultSetIds: Seq[Int] ): Array[Option[ResultSet]] = {
    
    val resultSets = this.getResultSets( resultSetIds )
    val resultSetById = resultSets.map { rs => rs.id -> rs } toMap
    
    val optRsBuffer = new ArrayBuffer[Option[ResultSet]]
    resultSetIds.foreach { rsId =>
      optRsBuffer += resultSetById.get( rsId )
    }
    
    optRsBuffer.toArray
  }
  
}