package fr.proline.core.om.model.msi

import scala.annotation.meta.field
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.StringUtils.isEmpty
import fr.profi.util.StringUtils.isNotEmpty

object PtmNames extends InMemoryIdGen

trait PtmNamesContainer {
  val shortName: String
  val fullName: String
}

case class PtmNames(val shortName: String, val fullName: String) extends PtmNamesContainer {

  // Requirements
  require(!isEmpty(shortName),"shortName is empty")

  def sameAs(that: Any) = that match {
    case o : PtmNames => o.shortName==shortName && o.fullName==fullName
    case _ => false
  }
}

case class UnimodEntry(

  // Required fields
  override val shortName: String,
  override val fullName: String,
  val specificities: Array[Any],

  // Immutable optional fields
  val unimodId: Long = 0,
  val ptmEvidences: Array[PtmEvidence] = null

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
  val ionType: IonTypes.IonType,
  var composition: String,
  val monoMass: Double,
  val averageMass: Double, // TODO: set to Float !

  // Immutable optional fields
  val isRequired: Boolean = false
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

trait IPtmSpecificity {
  def id: Long
  val location: String
  val residue: Char
  val classification: String
  val ptmId: Long
}

//@JsonInclude(Include.NON_NULL)
case class PtmSpecificity(

  // Required fields
  val location: String,

  // Immutable optional fields
  val residue: Char = '\0',
  val classification: String = null,
  val id: Long = 0,
  val ptmId: Long = 0
) extends IPtmSpecificity {

  // Requirements
  require(!isEmpty(location), "location is empty")

  def sameAs(that: Any) = that match {
    case o : PtmSpecificity => o.location==location && o.residue==residue && o.classification == classification && o.ptmId == ptmId
    case _ => false
  }

}

object PtmDefinition extends InMemoryIdGen

//@JsonInclude(Include.NON_NULL)
case class PtmDefinition(

  // Required fields
  var id: Long,
  val location: String,
  val names: PtmNames,
  val ptmEvidences: Array[PtmEvidence], // TODO: remove and replace by corresponding lazy attributes ???

  // Immutable optional fields
  val residue: Char = '\0',
  val classification: String = null,
  val ptmId: Long = 0,
  val unimodId: Int = 0

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
  @transient def neutralLosses = ptmEvidences.filter( _.ionType == IonTypes.NeutralLoss ).sortBy(_.monoMass)
  @transient def pepNeutralLosses = ptmEvidences.find( ev => ev.ionType == IonTypes.PepNeutralLoss )
  @transient def artefacts = ptmEvidences.find( ev => ev.ionType == IonTypes.Artefact )

  def isCompositionDefined = !isEmpty(precursorDelta.composition)

  def sameAs(that: Any) = that match {
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
  def toReadableString() = {
    val loc = if( location == PtmLocation.ANYWHERE.toString() ) "" else location
    val resAsStr = if( residue != '\0' ) residue.toString else ""
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
  val definition: PtmDefinition,
  val seqPosition: Int,
  val monoMass: Double,    // TODO: retrieve from PtmDefinition ???
  val averageMass: Double, // TODO: retrieve from PtmDefinition ??? TODO: set to Float !
  val composition: String, // TODO: retrieve from PtmDefinition ???

  // Immutable optional fields
  val isNTerm: Boolean,
  val isCTerm: Boolean
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
  def toReadableString() = {
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

case class PtmSite(
  val proteinMatchId: Long, 
  val definitionId: Long,
  val seqPosition: Int, 
  val bestPeptideMatchId: Long,
  val peptideIdsByPtmPosition: Map[Int, Array[Long]], 
  val peptideInstanceIds: Array[Long], 
  val isomericPeptideInstanceIds: Array[Long]
) {
  
}
