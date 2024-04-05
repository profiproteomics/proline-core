package fr.proline.core.om.util

import fr.proline.context.{MsiDbConnectionContext, UdsDbConnectionContext}
import fr.proline.core.dal.tables.msi.{MsiDbPeaklistSoftwareColumns, MsiDbPeaklistSoftwareTable}
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.provider.msi.impl.{SQLPeaklistSoftwareProvider => MsiSQLPklSoftProvider}
import fr.proline.core.om.provider.uds.impl.{SQLPeaklistSoftwareProvider => UdsSQLPklSoftProvider}
import fr.proline.repository.util.JDBCWork

import java.sql.Connection
object PeaklistSoftwareCopyUtil {

  def getOrCopyPeaklistSoftware(peaklistSoftwareId: Long,  msiDbCtx: MsiDbConnectionContext, udsDbCtx: UdsDbConnectionContext) : PeaklistSoftware  = {

    val msiPklSoftProvider = new MsiSQLPklSoftProvider(msiDbCtx)
    val udsPklSoftProvider = new UdsSQLPklSoftProvider(udsDbCtx)

    val udsPklSoftOpt = udsPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
    require(udsPklSoftOpt.isDefined, "can't find a peaklist software for id = " + peaklistSoftwareId)

    // Try to retrieve peaklist software from the MSidb
    val msiPklSoftOpt = msiPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
    if (msiPklSoftOpt.isEmpty) {

      // If it doesn't exist => retrieve from the UDSdb
      val pklSoft = udsPklSoftOpt.get
      //Convert read properies to JSON Compatible string
      var propAsString: Option[String] = None
      if (pklSoft.properties.isDefined && pklSoft.properties.get.pifRegExp.isDefined) {
        propAsString = Some("{\"pif_reg_exp\":\"" + pklSoft.properties.get.pifRegExp.get + "\"}")
      }

      //VDS TODO WART Until found better way to deal with regex and json serialisation
      if(propAsString.isDefined) {
        // Then insert it in the current MSIdb
        val jdbcWork = new JDBCWork {

          override def execute(con: Connection) {

            val pStmt = con.prepareStatement("insert into " + MsiDbPeaklistSoftwareTable.name + " (" + MsiDbPeaklistSoftwareTable.columnsAsStrList.mkString(",") + ") VALUES (?,?,?,?)")
            pStmt.setLong(1, pklSoft.id)
            pStmt.setString(2, pklSoft.name)
            pStmt.setString(3, pklSoft.version)
            pStmt.setString(4, propAsString.get)
            pStmt.executeUpdate()
            pStmt.close()
          }

        } // End of jdbcWork anonymous inner class

        msiDbCtx.doWork(jdbcWork, false)
      } else {
        // Then insert it in the current MSIdb
        val jdbcWork = new JDBCWork {

          override def execute(con: Connection) {
            val pStmt = con.prepareStatement("insert into " + MsiDbPeaklistSoftwareTable.name + " (" + MsiDbPeaklistSoftwareTable.columnsAsStrList.filter(_ != MsiDbPeaklistSoftwareTable.columns.SERIALIZED_PROPERTIES.toString()).mkString(",") + ") VALUES (?,?,?)")
            pStmt.setLong(1, pklSoft.id)
            pStmt.setString(2, pklSoft.name)
            pStmt.setString(3, pklSoft.version)
            pStmt.executeUpdate()
            pStmt.close()
          }

        } // End of jdbcWork anonymous inner class

        msiDbCtx.doWork(jdbcWork, false)
      }

    }

    udsPklSoftOpt.get
  }

}
