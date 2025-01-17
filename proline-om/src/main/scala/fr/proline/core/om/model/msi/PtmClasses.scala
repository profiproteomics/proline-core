package fr.proline.core.om.model.msi

import scala.annotation.meta.field
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.StringUtils.isEmpty
import fr.profi.util.StringUtils.isNotEmpty

object PtmNames extends InMemoryIdGen

trait PtmNamesContainer {
  val shortName: String
  val fullName: String
}

case class PtmNames(shortName: String, fullName: String) extends PtmNamesContainer {

  // Requirements
  require(!isEmpty(shortName),"shortName is empty")

  def sameAs(that: Any): Boolean = that match {
    case o : PtmNames => o.shortName==shortName && o.fullName==fullName
    case _ => false
  }
}

case class UnimodEntry(

  // Required fields
  override val shortName: String,
  override val fullName: String,
  specificities: Array[Any],

  // Immutable optional fields
  unimodId: Long = 0,
  ptmEvidences: Array[PtmEvidence] = null

) extends PtmNamesContainer {

  // Requirements
  require(specificities != null,"specificities is null")

}

// TODO: move Java enumerations like fr.proline.core.orm.ps.PtmEvidence.Type into Java-Commons-API ???
// TODO: rename to PtmEvidenceType
object IonTypes extends Enumeration {
  type IonType = Value
  val Precursor = Value("Precursor")
  val Artefact = Value("Artefact")
  val NeutralLoss = Value("NeutralLoss")
  val PepNeutralLoss = Value("PepNeutralLoss")
}

// Required by the Scala-Jackson-Module to handle Scala enumerations
class IonTypesTypeRef extends TypeReference[IonTypes.type]

case class PtmEvidence(

  // Required fields
  @(JsonScalaEnumeration @field)(classOf[IonTypesTypeRef])
  ionType: IonTypes.IonType,
  var composition: String,
  monoMass: Double,
  averageMass: Double, // TODO: set to Float !

  // Immutable optional fields
  isRequired: Boolean = false
) {
  // Requirements
  require(ionType != null, "ionType is null")
  require(composition != null, "composition is null")

  // FIXME: is this method working ?
  def ionType_(newIonType: IonTypes.IonType) = { newIonType }

  // TODO: remove this method => it should not be useful here because we use a case class which already defines hashCode & equals
  def sameAs(that: Any) = that match {
    case o : PtmEvidence => o.ionType==ionType && o.composition==composition && o.monoMass == monoMass && o.averageMass == averageMass && o.isRequired == isRequired
    case _ => false
  }

}

object PtmLocation extends Enumeration {
  type Location = Value
  val PROT_N_TERM = Value("Protein N-term")
  val PROT_C_TERM = Value("Protein C-term")
  val ANY_N_TERM = Value("Any N-term")
  val ANY_C_TERM = Value("Any C-term")
  val ANYWHERE = Value("Anywhere")
}

object PtmClassification extends EnhancedEnum {
  val UNKNOWN = Value("-")
  val POST_TRANSLATIONAL = Value("Post-translational")
  val CO_TRANSLATIONAL = Value("Co-translational")
  val PRE_TRANSLATIONAL = Value("Pre-translational")
  val CHEMICAL_DERIVATIVE = Value("Chemical derivative")
  val ARTEFACT = Value("Artefact")
  val N_LINKED_GLYCOSYLATION = Value("N-linked glycosylation")
  val O_LINKED_GLYCOSYLATION = Value("O-linked glycosylation")
  val OTHER_GLYCOSYLATION = Value("Other glycosylation")
  val SYNTH_PEP_PROTECT_GP = Value("Synth. pep. protect. gp.")
  val ISOTOPIC_LABEL = Value("Isotopic label")
  val NON_STANDARD_RESIDUE = Value("Non-standard residue")
  val MULTIPLE = Value("Multiple")
  val OTHER = Value("Other")
  val AA_SUBSTITUTION = Value("AA substitution")
}

trait IPtmSpecificity {
  def id: Long
  val location: String
  val residue: Char
  val classification: String // PtmClassification.Value
  val ptmId: Long
}

case class PtmSpecificity(

  // Required fields
  location: String,

  // Immutable optional fields
  residue: Char = '\u0000',
  classification: String = null, //PtmClassification.Value = PtmClassification.UNKNOWN,
  id: Long = 0,
  ptmId: Long = 0
) extends IPtmSpecificity {

  // Requirements
  require(!isEmpty(location), "location is empty")

  def sameAs(that: Any): Boolean = that match {
    case o : PtmSpecificity => o.location==location && o.residue==residue && o.classification == classification && o.ptmId == ptmId
    case _ => false
  }

}

object PtmDefinition extends InMemoryIdGen

//@JsonInclude(Include.NON_NULL)
case class PtmDefinition(

  // Required fields
  var id: Long,
  location: String,
  names: PtmNames,
  ptmEvidences: Array[PtmEvidence], // TODO: remove and replace by corresponding lazy attributes ???

  // Immutable optional fields
  residue: Char = '\u0000',
  classification: String = null, //PtmClassification.Value = PtmClassification.UNKNOWN,
  ptmId: Long = 0,
  unimodId: Int = 0

) extends IPtmSpecificity {

  // Requirements
  require(names != null,"names is null")
  require(ptmEvidences != null, "ptmEvidences is null")

  // Lazy values
  // FIXME: set back to lazy field when jackson-module-scala issue #238 is fixed
  @JsonProperty def precursorDelta: PtmEvidence = {
    ptmEvidences.find( _.ionType == IonTypes.Precursor ).get
  }

  // FIXME: set back to lazy field when jackson-module-scala issue #238 is fixed
  @transient def neutralLosses: Array[PtmEvidence] = ptmEvidences.filter( _.ionType == IonTypes.NeutralLoss ).sortBy(_.monoMass)
  @transient def pepNeutralLosses: Option[PtmEvidence] = ptmEvidences.find(ev => ev.ionType == IonTypes.PepNeutralLoss )
  @transient def artefacts: Option[PtmEvidence] = ptmEvidences.find(ev => ev.ionType == IonTypes.Artefact )

  def isCompositionDefined: Boolean = !isEmpty(precursorDelta.composition)

  def sameAs(that: Any): Boolean = that match {
    case o : PtmDefinition => {
      var sameEvidences = ptmEvidences.length == o.ptmEvidences.length
      for (e <- ptmEvidences) {
        sameEvidences = sameEvidences && o.ptmEvidences.exists(_.sameAs(e))
      }
      (sameEvidences && o.location==location && o.names.sameAs(names) &&
         o.residue==residue && o.classification == classification && o.ptmId == ptmId && o.unimodId == unimodId)
    }
    case _ => false
  }

  /**
   * Convert the PTM definition into a readable string (using the Mascot convention).
   * Used by PM-DatasetExporter
   * TODO: rename to "toMascotString" or something else to avoid confusion with LocatedPtm.toReadableString ?
   */
  def toReadableString(): String = {
    val loc = if( location == PtmLocation.ANYWHERE.toString ) "" else location
    val resAsStr = if( residue != '\u0000' ) residue.toString else ""
    val locWithRes = Seq( loc, resAsStr ).filter( isNotEmpty(_) ).mkString(" ")

    "%s (%s)".format(this.names.shortName,locWithRes)
  }
  
}

object LocatedPtm {
  
  /**
   * Create a LocatedPtm using specified PtmDefinition and location on peptide sequence.
   * seqPos == 0 for Nterm and  seqPos == -1 for CTerm
   * 
   */
  def apply( ptmDef: PtmDefinition, seqPos: Int ): LocatedPtm = {
    
    var( isNTerm, isCTerm ) = ( false, false )
    
    // N-term locations are: Any N-term or Protein N-term
    if( ptmDef.location matches ".+N-term$" ) {
      isNTerm = true
    }
    // C-term locations are: Any C-term, Protein C-term
    else if( ptmDef.location matches ".+C-term$" ) {
      isCTerm = true
    }
    
    val precDelta = ptmDef.precursorDelta
    
    new LocatedPtm(
      definition = ptmDef, 
      seqPosition = seqPos,
      monoMass = precDelta.monoMass,
      averageMass = precDelta.averageMass,
      composition = precDelta.composition,
      isNTerm = isNTerm,
      isCTerm = isCTerm
    )

  }
  
}

case class LocatedPtm(
  // Required fields
  definition: PtmDefinition,
  seqPosition: Int,
  monoMass: Double,    // TODO: retrieve from PtmDefinition ???
  averageMass: Double, // TODO: retrieve from PtmDefinition ??? TODO: set to Float !
  composition: String, // TODO: retrieve from PtmDefinition ???

  // Immutable optional fields
  isNTerm: Boolean,
  isCTerm: Boolean
) {
  
  def this(
    definition: PtmDefinition,
    seqPosition: Int,
    monoMass: Double,
    averageMass: Double,
    composition: String
  ) = {
    this(
      definition = definition,
      seqPosition = seqPosition,
      monoMass = monoMass,
      averageMass = averageMass,
      composition = composition,
      isNTerm = false,
      isCTerm = false
    )
  }
  
  def this(
    definition: PtmDefinition,
    seqPosition: Int,
    precursorDelta: PtmEvidence,
    isNTerm: Boolean = false,
    isCTerm: Boolean = false
  ) = {
    this(
      definition = definition,
      seqPosition = seqPosition,
      monoMass = precursorDelta.monoMass,
      averageMass = precursorDelta.averageMass,
      composition = precursorDelta.composition,
      isNTerm = isNTerm,
      isCTerm = isCTerm
    )
  }

  // Requirements
  require(definition != null, "definition is null")
  require(seqPosition >= -1 , "invalid seqPosition, it must be an integer >= -1")
  require(composition != null, "composition is null")

  if (isNTerm) require(seqPosition == 0, "invalid seqPosition for a N-term PTM (it must be 0)")
  if (isCTerm) require(seqPosition == -1, "invalid seqPosition for a C-term PTM (it must be -1)")

  /**
   * Convert the PTM definition into a readable string (using the Proline convention).
   */
  def toReadableString(): String = {
    val ptmDef = this.definition
    val shortName = ptmDef.names.shortName
    
    val ptmConstraint = if (isNTerm || isCTerm) ptmDef.location
    else "" + ptmDef.residue + seqPosition
    
    s"${shortName} (${ptmConstraint})"
  }
  
  def toPtmString(): String = {
    
    val atomModBySymbol = this.computePtmStructure( this.composition ).atomModBySymbol        
    val atomModStrings = new ArrayBuffer[String]
    
    // Sort atom symbols by ascendant order
    val sortedAtomSymbols = atomModBySymbol.keys.toList.sorted
    
    // Iterate over atom symbols
    for(atomSymbol <- sortedAtomSymbols ) {
      
      val atomMod = atomModBySymbol(atomSymbol)
      
      // Sort atom symbols by ascendant order
      val sortedAtomIsotopes = atomMod.keys.toList.sorted
      
      // Iterate over atom isotopes
      for(atomIsotope <- sortedAtomIsotopes ) {
        
        val isotopePrefix = if( atomIsotope == 0 ) "" else atomIsotope.toString
        val atomModIsotopeComposition = atomMod(atomIsotope)
        val nbAtoms = atomModIsotopeComposition.quantity
        var atomModString = isotopePrefix + atomSymbol
        
        // Stringify substracted atoms
        if( atomModIsotopeComposition.sign == "-" ) {
          
          atomModString += "(-"+nbAtoms+")"
          
        // Stringify added atoms
        } else if( atomModIsotopeComposition.sign == "+" ) {
          
          if( nbAtoms > 1 ) atomModString += "("+nbAtoms+")"
          
        } else { throw new Exception("invalid sign of isotope composition") }
        
        atomModStrings += atomModString
      }
    }
    
    if( atomModStrings.isEmpty ) {
      throw new Exception( "a problem has occured during the ptm string construction" )
    }
    
    this.seqPosition + "[" + atomModStrings.mkString(" ") + "]"
  }
  
  private case class PtmIsotopeComposition( sign: String, quantity: Int )
  private case class PtmStructure( atomModBySymbol: HashMap[String,HashMap[Int,PtmIsotopeComposition]] )
  
  private def computePtmStructure( composition: String ): PtmStructure = {
    
    import java.util.regex.Pattern
    
    // EX : SILAC label (R) => "C(-9) 13C(9)"
    val atomMods = composition.split(" ")
    val atomCompositionBySymbol = new HashMap[String,HashMap[Int,PtmIsotopeComposition]]()
    
    for(atomMod <- atomMods ) {
      var( atomSymbol, nbAtoms, atomIsotope, sign ) = ("",0,0,"")
      
      val m = Pattern.compile("""^(\d*)(\w+)(\((-){0,1}(.+)\)){0,1}""").matcher(atomMod)
      if( m.matches ) {
        
        // 0 means most frequent isotope
        atomIsotope = if( isNotEmpty(m.group(1)) ) m.group(1).toInt else 0
        atomSymbol = m.group(2)
        sign = if( isNotEmpty(m.group(4)) ) m.group(4) else "+"
        nbAtoms = if( isNotEmpty(m.group(5)) ) m.group(5).toInt else 1
      }
      else { throw new Exception( "can't parse atom composition '"+atomMod+"'" ) }
      
      if( ! atomCompositionBySymbol.contains(atomSymbol) ) {
        atomCompositionBySymbol += atomSymbol -> new HashMap[Int,PtmIsotopeComposition]()
      }
      
      atomCompositionBySymbol(atomSymbol) += ( atomIsotope -> PtmIsotopeComposition( sign, nbAtoms ) )
    }
    
    PtmStructure( atomCompositionBySymbol )
  }

}

case class PtmDataSet(
  // model version. Default version = 1.0
  version: String = "1.0",
  // array of of the PTMs of interest
  ptmIds: Array[Long],
  // array of Leaf RSM
  leafResultSummaryIds: Array[Long],
  // array of PtmSites identified
  ptmSites: Array[PtmSite2],
  // array of Ptm Clusters
  ptmClusters: Array[PtmCluster]
)

case class PtmCluster(
    id: Long,
    // Array of PTM site locations Ids
    ptmSiteLocations: Array[Long],
    // Best (highest PTM probability) peptide match for this PtmSiteTuple
    bestPeptideMatchId: Long,
    // The localization probability (percentage between 0 and 100)
    localizationConfidence: Float,
    // Array of clusterized peptides
    peptideIds: Array[Long],
    // array of peptide having the same sequence and modification of a matching peptide, but with their ptm located at another position
    // those peptide did not match the ptm site, but they can confuse the quantification process
    isomericPeptideIds: Array[Long],
    // Annotation properties
    //  Idem as for Quanti/Ident: DESELECTED_MANUAL(0) DESELECTED_AUTO(1) SELECTED_AUTO(2) SELECTED_MANUAL(3)
    selectionLevel: Integer,
    //Notation for selection_level confidence
    selectionConfidence: Integer,
    //Description for selection_level reason or precision
    selectionInformation: String
)

case class PtmSite2(
    id: Long,
    // the protein match this ptm belongs to
    proteinMatchId: Long,
    // ptm definition
    ptmDefinitionId: Long,
    // position of the ptm site on the protein sequence
    seqPosition: Int,
    // best (higher ptm probability) peptide match for this site
    bestPeptideMatchId: Long,
    // The localization probability (percentage between 0 and 100)
    localizationConfidence: Float,
    // map of peptide Ids matching that site, organized by position of the modification on the peptide sequence
    peptideIdsByPtmPosition: Map[Int, Array[Long]],
    // array of peptide having the same sequence and modification of a matching peptide, but with their ptm located at another position
    // those peptide did not match the ptm site, but they can confuse the quantification process
    isomericPeptideIds: Array[Long],
    //specify if modification Site is a CTerm or NTerm modification
    isNTerminal : Boolean = false,
    isCTerminal : Boolean = false,
)

case class PtmSite(
  // the protein match this ptm belongs to
  proteinMatchId: Long,
  // ptm definition 
  ptmDefinitionId: Long,
  // position of the ptm site on the protein sequence
  seqPosition: Int,
  // best (higher ptm probability) peptide match for this site
  bestPeptideMatchId: Long,
  // The localization probability (percentage between 0 and 100)
  localizationConfidence: Float,
  // map of peptide Ids matching that site, organized by position of the modification on the peptide sequence
  peptideIdsByPtmPosition: Map[Int, Array[Long]],
  // array of matching peptide instances in leaf RSM
  peptideInstanceIds: Array[Long],
  // array of peptide instances having the same sequence and modification of a matching peptide, but with their ptm located at another position
  // those peptide instance did not match the ptm site, but they can confuse the quantification process
  isomericPeptideInstanceIds: Array[Long]
)