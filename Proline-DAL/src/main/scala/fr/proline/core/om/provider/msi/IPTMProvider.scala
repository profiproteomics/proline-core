package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation

trait IPTMProvider {

  /**
   *  Get PtmDefinitions (wrapped in Option) with specified Ids.
   *  If no PtmDefinitions is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[PtmDefinition] in the same order as their specified ids.
   *  
   *  @param ptmDefIds: Sequence of ids of PtmDefinitions to search for
   *  @return Array of Option[PtmDefinition] corresponding to found PtmDefinitions
   */
  def getPtmDefinitionsAsOptions( ptmDefIds: Seq[Int] ): Array[Option[PtmDefinition]]
  
  /**
   *  Get PtmDefinitions with specified Ids.
   *  
   *  @param ptmDefIds: Sequence of ids of PtmDefinitions to search for
   *  @return Array of PtmDefinition corresponding to found PtmDefinitions
   */
  def getPtmDefinitions( ptmDefIds: Seq[Int] ): Array[PtmDefinition]
  
  /**
   *  Get PtmDefinition (wrapped in Option) with specified Id.
   *  If no PtmDefinition is defined for specified id, Option.None will be returned.
   *  
   *  @param ptmDefID: id of PtmDefinition to search for
   *  @return Option[PtmDefinition] corresponding to found PtmDefinition
   */
  def getPtmDefinition( ptmDefID: Int ): Option[PtmDefinition] = { getPtmDefinitionsAsOptions( Array(ptmDefID) )(0) }
    
  /**
   * Search for a PtmDefinition with specified features
   * - ptmShortName : Associated PtmNames have ptmShortName as short name
   * - ptmResidue : residue on which ptm is applied : could be '\0' if no specific residue
   * - ptmLocation : Location of the Ptm. Could be one of PtmLocation.Value 
   * 
   */
  def getPtmDefinition( ptmShortName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location ): Option[PtmDefinition] 
  
  /**
   * Get the PtmNames id for specified ShortName
   */
  def getPtmId( shortName: String ): Option[Int]
  
}