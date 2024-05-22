package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.context.DatabaseConnectionContext
import scala.collection.mutable.HashMap
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.PtmNames

trait IPTMProvider {

  /**
   *  Get PtmDefinitions (wrapped in Option) with specified Ids.
   *  If no PtmDefinitions is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[PtmDefinition] in the same order as their specified ids.
   *  
   *  @param ptmDefIds: Sequence of ids of PtmDefinitions to search for
   *  @return Array of Option[PtmDefinition] corresponding to found PtmDefinitions
   */
  def getPtmDefinitionsAsOptions( ptmDefIds: Seq[Long] ): Array[Option[PtmDefinition]]
  
  /**
   *  Get PtmDefinitions with specified Ids.
   *  
   *  @param ptmDefIds: Sequence of ids of PtmDefinitions to search for
   *  @return Array of PtmDefinition corresponding to found PtmDefinitions
   */
  def getPtmDefinitions( ptmDefIds: Seq[Long] ): Array[PtmDefinition]
  
  /**
   *  Get PtmDefinition (wrapped in Option) with specified Id.
   *  If no PtmDefinition is defined for specified id, Option.None will be returned.
   *  
   *  @param ptmDefID: id of PtmDefinition to search for
   *  @return Option[PtmDefinition] corresponding to found PtmDefinition
   */
  def getPtmDefinition( ptmDefID: Long ): Option[PtmDefinition] = { getPtmDefinitionsAsOptions( Array(ptmDefID) )(0) }
    
  /**
   * Search for a PtmDefinition with specified features
   * - ptmShortName : Associated PtmNames have ptmShortName as short name
   * - ptmResidue : residue on which ptm is applied : could be '\u0000' if no specific residue
   * - ptmLocation : Location of the Ptm. Could be one of PtmLocation.Value 
   * 
   */
  def getPtmDefinition( ptmShortName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location ): Option[PtmDefinition]
  
   /**
   * Search for a PtmDefinition with specified features
   * - ptmMonoMass : Associated PtmEvidences have ptmMonoMass as mono_mass
   * - ptmMonoMassMargin : Margin between input mono_mass and ProlineDB mono_mass values
   * - ptmResidue : residue on which ptm is applied : could be '\u0000' if no specific residue
   * - ptmLocation : Location of the Ptm. Could be one of PtmLocation.Value 
   * 
   */
  def getPtmDefinition( ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location ): Option[PtmDefinition]
  
  /**
   * Get the PtmNames id for specified ShortName
   */
  def getPtmId( shortName: String ): Option[Long]
  
}

/**
 * Fake Provider, using cache (Name=> PTMDefinition) and creating fake PTM Definition : 
 * - fullName = ptmName (ptmResidue)
 * - Post-translational
 * - PtmEvidence : Precursor, monoMass = averageMass = Double.MaxValue, not required
 *  
 */
object PTMFakeProvider extends IPTMProvider { 
  
  val psDbCtx = null

  var ptmDefByName:HashMap[String, PtmDefinition] = new HashMap[String, PtmDefinition]()
    
  /**
   *  Get PtmDefinition with specified ptm (name), residue and location,  if already created using this Provider  
   * or create a new fake one with classification = Post-translational, a fake Precursor PtmEvidence and specified properties
   */
  def getPtmDefinition(ptmName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = { 
    
    val fullName = ptmName+" ("+ptmResidue+")"
    if(ptmDefByName.contains(fullName))
      return ptmDefByName.get(fullName)  
      
    var newPtmDef:PtmDefinition  = null
    val ptmEvidence = new PtmEvidence( 
          ionType = IonTypes.Precursor,
      		composition = "UNVALID",
      		monoMass = Double.MaxValue,
      		averageMass = Double.MaxValue,
      		isRequired = false
      		)
    
    newPtmDef = new PtmDefinition(
                      id = PtmDefinition.generateNewId(),
                      ptmId = PtmNames.generateNewId,
                      location =ptmLocation.toString,
                      residue = ptmResidue,
                      classification = "Post-translational",
                      names = new PtmNames(shortName = ptmName,fullName = fullName),
                      ptmEvidences = Array(ptmEvidence)
                    )
    ptmDefByName += fullName -> newPtmDef 
    
    Some(newPtmDef)
  }

  /**
   * Get PtmDefinition with specified ptm (name), residue, mono mass and location. If a ptm definition wuith same name has
   * already been created using this Provider, it will be returned if other data corresponds and will be set in, cache
   * or create a new fake one with classification = Post-translational, a fake Precursor PtmEvidence and specified properties
   * This is a specific method, not a IPTMProvider implementation
   */
  def getPtmDefinition(ptmName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location, sMonoMass: Double): Option[PtmDefinition] = {

    val fullName = ptmName + " (" + ptmResidue + ")"
    if (ptmDefByName.contains(fullName)) {
      val foundDef =ptmDefByName.get(fullName)
      if(foundDef.get.residue == ptmResidue && foundDef.get.location.equals(ptmLocation.toString) && foundDef.get.ptmEvidences(0).monoMass.equals(sMonoMass))
        return ptmDefByName.get(fullName)
    }

    var newPtmDef: PtmDefinition = null
    val ptmEvidence = new PtmEvidence(
      ionType = IonTypes.Precursor,
      composition = "UNVALID",
      monoMass = sMonoMass,
      averageMass = Double.MaxValue,
      isRequired = false
    )

    newPtmDef = new PtmDefinition(
      id = PtmDefinition.generateNewId(),
      ptmId = PtmNames.generateNewId(),
      location = ptmLocation.toString,
      residue = ptmResidue,
      classification = "Post-translational",
      names = new PtmNames(shortName = ptmName, fullName = fullName),
      ptmEvidences = Array(ptmEvidence)
    )
    ptmDefByName += fullName -> newPtmDef

    Some(newPtmDef)
  }

  def getPtmDefinition(ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location) : Option[PtmDefinition] = {

    val ptmName = "UnknownPtmNamesShortNames"
    val fullName = ptmName+" ("+ptmResidue+")"
      
    var newPtmDef:PtmDefinition  = null
    val ptmEvidence = new PtmEvidence( 
          ionType = IonTypes.Precursor,
      		composition = "UNVALID",
      		monoMass = Double.MaxValue,
      		averageMass = Double.MaxValue,
      		isRequired = false
      		)
    
    newPtmDef = new PtmDefinition(
                      id = PtmDefinition.generateNewId(),
                      ptmId = PtmNames.generateNewId,
                      location =ptmLocation.toString,
                      residue = ptmResidue,
                      classification = "Post-translational",
                      names = new PtmNames(shortName = ptmName,fullName = fullName),
                      ptmEvidences = Array(ptmEvidence)
                    )
    ptmDefByName += fullName -> newPtmDef 
    
    Some(newPtmDef)
  }
  
  /**
   * Return an empty array
   */
  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {
  	val retArray =  new Array[Option[PtmDefinition]](1)
  	retArray.update(0, Option.empty[PtmDefinition])	
  	
  	retArray
  }
  
  def getPtmDefinitions(ptmDefIds: Seq[Long]): Array[PtmDefinition] = {
    this.getPtmDefinitionsAsOptions(ptmDefIds).filter( _ != None ).map( _.get )
  } 
      
  /**
   * Return an Option.empty Long
   */
  def getPtmId(shortName: String): Option[Long] = {
    Option.empty[Long]
  }

}


/**
 * Return only no value (Option.empty) 
 */
object EmptyPTMProvider extends IPTMProvider {

  val psDbCtx = null

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {
  	val retArray =  new Array[Option[PtmDefinition]](ptmDefIds.length)
  	var index = 0
  	ptmDefIds foreach ( id => {
  	  retArray.update(index, Option.empty[PtmDefinition])
  	  index += 1
  	})
  	
  	retArray
  }
  
  def getPtmDefinitions(ptmDefIds: Seq[Long]): Array[PtmDefinition] = {
    Array.empty[PtmDefinition]
  }
 
  def getPtmDefinition(ptmName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
	  Option.empty[PtmDefinition]
  }
  
  def getPtmDefinition(ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location) : Option[PtmDefinition] = {
    Option.empty[PtmDefinition]
  }
  
  def getPtmId(shortName: String): Option[Long] = {
     Option.empty[Long]
  }
 
}