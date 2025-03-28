package fr.proline.core.om.provider.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstancePeptideMatchMapTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstanceTable
import fr.proline.core.om.builder.PeptideInstanceBuilder
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import fr.proline.core.om.provider.msi.IPeptideProvider

class SQLPeptideInstanceProvider(
  val msiDbCtx: MsiDbConnectionContext,
  val peptideProvider: IPeptideProvider
) extends IPeptideInstanceProvider with LazyLogging {
  
  def this(peptideCacheExecContext: PeptideCacheExecutionContext) = {
    this(peptideCacheExecContext.getMSIDbConnectionContext, new SQLPeptideProvider(peptideCacheExecContext) )
  }

  def getPeptideInstancesAsOptions(pepInstIds: Seq[Long]): Array[Option[PeptideInstance]] = {
    if( pepInstIds.isEmpty ) return Array()
    
    val pepInsts = this.getPeptideInstances(pepInstIds)
    val pepInstById = pepInsts.map { p => p.id -> p }.toMap

    pepInstIds.map { pepInstById.get(_) }.toArray
  }

  def getPeptideInstances(pepInstIds: Seq[Long]): Array[PeptideInstance] = {
    if( pepInstIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      // TODO: use max nb iterations
      val sqlQuery1 = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepInstIds.mkString(",") ~")"
      )
      val pepInstRecords = msiEzDBC.selectAllRecords(sqlQuery1)
      
      this._getPeptideInstances(msiEzDBC,pepInstIds,pepInstRecords)
    }
  }
  
  def getPeptideInstancesByPeptideIds(pepIds: Seq[Long]): Array[PeptideInstance] = {
    if( pepIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      // TODO: use max nb iterations
      val sqlQuery1 = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.PEPTIDE_ID ~" IN("~ pepIds.mkString(",") ~")"
      )
      val pepInstRecords = msiEzDBC.selectAllRecords(sqlQuery1)
      val pepInstIds = pepInstRecords.map( _("id").asInstanceOf[Long] )
      
      this._getPeptideInstances(msiEzDBC,pepInstIds,pepInstRecords)
    }
  }
  
  private def _getPeptideInstances(
    msiEzDBC: EasyDBC,
    pepInstIds: Seq[Long],
    pepInstRecords: Array[AnyMap]
  ): Array[PeptideInstance] = {
    
    val sqlQuery2 = new SelectQueryBuilder1(MsiDbPeptideInstancePeptideMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_INSTANCE_ID ~" IN("~ pepInstIds.mkString(",") ~")"
    )
    val pepInstPepMatchMapRecords = msiEzDBC.selectAllRecords(sqlQuery2)
    
    PeptideInstanceBuilder.buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords, peptideProvider)
  }

  def getResultSummariesPeptideInstances(rsmIds: Seq[Long]): Array[PeptideInstance] = {
    if( rsmIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      
      // TODO: use max nb iterations
      val sqlQuery1 = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
      )
      val pepInstRecords = msiEzDBC.selectAllRecords(sqlQuery1)
      
      val sqlQuery2 = new SelectQueryBuilder1(MsiDbPeptideInstancePeptideMatchMapTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
      )
      val pepInstPepMatchMapRecords = msiEzDBC.selectAllRecords(sqlQuery2)
  
      PeptideInstanceBuilder.buildPeptideInstances(pepInstRecords, pepInstPepMatchMapRecords, peptideProvider)
      
    }
  }
  
}
