package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.provider.msi.IPeaklistSoftwareProvider
import fr.proline.util.primitives._
 
// TODO: use Select Query builder
class SQLPeaklistSoftwareProvider(val dbCtx: DatabaseConnectionContext) extends IPeaklistSoftwareProvider {
  
  def getPeaklistSoftwareListAsOptions( pklSoftIds: Seq[Int] ): Array[Option[PeaklistSoftware]] = {
    val pklSoftById = Map() ++ this.getPeaklistSoftwareList(pklSoftIds).map( ps => ps.id -> ps )
    pklSoftIds.toArray.map( pklSoftById.get(_) )
  }
  
  def getPeaklistSoftwareList( pklSoftIds: Seq[Int] ): Array[PeaklistSoftware] = {

    import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable

    DoJDBCReturningWork.withEzDBC(dbCtx, { ezDBC =>

      ezDBC.select("SELECT id, name, version FROM peaklist_software WHERE id IN(" + pklSoftIds.mkString(",") +")") { r =>
        new PeaklistSoftware(id = toInt(r.nextAnyVal), name = r.nextString, version = r.nextStringOrElse(""))
      } toArray
      
    })

  }
  
  def getPeaklistSoftware( softName: String, softVersion: String ): Option[PeaklistSoftware] = {
    
    DoJDBCReturningWork.withEzDBC(dbCtx, { udsEzDBC =>
      udsEzDBC.selectHeadOption(
        "SELECT * FROM peaklist_software WHERE name= ? and version= ? ", softName, softVersion)(r =>
          new PeaklistSoftware(id = r, name = r, version = r)
        )
    })
  }

}

