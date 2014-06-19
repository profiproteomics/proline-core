package fr.proline.core.om.model.msi

import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.StringUtils

object PtmNames extends InMemoryIdGen

trait PtmNamesContainer {
  val shortName: String
  val fullName: String
}

case class PtmNames(val shortName: String, val fullName: String) extends PtmNamesContainer {

  // Requirements
  require(!StringUtils.isEmpty(shortName),"shortName is empty")

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

// TODO: move Java enumerations like fr.proline.core.orm.ps.PtmEvidence.Type into Java-Commons-API
object IonTypes extends Enumeration {
  type IonType = Value
  val Precursor = Value("Precursor")
  val Artefact = Value("Artefact")
  val NeutralLoss = Value("NeutralLoss")
  val PepNeutralLoss = Value("PepNeutralLoss")
}

case class PtmEvidence(

  // Required fields
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

  def ionType_(newIonType: IonTypes.IonType) = { newIonType }

   def sameAs(that: Any) = that match {
    case o : PtmEvidence => o.ionType==ionType && o.composition==composition && o.monoMass == monoMass && o.averageMass == averageMass && o.isRequired == isRequired
    case _ => false
  }

}

object PtmLocation extends Enumeration {
  type Location = Value
  val PROT_N_TERM = Value("Protein N-term")
  val PROT_C_TERM = Value("Protein C-term")
  val N_TERM = Value("N-term")
  val C_TERM = Value("C-term")
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
  require(!StringUtils.isEmpty(location), "location is empty")

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
  lazy val precursorDelta: PtmEvidence = {
    ptmEvidences.find( _.ionType == IonTypes.Precursor ).get
  }

  @transient lazy val neutralLosses = ptmEvidences.filter( _.ionType == IonTypes.NeutralLoss ).sortBy(_.monoMass)
  @transient lazy val pepNeutralLosses = ptmEvidences.find( ev => ev.ionType == IonTypes.PepNeutralLoss )
  @transient lazy val artefacts = ptmEvidences.find( ev => ev.ionType == IonTypes.Artefact )

  def isCompositionDefined = !StringUtils.isEmpty(precursorDelta.composition)

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
   */
  def toReadableString = {
    val loc = if( location == PtmLocation.ANYWHERE.toString() ) "" else location
    val resAsStr = if( residue != '\0' ) residue.toString else ""
    val locWithRes = Seq( loc, resAsStr ).filter( StringUtils.isNotEmpty(_) ).mkString(" ")

    "%s (%s)".format(this.names.shortName,locWithRes)
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
  val isNTerm: Boolean = false,
  val isCTerm: Boolean = false
) {

  // Requirements
  require(definition != null, "definition is null")
  require(seqPosition >= -1 , "invalid seqPosition, it must be an integer >= -1")
  require(!StringUtils.isEmpty(composition), "composition is empty")

  if (isNTerm) require(seqPosition == 0, "invalid seqPosition for a N-term PTM (it must be 0)")
  if (isCTerm) require(seqPosition == -1, "invalid seqPosition for a C-term PTM (it must be -1)")

}

