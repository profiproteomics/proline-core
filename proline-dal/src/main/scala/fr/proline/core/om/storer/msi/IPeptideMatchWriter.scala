package fr.proline.core.om.storer.msi

import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.storer.msi.impl.PgPeptideMatchWriter
import fr.proline.repository.DriverType

trait IPeptideMatchWriter {
  def insertPeptideMatches(peptideMatches: Seq[PeptideMatch], msiDbConCtxt : MsiDbConnectionContext): Unit

}

object PeptideMatchWriter {

  def apply(driverType : DriverType): IPeptideMatchWriter = {
    driverType match {
      case DriverType.POSTGRESQL => PgPeptideMatchWriter
      case _ => throw new NotImplementedError()
    }
  }
}