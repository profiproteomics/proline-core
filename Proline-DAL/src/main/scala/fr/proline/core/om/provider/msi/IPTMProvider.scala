package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation



trait IPTMProvider {

  def getPtmDefinitions( ptmDefIds: Seq[Int] ) : Array[Option[PtmDefinition]]
  
  def getPtmDefinition( ptmDefID: Int ) : Option[PtmDefinition] = { getPtmDefinitions( Array(ptmDefID) )(0) }
    
  /**
   * Search for a PtmDefinition with specified features
   * - ptmShortName : Associated PtmNames have ptmShortName as short name
   * - ptmResidue : residue on which ptm is applied : could be '\0' if no specific residue
   * - ptmLocation : Location of the Ptm. Could be one of PtmLocation.Value 
   * 
   */
  def getPtmDefinition( ptmShortName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location ) : Option[PtmDefinition] 
  
  /**
   * Get the PtmNames id for specified ShortName
   */
  def getPtmId( shortName : String ) : Option[Int]
  
}