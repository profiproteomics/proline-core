package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.proline.context._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.builder.PeptideSetBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.{ IPeptideSetProvider, IPeptideInstanceProvider }
import fr.proline.repository.ProlineDatabaseType

class SQLPeptideSetProvider(
  val msiDbCtx: MsiDbConnectionContext,
  val peptideInstanceProvider: IPeptideInstanceProvider
) extends IPeptideSetProvider {
  
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  def this(msiDbCtx: MsiDbConnectionContext, psDbCtx: DatabaseConnectionContext) {
    this(msiDbCtx, new SQLPeptideInstanceProvider(msiDbCtx,psDbCtx) )
  }
  
  val PepSetItemCols = MsiDbPeptideSetPeptideInstanceItemTable.columns

  // Instantiate a MSIdb helper
  val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // Retrieve score type map
  val scoreTypeById = msiDbHelper.getScoringTypeById

  def getPeptideSetsAsOptions(pepSetIds: Seq[Long]): Array[Option[PeptideSet]] = {
    if (pepSetIds.isEmpty) return Array()

    val pepSets = this.getPeptideSets(pepSetIds)
    val pepSetById = pepSets.map { p => p.id -> p } toMap

    pepSetIds.map { pepSetById.get(_) } toArray

  }

  def getPeptideSets(pepSetIds: Seq[Long]): Array[PeptideSet] = {
    if (pepSetIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
    
      val pepSetItemRecords = this._getPepSetItemRecords(msiEzDBC,pepSetIds)
      val pepInstIds = pepSetItemRecords.map { v => toLong(v(PepSetItemCols.PEPTIDE_INSTANCE_ID)) } distinct
  
      PeptideSetBuilder.buildPeptideSets(
        this._getPepSetRecords(msiEzDBC,pepSetIds),
        this._getPepSetRelationRecords(msiEzDBC,pepSetIds),
        pepSetItemRecords,
        this.peptideInstanceProvider.getPeptideInstances(pepInstIds),
        this._getPepSetProtMatchMapRecords(msiEzDBC,pepSetIds),
        scoreTypeById
      )
    
    }
  }

  def getResultSummariesPeptideSets(rsmIds: Seq[Long]): Array[PeptideSet] = {
    if (rsmIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
    
      PeptideSetBuilder.buildPeptideSets(
        this._getRSMsPepSetRecords(msiEzDBC,rsmIds),
        this._getRSMsPepSetRelationRecords(msiEzDBC,rsmIds),
        this._getRSMsPepSetItemRecords(msiEzDBC,rsmIds),
        this.peptideInstanceProvider.getResultSummariesPeptideInstances(rsmIds),
        this._getRSMsPepSetProtMatchMapRecords(msiEzDBC,rsmIds),
        scoreTypeById
      )
      
    }
  }

  private def _getRSMsPepSetRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[AnyMap] = {
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[AnyMap] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetRelationRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[AnyMap] = {
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetRelationTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetRelationRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[AnyMap] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetRelationTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_OVERSET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetItemRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[AnyMap] = {
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetPeptideInstanceItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetItemRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[AnyMap] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetPeptideInstanceItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_SET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }

  private def _getRSMsPepSetProtMatchMapRecords(msiEzDBC: EasyDBC, rsmIds: Seq[Long]): Array[AnyMap] = {
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.RESULT_SUMMARY_ID ~" IN("~ rsmIds.mkString(",") ~")"
    ) )
  }

  private def _getPepSetProtMatchMapRecords(msiEzDBC: EasyDBC, pepSetIds: Seq[Long]): Array[AnyMap] = {
    // TODO: use max nb iterations
    msiEzDBC.selectAllRecords( new SelectQueryBuilder1(MsiDbPeptideSetProteinMatchMapTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.PEPTIDE_SET_ID ~" IN("~ pepSetIds.mkString(",") ~")"
    ) )
  }



}