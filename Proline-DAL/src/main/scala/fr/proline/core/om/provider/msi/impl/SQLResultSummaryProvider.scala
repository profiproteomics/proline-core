package fr.proline.core.om.provider.msi.impl

import fr.profi.util.serialization.ProfiJson
import fr.profi.util.misc.MapIfNotNull
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ResultSummaryProperties
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.context.DatabaseConnectionContext

class SQLResultSummaryProvider(
  val msiDbCtx: DatabaseConnectionContext,
  val psDbCtx: DatabaseConnectionContext,
  val udsDbCtx: DatabaseConnectionContext = null
) extends SQLResultSetLoader with IResultSummaryProvider {

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx)

  val RSMCols = MsiDbResultSummaryTable.columns

  def getResultSummaries(rsmIds: Seq[Long], loadResultSet: Boolean): Array[ResultSummary] = {
    if (rsmIds.isEmpty) return Array()

    import fr.profi.util.primitives._
    import fr.profi.util.sql.StringOrBoolAsBool._

    val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
    val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)
    
    // Load peptide sets
    val pepSetProvider = new SQLPeptideSetProvider(msiDbCtx, psDbCtx)
    val pepSets = pepSetProvider.getResultSummariesPeptideSets(rsmIds)
    val inMemPepSetProvider = new InMemoryPeptideSetProvider(pepSets)

    // Load protein sets
    val protSetProvider = new SQLProteinSetProvider(msiDbCtx, inMemPepSetProvider)
    val protSets = protSetProvider.getResultSummariesProteinSets(rsmIds)
    val protSetsByRsmId = protSets.groupBy(_.resultSummaryId)

    // Execute SQL query to load result sets
    val rsmQuery = new SelectQueryBuilder2(MsiDbResultSummaryTable,MsiDbResultSetTable).mkSelectQuery( (t1,c1,t2,c2) =>
      List(t1.*,t2.TYPE) -> "WHERE "~ t1.ID ~" IN("~ rsmIds.mkString(",") ~") AND "~ t1.RESULT_SET_ID ~"="~ t2.ID
    )

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>      
    
      // TODO: load all result sets at once to avoid duplicated entities and for a faster loading
      val rsms = msiEzDBC.select(rsmQuery) { r =>
    
        // Retrieve some vars
        val rsmId: Long = toLong(r.getAny(RSMCols.ID))
        val rsmPepSets = inMemPepSetProvider.getResultSummaryPeptideSets(rsmId)
        val rsmPepInsts = rsmPepSets.flatMap(_.getPeptideInstances).distinct
        val rsmProtSets = protSetsByRsmId.getOrElse(rsmId, Array.empty[ProteinSet])
    
        val decoyRsmId = r.getLongOrElse(RSMCols.DECOY_RESULT_SUMMARY_ID, 0L)
        //val decoyRsmId = if( decoyRsmIdField != null ) decoyRsmIdField.asInstanceOf[Int] else 0
        
        // Check if the result summary corresponds to a decoy result set
        val rsType = r.getString(MsiDbResultSetTable.columns.TYPE)
        val isQuantified = r.getBooleanOrElse(RSMCols.IS_QUANTIFIED,false)
        
        // Decode JSON properties
        val propertiesAsJSON = r.getString(RSMCols.SERIALIZED_PROPERTIES)
        val properties = MapIfNotNull(propertiesAsJSON) { ProfiJson.deserialize[ResultSummaryProperties](_) }
        
        val rsId = toLong(r.getAny(RSMCols.RESULT_SET_ID))
        
        var rsAsOpt = Option.empty[ResultSet]
        if (loadResultSet) {
    
          val pepMatches = pepMatchProvider.getResultSummaryPeptideMatches(rsmId)
          val protMatches = protMatchProvider.getResultSummariesProteinMatches(Array(rsmId))
          
          // TODO: remove these two commented and obsolete workarounds when all tests are ok
          
          // tries to fix redundant protein matches
          //val distinctProtMatches = protMatches.groupBy( _.id ).map( _._2.head )
          
          // Remove objects which are not linked to result summary
          //val protMatchIdSet = rsmPepSets.flatMap(_.proteinMatchIds) toSet
          //val rsmProtMatches = distinctProtMatches.filter( p => protMatchIdSet.contains(p.id) ).toArray
          
          val isValidatedContent = true
          
          rsAsOpt = Some(this.getResultSet(rsId, isValidatedContent, pepMatches, protMatches))
        }
    
        new ResultSummary(
          id = rsmId,
          description = r.getString(RSMCols.DESCRIPTION),
          isQuantified = isQuantified,
          modificationTimestamp = r.getDate(RSMCols.MODIFICATION_TIMESTAMP),
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
      
    })

  }

  def getResultSummariesAsOptions(rsmIds: Seq[Long], loadResultSet: Boolean): Array[Option[ResultSummary]] = {
    if (rsmIds.isEmpty) return Array()
    
    val rsms = this.getResultSummaries(rsmIds, loadResultSet)
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;
    rsmIds.map { rsmById.get(_) } toArray
  }

  def getResultSetsResultSummaries(rsIds: Seq[Long], loadResultSet: Boolean): Array[ResultSummary] = {
    if (rsIds.isEmpty) return Array()
    
    throw new Exception("NYI")
    null
  }

}