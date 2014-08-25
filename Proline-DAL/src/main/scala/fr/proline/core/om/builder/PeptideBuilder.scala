package fr.proline.core.om.builder

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object PeptideBuilder {
  
  protected val pepCols = MsiDbPeptideColumns
  
  def buildPeptide(record: IValueContainer, locatedPtms: Option[Array[LocatedPtm]] ): Peptide = {
    
    val pepId = record.getLong(pepCols.ID)
    
    new Peptide(
      id = record.getLong(pepCols.ID),
      sequence = record.getString(pepCols.SEQUENCE),
      ptmString = record.getString(pepCols.PTM_STRING),
      ptms = locatedPtms.getOrElse(Array.empty[LocatedPtm]),
      calculatedMass = record.getDouble(pepCols.CALCULATED_MASS)
    )
  }

}