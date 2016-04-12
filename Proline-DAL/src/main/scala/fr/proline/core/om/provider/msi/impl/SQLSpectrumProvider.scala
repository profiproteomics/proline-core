package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.bytes._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.builder.SpectrumBuilder
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.provider.msi.ISpectrumProvider
import fr.proline.repository.ProlineDatabaseType

class SQLSpectrumProvider(val msiDbCtx: DatabaseConnectionContext) extends ISpectrumProvider {

  val SpecCols = MsiDbSpectrumTable.columns

  require(msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")

  def getSpectra(spectrumIds: Seq[Long], loadPeaks: Boolean = true): Array[Spectrum] = {
    if (spectrumIds.isEmpty) return Array()

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      val spectrumIdsAsStr = spectrumIds.mkString(",")
      val whereClause = "WHERE " ~ SpecCols.ID ~ " IN(" ~ spectrumIdsAsStr ~ ")"
      val specQuery = _buildSqlQuery(whereClause, loadPeaks)

      SpectrumBuilder.buildSpectra(msiEzDBC.select(specQuery), loadPeaks).toArray

    }, false)
  }
  
  def getPeaklistsSpectra( peaklistIds: Seq[Long], loadPeaks: Boolean = true ): Array[Spectrum] = {
    if (peaklistIds.isEmpty) return Array()

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      val whereClause = "WHERE " ~ peaklistIds.map(id => "" ~ SpecCols.PEAKLIST_ID ~ s"=$id").mkString(" OR ")
      val spQuery = _buildSqlQuery(whereClause, loadPeaks)

      SpectrumBuilder.buildSpectra(msiEzDBC.select(spQuery), loadPeaks).toArray

    }, false)
  }
  
  def foreachPeaklistSpectrum( peaklistId: Long, loadPeaks: Boolean = true )( onEachSpectrum: Spectrum => Unit ) {
    this.foreachPeaklistsSpectrum(List(peaklistId), loadPeaks)(onEachSpectrum)
  }
  
  def foreachPeaklistsSpectrum( peaklistIds: Seq[Long], loadPeaks: Boolean = true )( onEachSpectrum: Spectrum => Unit ) {
    if (peaklistIds.isEmpty) return ()

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      val whereClause = "WHERE " ~ peaklistIds.map(id => "" ~ SpecCols.PEAKLIST_ID ~ s"=$id").mkString(" OR ")
      val spQuery = _buildSqlQuery(whereClause, loadPeaks)

      msiEzDBC.select(spQuery) { r =>
        onEachSpectrum(SpectrumBuilder.buildSpectrum(r, loadPeaks))
      }

    }, false)
  }
  
  private def _buildSqlQuery(whereClause: String, loadPeaks: Boolean): String = {
    if (loadPeaks) {
      new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery((t, c) => List(t.*) -> whereClause)
    } else {
      new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery((t, cols) =>
        cols.filter(c => c != t.MOZ_LIST && c != t.INTENSITY_LIST) -> whereClause
      )
    }
  }
  
}