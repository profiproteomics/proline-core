package fr.proline.core.om.provider.msi.impl

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
  
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  def getSpectra( spectrumIds: Seq[Long] ): Array[Spectrum] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
    	val spectrumIdsAsStr = spectrumIds.mkString(",")
    	
    	val spQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ spectrumIdsAsStr ~ ")"
    	)
    	
    	SpectrumBuilder.buildSpectra( msiEzDBC.select(spQuery) ).toArray
    	
    }, false)  
  }
  
}