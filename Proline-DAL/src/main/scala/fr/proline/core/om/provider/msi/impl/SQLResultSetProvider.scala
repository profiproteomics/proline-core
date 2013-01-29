package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.SQLContext
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.model.msi.{ProteinMatch,PeptideMatch,ResultSet}
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.context.DatabaseConnectionContext
import fr.profi.jdbc.easy.EasyDBC

trait SQLResultSetLoader {
  
  import fr.proline.core.dal.helper.MsiDbHelper  
  
  val udsSqlCtx: SQLContext
  val msiDbCtx: DatabaseConnectionContext
  val psDbCtx: DatabaseConnectionContext  
  val msiSqlExec: EasyDBC
  val psSqlExec: EasyDBC
  
  val msiSqlCtx = new SQLContext( msiDbCtx, msiSqlExec )
  val psSqlCtx = new SQLContext( psDbCtx, psSqlExec )
  
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
    val msiDbHelper = new MsiDbHelper( msiSqlExec )
    val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds( rsIds )
    
    var msiSearchById: Map[Int,fr.proline.core.om.model.msi.MSISearch] = Map()
    if( udsSqlCtx != null ) {
      val msiSearches = new SQLMsiSearchProvider(udsSqlCtx,msiSqlCtx,psSqlCtx).getMSISearches(msiSearchIds)
      msiSearchById = Map() ++ msiSearches.map( ms => ms.id -> ms )
    }
    
    // Execute SQL query to load result sets
    var rsColNames: Seq[String] = null
    val resultSets = msiSqlExec.select( "SELECT * FROM result_set WHERE id IN ("+ rsIds.mkString(",") +")" ) { r =>
              
      if( rsColNames == null ) { rsColNames = r.columnNames }
      val resultSetRecord = rsColNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
      
      // Retrieve some vars
      val rsId: Int = resultSetRecord(RSCols.ID).asInstanceOf[AnyVal]
      val rsProtMatches = protMatchesByRsId(rsId)
      val rsPepMatches = pepMatchesByRsId(rsId)
      val rsPeptides = rsPepMatches map { _.peptide } distinct
      val rsType = resultSetRecord(RSCols.TYPE).asInstanceOf[String]
      val isDecoy = rsType matches "DECOY_SEARCH"
      val isNative = rsType matches "SEARCH"
      val msiSearchId = resultSetRecord(RSCols.MSI_SEARCH_ID).asInstanceOf[Int]
      val msiSearch = msiSearchById.getOrElse(msiSearchId,null)
      
      var decoyRsId: Int = 0
      if( resultSetRecord(RSCols.DECOY_RESULT_SET_ID) != null )
        decoyRsId = resultSetRecord(RSCols.DECOY_RESULT_SET_ID).asInstanceOf[Int]
      
      // TODO: parse properties
      new ResultSet(
            id = rsId,
            name = resultSetRecord(RSCols.NAME).asInstanceOf[String],
            description = resultSetRecord(RSCols.DESCRIPTION).asInstanceOf[String],
            peptides = rsPeptides,
            peptideMatches = rsPepMatches,
            proteinMatches = rsProtMatches,
            isDecoy = isDecoy,
            isNative = isNative,
            msiSearch = msiSearch,
            decoyResultSetId = decoyRsId
          )
    }
    
    resultSets.toArray

  }

}

class SQLResultSetProvider( val msiDbCtx: DatabaseConnectionContext,
                            val msiSqlExec: EasyDBC,
                            val psDbCtx: DatabaseConnectionContext,
                            val psSqlExec: EasyDBC,
                            val udsSqlCtx: SQLContext ) extends SQLResultSetLoader with IResultSetProvider {
  
  val udsDbCtx: DatabaseConnectionContext = udsSqlCtx.dbContext
  val pdiDbCtx: DatabaseConnectionContext = null
  
  def getResultSets( rsIds: Seq[Int] ): Array[ResultSet] = {
    
    // Load peptide matches
    val pepMatchProvider = new SQLPeptideMatchProvider( msiDbCtx, msiSqlExec, psDbCtx, psSqlExec )
    val pepMatches = pepMatchProvider.getResultSetsPeptideMatches( rsIds )
    
    // Load protein matches
    val protMatchProvider = new SQLProteinMatchProvider( msiDbCtx, msiSqlExec )
    val protMatches = protMatchProvider.getResultSetsProteinMatches( rsIds )    

    this.getResultSets( rsIds, pepMatches, protMatches )

  }
  
  def getResultSetsAsOptions( resultSetIds: Seq[Int] ): Array[Option[ResultSet]] = {
    
    val resultSets = this.getResultSets( resultSetIds )
    val resultSetById = resultSets.map { rs => rs.id -> rs } toMap
    
    resultSetIds.map { resultSetById.get( _ ) } toArray
    
  }
  
  
  

  
}