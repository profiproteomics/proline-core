package fr.proline.core.om.storer.msi

import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.storer.msi.impl.PgPeptideWriter
import fr.proline.core.om.storer.msi.impl.SQLPeptideWriter
import fr.proline.core.om.storer.msi.impl.StorerContext

trait IPeptideWriter {
  def insertPeptides(peptides: Seq[Peptide], context: StorerContext): Unit 
}


/** A factory object for implementations of the IPeptideWriter trait */
object PeptideWriter {
  
  import fr.proline.repository.DriverType

  def apply( driverType: DriverType ): IPeptideWriter = {
    driverType match {
      case DriverType.POSTGRESQL => PgPeptideWriter
      case _ => SQLPeptideWriter
    }
  }
}