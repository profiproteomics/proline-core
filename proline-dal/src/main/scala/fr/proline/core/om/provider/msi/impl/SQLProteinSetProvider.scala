package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbPeptideSetTable
import fr.proline.core.dal.tables.msi.MsiDbProteinSetProteinMatchItemTable
import fr.proline.core.dal.tables.msi.MsiDbProteinSetTable
import fr.proline.core.om.builder.ProteinSetBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.IPeptideSetProvider

class SQLProteinSetProvider(
  val msiDbCtx: MsiDbConnectionContext,
  val peptideSetProvider: IPeptideSetProvider
) {
  
  def this(peptideCacheExecContext: PeptideCacheExecutionContext) = {
    this(peptideCacheExecContext.getMSIDbConnectionContext, new SQLPeptideSetProvider(peptideCacheExecContext) )
  }

  def getProteinSetsAsOptions(protSetIds: Seq[Long]): Array[Option[ProteinSet]] = {
    if (protSetIds.isEmpty) return Array()

    val protSetById = this.getProteinSets(protSetIds).map { p => p.id -> p }.toMap;
    protSetIds.map { protSetById.get(_) }.toArray

  }

  def getProteinSets(protSetIds: Seq[Long]): Array[ProteinSet] = {
    if (protSetIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
    
      val pepSetIdQuery = new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
        List(t.ID) -> "WHERE "~ t.PROTEIN_SET_ID ~" IN("~ protSetIds.mkString(",") ~")"
      )
  
      val peptideSetIds = msiEzDBC.select(pepSetIdQuery) { v => toLong(v.nextAny) }
      val peptideSets = this.peptideSetProvider.getPeptideSets(peptideSetIds)
  
      ProteinSetBuilder.buildProteinSets(
        this._getProtSetRecords(msiEzDBC,protSetIds),
        this._getProtSetItemRecords(msiEzDBC,protSetIds),
        peptideSets
      )
      
    }
  }

  def getResultSummariesProteinSets(rsmIds: Seq[Long]): Array[ProteinSet] = {
    if (rsmIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      ProteinSetBuilder.buildProteinSets(
        this._getRSMsProtSetRecords(msiEzDBC,rsmIds),
        this._getRSMsProtSetItemRecords(msiEzDBC,rsmIds),
        this.peptideSetProvider.getResultSummariesPeptideSets(rsmIds)
      )
    }
  }

  private def _getRSMsProtSetRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[AnyMap] = {
    val protSetQuery = new SelectQueryBuilder1(MsiDbProteinSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecords(protSetQuery)
  }

  private def _getProtSetRecords(msiEzDBC: EasyDBC, protSetIds: Seq[Long]): Array[AnyMap] = {
    // TODO: use max nb iterations
    val protSetQuery = new SelectQueryBuilder1(MsiDbProteinSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ protSetIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecords(protSetQuery)
  }

  private def _getRSMsProtSetItemRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[AnyMap] = {    
    val protSetItemQuery = new SelectQueryBuilder1(MsiDbProteinSetProteinMatchItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecords(protSetItemQuery)
  }

  private def _getProtSetItemRecords(msiEzDBC: EasyDBC, protSetIds: Seq[Long]): Array[AnyMap] = {
    // TODO: use max nb iterations
    val protSetItemQuery = new SelectQueryBuilder1(MsiDbProteinSetProteinMatchItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PROTEIN_SET_ID ~" IN("~ protSetIds.mkString(",") ~")"
    )
    msiEzDBC.selectAllRecords(protSetItemQuery)
  }



}