package fr.proline.core.om.storer.msi

import fr.proline.core.om.storer.msi.impl.SQLPeptideInstanceWriter
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.repository.DriverType

trait IPeptideInstanceWriter {
  def insertPeptideInstances(pepInstances: Seq[PeptideInstance], msiDbConCtxt : MsiDbConnectionContext): Unit

}

object PeptideInstanceWriter {
  def apply(driverType: DriverType): IPeptideInstanceWriter = {
    SQLPeptideInstanceWriter
//    driverType match {
//      case DriverType.POSTGRESQL => PgPeptideInstanceWriter
//      case _ => throw new NotImplementedError()
//    }
  }
}