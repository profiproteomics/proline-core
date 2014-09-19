package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.om.builder.PeaklistSoftwareBuilder
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.provider.msi.IPeaklistSoftwareProvider

class SQLPeaklistSoftwareProvider(val dbCtx: DatabaseConnectionContext) extends IPeaklistSoftwareProvider {
  
  def getPeaklistSoftwareListAsOptions( pklSoftIds: Seq[Long] ): Array[Option[PeaklistSoftware]] = {
    if( pklSoftIds.isEmpty ) return Array()
    
    val pklSoftById = Map() ++ this.getPeaklistSoftwareList(pklSoftIds).map( ps => ps.id -> ps )
    pklSoftIds.toArray.map( pklSoftById.get(_) )
  }
  
  def getPeaklistSoftwareList(pklSoftIds: Seq[Long]): Array[PeaklistSoftware] = {
    if( pklSoftIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(dbCtx, { msiEzDBC =>
      PeaklistSoftwareBuilder.buildPeaklistSoftwareList( SQLPeaklistSoftwareProvider.selectPklSoftRecords(msiEzDBC,pklSoftIds) )
    })
  }
  
  def getPeaklistSoftware( softName: String, softVersion: String ): Option[PeaklistSoftware] = {
    
    DoJDBCReturningWork.withEzDBC(dbCtx, { udsEzDBC =>
      SQLPeaklistSoftwareProvider.selectPklSoftRecord(udsEzDBC,softName,softVersion) { r =>
        PeaklistSoftwareBuilder.buildPeaklistSoftware(r)
      }
    })
  }

}

object SQLPeaklistSoftwareProvider {
  
  def selectPklSoftRecords(ezDBC: EasyDBC, pklSoftIds: Seq[Long]): (IValueContainer => PeaklistSoftware) => Seq[PeaklistSoftware] = {
    
    val pklSoftQuery = new SelectQueryBuilder1(MsiDbPeaklistSoftwareTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ pklSoftIds.mkString(",") ~")"
    )

    ezDBC.select(pklSoftQuery)
  }
  
  def selectPklSoftRecord(ezDBC: EasyDBC, softName: String, softVersion: String): (IValueContainer => PeaklistSoftware) => Option[PeaklistSoftware] = {
    
    // "SELECT * FROM peaklist_software WHERE name = ? and version = ? ", softName, softVersion
    val pklSoftQuery = new SelectQueryBuilder1(MsiDbPeaklistSoftwareTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.NAME ~ s" = '${softName}' AND " ~ t.VERSION ~ s" = '${softVersion}'"
    )

    ezDBC.selectHeadOption( pklSoftQuery )
  }
  
}


