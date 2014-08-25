package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbPeaklistTable
import fr.proline.core.om.builder.PeaklistBuilder
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.repository.ProlineDatabaseType

/**
 * @author David Bouyssie
 *
 */
class SQLPeaklistProvider(val msiDbCtx: DatabaseConnectionContext) {
  require( msiDbCtx.getProlineDatabaseType == ProlineDatabaseType.MSI, "MsiDb connection required")
  
  def getPeaklists(peaklistIds: Seq[Long]): Array[Peaklist] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>

      PeaklistBuilder.buildPeaklists(
        SQLPeaklistProvider.selectPeaklistRecords(msiEzDBC,peaklistIds),
        pklSoftIds => SQLPeaklistSoftwareProvider.selectPklSoftRecords(msiEzDBC,pklSoftIds)
      )
      
    })

  }
  

}

object SQLPeaklistProvider {

  def selectPeaklistRecords(msiEzDBC: EasyDBC, peaklistIds: Seq[Long]): (IValueContainer => Peaklist) => Seq[Peaklist] = {
    
    val pklQuery = new SelectQueryBuilder1(MsiDbPeaklistTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ peaklistIds.mkString(",") ~")"
    )

    msiEzDBC.select(pklQuery)
  }
  
}


  