package fr.proline.core.om.model.msi

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.typesafe.scalalogging.LazyLogging
import fr.profi.chemistry.algo.DigestionUtils
import fr.profi.chemistry.model.Enzyme
import fr.profi.util.StringUtils.isNotEmpty
import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.ms.massToMoz

import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer

object Peptide extends InMemoryIdGen with LazyLogging {
  
  private val massComputer = new fr.profi.chemistry.algo.MassComputer(fr.profi.chemistry.model.ProteinogenicAminoAcidTable)
  
  // TODO: use this pattern everywhere empty arrays may be created
  final val EMPTY_PTM_ARRAY = Array.empty[LocatedPtm]
  
  import scala.collection._
  
  /** Returns a list of LocatedPTM objects for the provided sequence, PTM definition and optional position constraints.
   *  The results contains a list of putative PTMs that may be present or not on the peptide sequence.
   *  To get a list of truly located PTMs one has to provide a list of position constraints.
   */
  // TODO: check usage => previously this method was returning Unit and this can't work in practice
  // TODO: implement a JUnit test
  def getPutativeLocatedPtms (
    sequence: String,
    ptmDefinition: PtmDefinition,
    positionConstraints: Option[Array[Boolean]]
  ): Array[LocatedPtm] = {
    require( sequence != null, "sequence is null")
    require( ptmDefinition != null, "ptmDefinition is null")
         
    // Define some vars
    val residues = sequence.toCharArray() //sequence.split("") map { _.charAt(0) }
    val nbResidues = residues.length
    val searchedResidue = ptmDefinition.residue
    val precursorDelta = ptmDefinition.precursorDelta
    val tmpLocatedPtms = new ArrayBuffer[LocatedPtm]()
    
    // N-term locations are: Any N-term or Protein N-term
    if( ptmDefinition.location matches """.+N-term""" ) {
      if( searchedResidue == '\u0000' || searchedResidue == residues(0) ) {
        tmpLocatedPtms += new LocatedPtm( ptmDefinition, 0, precursorDelta, isNTerm = true )
      }
    }
    // C-term locations are: Any C-term, Protein C-term
    else if( ptmDefinition.location matches """.+C-term""" ) {
      if( searchedResidue == '\u0000' || searchedResidue == residues.last ) {
        tmpLocatedPtms += new LocatedPtm( ptmDefinition, -1, precursorDelta, isCTerm = true )
      }
    }
    // No location constraint (location=Anywhere)
    else {
      var seqPos = 1
      for(residue <- residues ) {
        if( searchedResidue == residue || residue == 'X' )  {
          tmpLocatedPtms += new LocatedPtm( ptmDefinition, seqPos, precursorDelta )
        }
        seqPos += 1
      }
    }
    
    // Check if position constraints are provided
    val locatedPtms = if( positionConstraints.isEmpty ) tmpLocatedPtms
    else {
      val filteredLocatedPtms = new ArrayBuffer[LocatedPtm]
      
      for(tmpLocatedPtm <- tmpLocatedPtms ) {
        
        val seqPos = tmpLocatedPtm.seqPosition
        val posConstraint = seqPos match {
          case -1 => positionConstraints.get.last
          // FIXME: is valid for Nterm position ?
          case _ => positionConstraints.get(seqPos)
        }
        
        if( posConstraint ) filteredLocatedPtms += tmpLocatedPtm
      }
      
      filteredLocatedPtms
    }
    
    locatedPtms.toArray
  }
  
  /** Returns the given list of located PTMs as a string.
   *  Example of PTM string for peptide MENHIR with oxidation (M) and SILAC label (R): 1[O]7[C(-9) 13C(9)] 
   */
  def makePtmString( locatedPtms: List[LocatedPtm] ): String = {
    require( locatedPtms != null, "locatedPtms is null" )
    
    // Return null if no located PTM
    if( locatedPtms.isEmpty ) {
      return ""
      //throw new IllegalArgumentException("can't compute a PTM string using an empty list of located PTMs")
    }
    
    // Sort located PTMs
    val sortedLocatedPtms = locatedPtms.sortBy(_.seqPosition)
    
    // Define data structure which will contain located PTM strings mapped by sequence position
    // TODO: do we allow more than one PTM at a given position ???
    val locatedPtmStringBySeqPos = new mutable.HashMap[Int,ArrayBuffer[LocatedPtm]]()
    
    // Iterate over located PTMs
    var lastSeqPos = 1 // will be used to compute a sequence position range
    for( locatedPtm <- sortedLocatedPtms ) {
      
      // Compute sequence position
      var seqPos = -2
      if( locatedPtm.isNTerm ) { seqPos = 0 }
      else if( locatedPtm.isCTerm  ) { seqPos = -1 }
      else {
        seqPos = locatedPtm.seqPosition
        lastSeqPos = seqPos
      }
      
      // Compute new PTM string and add it to the map locatedPtmStringBySeqPos
      locatedPtmStringBySeqPos.getOrElseUpdate(seqPos, new ArrayBuffer[LocatedPtm]()) += locatedPtm
    }
    
    // Create a list of all possible PTM sequence positions
    val putativeSeqPositions = List(0) ++ (1 to lastSeqPos) ++ List(-1)
    
    // Sort PTMs and merge them into a unique string
    val ptmStringBuilder = new StringBuilder()
    for(seqPos <- putativeSeqPositions ) {
      val locatedPtmStringsOpt = locatedPtmStringBySeqPos.get(seqPos)
      if( locatedPtmStringsOpt.isDefined ) {
        ptmStringBuilder ++= locatedPtmStringsOpt.get.map( _.toPtmString() ).sorted.mkString("")
      }
    }
    
    ptmStringBuilder.toString()
  }
  
  def makePtmString( locatedPtms: Array[LocatedPtm] ): String = {
    locatedPtms match {
      case null => ""
      case _ => Peptide.makePtmString( locatedPtms.toList )
    }
  }
  
  def calcMass( sequence: String, peptidePtms: Array[LocatedPtm] ): Double = {
    require( sequence != null, "sequence is null" )
    require( peptidePtms != null, "peptidePtms is null" )
    
    // Compute peptide sequence mass
    var mass = this.calcMass( sequence )
    if( mass == 0.0 ) return 0.0
    
    // Add peptide PTMs masses
    peptidePtms.foreach { mass += _.monoMass }    
    
    mass
  }

  def calcMass(sequence: String): Double = {
    require(sequence != null, "sequence is null")

    var mass: Double = 0

    mass = try {
      massComputer.computeMass(sequence)      
    } catch {
      case e: Exception => Double.NaN
    }

    if (mass.isNaN) {
      throw new Exception("can't compute peptide mass for sequence=" + sequence)
    }

    mass
  }

}

case class Peptide (
    
  // Required fields
  var id: Long,
  val sequence: String,
  val ptmString: String,
  @transient val ptms: Array[LocatedPtm],
  val calculatedMass: Double,
  
  // Mutable optional fields
  var properties: Option[PeptideProperties] = None
  
) {
  
  override def hashCode(): Int = uniqueKey.hashCode()
  
  // Define secondary constructors
  def this( id: Long, sequence: String, ptms: Array[LocatedPtm], calculatedMass: Double ) = {
    this( id, sequence, Peptide.makePtmString( ptms ), ptms, calculatedMass )
  }
  
  def this( sequence: String, ptms: Array[LocatedPtm], calculatedMass: Double ) = {
    this( Peptide.generateNewId(), sequence, Peptide.makePtmString( ptms ), ptms, calculatedMass )
  }
  
  def this( sequence: String, ptms: Array[LocatedPtm], id: Long ) = {
    this( id, sequence, ptms, Peptide.calcMass( sequence, ptms ) )
  }
  
  def this( sequence: String, ptms: Array[LocatedPtm] ) = {
    this( sequence, ptms, Peptide.generateNewId() )
  }
  
  // Requirements
  require( sequence != null, "sequence is null" )
  require( calculatedMass >= 0 )
  if (isNotEmpty(ptmString)) {
    require(isModified, s"PTMs ($sequence) can't be empty if ptmString is not empty ($ptmString)")
  }
  
  def isModified(): Boolean = ptms != null && ptms.nonEmpty
  
  /** Returns a string representing the peptide PTMs */
  @JsonProperty def readablePtmString: String = {
    
    var tmpReadablePtmString: String = null
    if (ptms != null) {
  
      val ptmStringBuf = new ListBuffer[String]
  
      for (ptm <- ptms.sortBy(_.seqPosition) ) {
        ptmStringBuf += ptm.toReadableString
      }
  
      tmpReadablePtmString = ptmStringBuf.mkString("; ")
    }

    tmpReadablePtmString

  }
  
  /** Returns a string that can be used as a unique key for this peptide */
  @transient def uniqueKey: String = { 
    if (ptmString != null) 
    	sequence + "%" + ptmString
    else
    	sequence + "%" 
  }
  
}

case class PeptideProperties()

object PeptideMatch extends InMemoryIdGen with LazyLogging {
  
  def countMissedCleavages(
    sequence: String,
    residueBefore: Option[Char],
    residueAfter: Option[Char],
    enzymes: Array[Enzyme]
  ): Int = {
    
    require( sequence != null, "sequence is null" )

    //If no enzyme, no missed cleavage
    if(enzymes == null || enzymes.isEmpty)
      return 0

    // Only consider first enzyme
    require(enzymes.length == 1, "Unexpected number of enzymes")
    
    val enzyme = enzymes.head
    require(enzyme != null, "Enzyme is null")
    
    var missedCleavages: Int = 0
    
    // Two different ways to count missed cleavages
    if(enzyme.isIndependant == false) { // main case : search for missed cleavage corresponding to any cleavage site
      enzyme.enzymeCleavages.foreach(missedCleavages += DigestionUtils.countMissedCleavages(sequence, _))
    } else { // specific case : enzymes must be considered one by one
      
      // determine the enzyme cleavage to consider
      val enzymeCleavages = DigestionUtils.getEnzymesCleavages(sequence, residueBefore, residueAfter, enzyme)
      
      // enzymeCleavages should contain only one item, more than one means ambiguity. In any case return max number of missed cleavages
      var maxMissedCleavages = 0
      enzymeCleavages.foreach(ec => {
        val mc = DigestionUtils.countMissedCleavages(sequence, ec)
        if(mc > maxMissedCleavages) maxMissedCleavages = mc
      })
      
      missedCleavages = maxMissedCleavages
    }
//    if(missedCleavages > 0) logger.debug("Sequence "+residueBefore.getOrElse("^")+"."+sequence+"."+residueAfter.getOrElse("$")+" has "+missedCleavages+" miscleavages")
    missedCleavages
  }

  def getBestOnScoreDeltaMoZ(pepMatches: Array[PeptideMatch]): PeptideMatch = {
       var tmpBest = pepMatches(0)
      pepMatches.foreach( pepM => {
        if ((tmpBest.score < pepM.score) || ((tmpBest.score == pepM.score) && (tmpBest.deltaMoz < pepM.deltaMoz)))
          tmpBest = pepM
      })
      tmpBest
  }
  
}

object PeptideMatchScoreType extends Enumeration {
  val ANDROMEDA_SCORE = Value("andromeda:score")
  val COMET_EVALUE_LOG_SCALED = Value("comet:evalue log scaled")
  val MASCOT_IONS_SCORE = Value("mascot:ions score")
  val MSGF_EVALUE_LOG_SCALED = Value("msgf:evalue log scaled")
  val OMSSA_EVALUE = Value("omssa:expect value")
  val PEPTIDE_SHAKER_PSM_SCORE = Value("peptide_shaker:psm score")
  val PERCOLATOR_PEP_LOG_SCALED = Value("percolator:pep log scaled")
  val SEQUEST_EXPECT_LOG_SCALED = Value("sequest:expect log scaled")
  val XTANDEM_EXPECT_LOG_SCALED = Value("xtandem:expect log scaled")
  val XTANDEM_HYPERSCORE = Value("xtandem:hyperscore")

}
// Required by the Scala-Jackson-Module to handle Scala enumerations
class PeptideMatchScoreTypeJacksonRef extends TypeReference[PeptideMatchScoreType.type]

case class PeptideMatch(
  // Required fields
  var id: Long,
  var rank: Int,
  val score: Float,
  @(JsonScalaEnumeration @field)(classOf[PeptideMatchScoreTypeJacksonRef])
  val scoreType: PeptideMatchScoreType.Value,
  val charge: Int,
  val deltaMoz: Float, // deltaMoz = expMoz - calcMoz
  val isDecoy: Boolean,
  @transient val peptide: Peptide,

  // Immutable optional fields
  @JsonProperty val missedCleavage: Int = 0,
  val fragmentMatchesCount: Int = 0,

  @transient val msQuery: MsQuery = null, // TODO: require ?

  // Mutable optional fields
  var isValidated: Boolean = true, // only defined in the model
  var resultSetId: Long = 0,
  var cdPrettyRank: Int = 0,
  var sdPrettyRank: Int = 0,

  protected val childrenIds: Array[Long] = null,
  @transient var children: Option[Array[PeptideMatch]] = null,

  var bestChildId: Long = 0,

  var properties: Option[PeptideMatchProperties] = None,

  @transient var validationProperties: Option[PeptideMatchResultSummaryProperties] = None
  
) {
  
  // Requirements
  require( rank > 0, "invalid rank value" )
  require( scoreType != null, "scoreType is null")
  require( peptide != null, "peptide is null" )
  
  // Define proxy defs (mainly used for serialization purpose)
  @JsonProperty def msQueryId: Long = this.msQuery.id
  @JsonProperty def peptideId: Long = this.peptide.id
  
  // Related objects ID getters
  def getChildrenIds(): Array[Long] = { if(children != null && children.isDefined) children.get.map(_.id) else childrenIds  }
  
  def getBestChild(): Option[PeptideMatch] = {
    if( bestChildId != 0 && children != null && children.isDefined ) {
      children.get.find( _.id == bestChildId )
    }
    else None
  }
  
  /** Returns a MS2 query object. */
  def getMs2Query(): Ms2Query = { if(msQuery != null) msQuery.asInstanceOf[Ms2Query] else null }
  
  def getExperimentalMoz(): Double = {
    if( msQuery != null ) msQuery.moz
    else {
      deltaMoz + massToMoz( peptide.calculatedMass, charge )
    }
  }

  /**
   * Return Peptide Sequence with ambiguous AA replaced.
   * If there is no ambiguous AA or Peptide is not specified in this object, an empty String will be returned
   *
   * @return
   */
  def getDisambiguatedSeq(): String ={
    var resultSeq = ""
    if (peptide != null) {
      val ambiguityStringOpt = properties.flatMap(_.getMascotProperties).flatMap(_.ambiguityString)
      if (ambiguityStringOpt.isDefined) {
        val seq = peptide.sequence.toCharArray
        val seqB = new StringBuilder()
        val indexAmbiguity = Seq.newBuilder[Int]
        val charAmbiguity = Seq.newBuilder[Char]
        ambiguityStringOpt.get.split(',').sliding(3, 3).foreach(tuple => {
          charAmbiguity += tuple(2).charAt(0)
          indexAmbiguity += (tuple(0).toInt)
        })

        val indSeq = indexAmbiguity.result()
        val charsSeq = charAmbiguity.result()

        for( i <- 0 until (seq.size)){
          if(indSeq.contains(i+1)) {
            seqB.append(charsSeq(indSeq.indexOf(i + 1)))
          } else
            seqB.append(seq(i))
        }
        resultSeq = seqB.mkString("")
      }
    }
    resultSeq
  }
  
}

case class PeptideMatchProperties (
  @BeanProperty var mascotProperties: Option[PeptideMatchMascotProperties] = None,
  @BeanProperty var omssaProperties: Option[PeptideMatchOmssaProperties] = None,
  @BeanProperty var xtandemProperties: Option[PeptideMatchXtandemProperties] = None,
  @BeanProperty var ptmSiteProperties: Option[PeptideMatchPtmSiteProperties] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var precursorIntensityFraction: Option[Float] = None,
  @BeanProperty var spectralCount: Option[Int] = None,
  @BeanProperty var isotopeOffset: Option[Int] = None
)

case class PeptideMatchMascotProperties (
  @BeanProperty var expectationValue: Double,
  @BeanProperty var readableVarMods: Option[String] = None,
  @BeanProperty var varModsPositions: Option[String] = None,
  @BeanProperty var ambiguityString: Option[String] = None,
  @BeanProperty var nlString: Option[String] = None,
  @BeanProperty var usedPeaksCount: Option[Int] = None
)

case class PeptideMatchOmssaProperties (
//  @BeanProperty var expectationValue: Double,
  @BeanProperty var pValue: Double,
  @BeanProperty var correctedCharge: Int,
  @BeanProperty var ionSeries: Array[String] = Array[String]()
)

case class PeptideMatchXtandemProperties (
  @BeanProperty var expectationValue: Double,
  @BeanProperty var nextScore: Double,
  @BeanProperty var ionSeriesMatches: Map[String, Int] = null,
  @BeanProperty var ionSeriesScores: Map[String, Double] = null
)

case class PeptideMatchResultSummaryProperties (
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var mascotScoreOffset: Option[Float] = None,
  @BeanProperty var mascotAdjustedExpectationValue: Option[Double] = None
)

case class PeptideMatchPtmSiteProperties(
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var mascotDeltaScore: Option[Float] = None,
  
  // Key is the ReadableString of the LocatedPtm, value is the Mascot Probability for this site
  @JsonDeserialize(contentAs = classOf[java.lang.Float])
  protected var mascotProbabilityBySite: Map[String, Float] = null,
  
  // A raw string containing some PhosphoRS information
  // This is mainly useful to manage PhosphoRS results obtained from mzIdentML files
  @BeanProperty var phosphoRsString: Option[String] = None
) {
  
  def getMascotProbabilityBySite(): Option[Map[String, Float]] = {
    Option(mascotProbabilityBySite)
  }
  
  def setMascotProbabilityBySite( probabilityBySite: Option[Map[String, Float]] ) = {
    mascotProbabilityBySite = probabilityBySite.orNull
  }
}

object PeptideInstance extends InMemoryIdGen

case class PeptideInstance(
  
  // Required fields
  var id: Long,
  @transient val peptide: Peptide,

  // Immutable optional fields
  var peptideMatchIds: Array[Long] = null, //One of these 2 values should be specified                        
  @transient var peptideMatches: Array[PeptideMatch] = null,
  
  val children: Array[PeptideInstance] = null,
  
  protected val unmodifiedPeptideId: Long = 0,
  
  @transient val unmodifiedPeptide: Option[Peptide] = null,

  @transient val isDecoy: Boolean = false, // Transient because the status is only used for validation purposes.
  @transient var isValidated: Boolean = true,

    // Mutable optional fields
  var proteinMatchesCount: Int = 0,
  var proteinSetsCount: Int = 0,
  var validatedProteinSetsCount: Int = 0,
  var totalLeavesMatchCount: Int = 0,
  var selectionLevel: Int = 2,
  var elutionTime: Float = 0,
  
  @transient var peptideSets: Array[PeptideSet] = null,
  var bestPeptideMatchId: Long = 0,
  var masterQuantComponentId: Long = 0,
  var resultSummaryId: Long = 0,
  
  var properties: Option[PeptideInstanceProperties] = None,
  var peptideMatchPropertiesById: Map[Long, PeptideMatchResultSummaryProperties ] = null

  ) {
  
  // Requirements
  require( peptide != null, "peptide is null" )
  require( (peptideMatchIds != null || peptideMatches !=null), "peptideMatchIds or peptideMatches is null")
  
  @JsonProperty def peptideId = peptide.id
  @JsonProperty def peptideMatchesCount = getPeptideMatchIds.length

  // Related objects ID getters
  def getPeptideMatchIds(): Array[Long] = { if(peptideMatches != null) peptideMatches.map(_.id)  else peptideMatchIds }
  
  def getUnmodifiedPeptideId(): Long = { if(unmodifiedPeptide != null && unmodifiedPeptide.isDefined) unmodifiedPeptide.get.id else unmodifiedPeptideId }
  
  def getPeptideMatchProperties( peptideMatchId: Long ): Option[PeptideMatchResultSummaryProperties] = {
    if( peptideMatchPropertiesById != null ) { peptideMatchPropertiesById.get(peptideMatchId) }
    else { None }
  }
  
  /** Returns true if the sequence is specific to a protein set. */
  def isProteinSetSpecific(): Boolean = { proteinSetsCount == 1 }
  
  /** Returns true if the sequence is specific to a validated protein set. */
  def isValidProteinSetSpecific(): Boolean = { validatedProteinSetsCount == 1 }
  
  /** Returns true if the sequence is specific to a protein match. */
  def isProteinMatchSpecific(): Boolean = { proteinMatchesCount == 1 }

  def scoringProperties =  {if (properties.isDefined) properties.get.score else None}
}

case class PeptideInstanceProperties(
  @BeanProperty var score: Option[ScoringProperties] = None
)

case class ScoringProperties(
    @BeanProperty var pValue: Double,
    @BeanProperty var score: Double,
    //@TODO needed during alpha test cycles. To be removed or renamed
    @BeanProperty var scoreType: Option[String] = None
)

case class PeptideSetItem (
  // Required fields
  var selectionLevel: Int,
  @transient val peptideInstance: PeptideInstance,
  
  // Mutable  fields
  var peptideSetId: Long,
  
  // Mutable optional fields
  var isBestPeptideSet: Option[Boolean] = None,
  var resultSummaryId: Long = 0,
  
  var properties: Option[PeptideSetItemProperties] = None
) {
  
  @JsonProperty def peptideInstanceId = peptideInstance.id
  
}

case class PeptideSetItemProperties()

object PeptideSet extends InMemoryIdGen

case class PeptideSet ( // Required fields
  var id: Long,
  val items: Array[PeptideSetItem],
  val isSubset: Boolean,
  val sequencesCount: Int,
  val peptideMatchesCount: Int,
  var proteinMatchIds: Array[Long],
  
  // Mutable optional fields
  protected val proteinSetId: Long = 0,
  @transient var proteinSet: Option[ProteinSet] = null,
  
  var resultSummaryId: Long = 0,
  var score: Float = 0,
  var scoreType: String = null,
  
  var strictSubsetIds: Array[Long] = null,
  var strictSubsets: Option[Array[PeptideSet]] = null,
  
  var subsumableSubsetIds: Array[Long] = null,
  var subsumableSubsets: Option[Array[PeptideSet]] = null,
  
  var properties: Option[PeptideSetProperties] = None
  
) {
  
  // Requirements
  require( items != null, "items is null" )
  require( peptideMatchesCount >= items.length, "invalid peptideMatchesCount" )
  
  // Related objects ID getters
  def getProteinSetId(): Long = { if(proteinSet != null && proteinSet.isDefined) proteinSet.get.id else proteinSetId }
  
  def getStrictSubsetIds(): Array[Long] = { if(strictSubsets != null && strictSubsets.isDefined) strictSubsets.get.map(_.id)  else strictSubsetIds  }
    
  def getSubsumableSubsetIds(): Array[Long] = { if(subsumableSubsets != null && subsumableSubsets.isDefined) subsumableSubsets.get.map(_.id)  else subsumableSubsetIds  }
  
  def getPeptideInstances(): Array[PeptideInstance] = { items.map( _.peptideInstance ) }
  
  def getPeptideMatchIds(): Array[Long] = {
    val peptideMatchIdSet = new ArrayBuffer[Long]()
    for (pepSetItem <- items) {
      peptideMatchIdSet ++= pepSetItem.peptideInstance.getPeptideMatchIds
    }
    peptideMatchIdSet.toArray
  }
  
  def getPeptideIds(): Array[Long] = this.items.map { _.peptideInstance.peptide.id }
  
  def hasStrictSubset(): Boolean = { 
    if( (strictSubsetIds != null && strictSubsetIds.length > 0 ) || 
        (strictSubsets != null && strictSubsets.isDefined) ) true else false
  }
  
  def hasSubsumableSubset(): Boolean = { 
    if( (subsumableSubsetIds != null && subsumableSubsetIds.length > 0 ) || 
        (subsumableSubsets != null && subsumableSubsets.isDefined) ) true else false
  }
  
  def hasSubset(): Boolean = { if( hasStrictSubset || hasSubsumableSubset ) true else false }
  
  override def hashCode = {
    if(proteinMatchIds != null && proteinMatchIds.size>0) id.hashCode +  proteinMatchIds.hashCode() else id.hashCode
  }

  override def toString(): String = {
    val toStrBulider = new StringBuilder(id.toString)
    var firstPepIt = true
    items.foreach(it => {
      if (!firstPepIt) { toStrBulider.append(",") } else { firstPepIt = false }
      toStrBulider.append(it.peptideInstance.peptide.sequence)
    })
    toStrBulider.result
  }
}

case class PeptideSetProperties(
    @BeanProperty var score: Option[ScoringProperties] = None
)

