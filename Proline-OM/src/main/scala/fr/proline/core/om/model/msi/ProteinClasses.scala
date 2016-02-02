package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.annotation.JsonProperty
import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.misc.InMemoryIdGen

object BioSequenceAlphabet extends EnhancedEnum {
  val AA, RNA, DNA = Value
}

/*trait IBioSequence {
  def id: Long
  def alphabet: BioSequenceAlphabet.Value
  def sequence: String // TODO: set as Option[String] ???
  def length: Int
  def crc64: String
  def properties: Option[BioSequenceProperties]
}

case class NucleicAcidSequence(
  val id: Long,
  val sequence: String, // May be ""
  val length: Int,
  val crc64: String,
  val alphabet: BioSequenceAlphabet.Value,
  var properties: Option[BioSequenceProperties]
) extends IBioSequence {
  require( alphabet != BioSequenceAlphabet.AA.toString )
}
*/

object BioSequence extends InMemoryIdGen

case class BioSequence(
  val id: Long,
  val alphabet: BioSequenceAlphabet.Value,
  val sequence: Option[String],
  val length: Int,
  var mass: Double,
  var pi: Float,
  val crc64: String,
  var properties: Option[BioSequenceProperties] = None
)

case class BioSequenceProperties()

// TODO: merge with Protein object when the Protein case class has been removed
object BuildProtein extends InMemoryIdGen {
  def apply(
    id: Long,
    sequence: String,
    mass: Double,
    pi: Float,
    crc64: String
  ) = {
    // Requirements
    require( sequence != null && ! sequence.isEmpty(), "sequence is null or empty" )  
    
    BioSequence(
      id,
      BioSequenceAlphabet.AA,
      Some(sequence),
      sequence.length,
      mass,
      pi,
      crc64
    )
  }
  def apply(
    sequence: String,
    id: Long = BioSequence.generateNewId()
  ) = {
    // Requirements
    require( sequence != null && ! sequence.isEmpty(), "sequence is null or empty" )  
    
    BioSequence(
      id,
      BioSequenceAlphabet.AA,
      Some(sequence),
      sequence.length,
      Protein.calcMass(sequence),
      Protein.calcPI(sequence),
      Protein.calcCRC64(sequence)
    )
  }
}

// TODO: merge with BuildProtein object when the Protein case class has been removed
object Protein extends InMemoryIdGen {

  /** A percentage (between 0 and 100) expressing the sequence coverage of the protein */
  def calcSequenceCoverage(protSeqLength: Int, seqPositions: Iterable[Tuple2[Int, Int]]): Float = {

    // Map sequence positions
    val seqIndexSet = new java.util.HashSet[Int]()
    for (seqPosition <- seqPositions) {
      for (seqIdx <- seqPosition._1 to seqPosition._2) {
        seqIndexSet.add(seqIdx)
      }
    }

    val coveredSeqLength = seqIndexSet.size()
    val coverage = 100 * coveredSeqLength / protSeqLength

    coverage
  }

  import org.biojava.bio.BioException
  import org.biojava.bio.proteomics._
  import org.biojava.bio.seq._
  import org.biojava.bio.symbol._

  def calcMass(sequence: String): Double = {
    try {
      new MassCalc(SymbolPropertyTable.AVG_MASS, false).getMass(ProteinTools.createProtein(sequence))
    } catch {
      case e: BioException => Double.NaN
    }
  }

  def calcPI(sequence: String): Float = {
    try {
      (new IsoelectricPointCalc().getPI(ProteinTools.createProtein(sequence), true, true)).toFloat
    } catch {
      case e: IllegalAlphabetException => Float.NaN
      case be: BioException            => Float.NaN
    }
  }

  // TODO: compute the CRC64
  def calcCRC64(sequence: String): String = {
    null
  }

}

// TODO: replace by BioSequence case class
case class Protein(
  // Required fields
  val id: Long,
  val sequence: String,
  var mass: Double,
  var pi: Float,
  val crc64: String,
  val alphabet: String,
  var properties: Option[BioSequenceProperties]
) {
  
  // Requirements
  require( sequence != null && ! sequence.isEmpty() )
  
  // Define secondary constructors
  def this( id: Long, sequence: String, mass: Double, pi: Float, crc64: String, alphabet: String) = {
    this( id, sequence, mass, pi, crc64, alphabet, None )
  }
  def this( sequence: String, id: Long = Protein.generateNewId(), alphabet: String = "aa" ) = {
    this( id, sequence, Protein.calcMass(sequence), Protein.calcPI(sequence), Protein.calcCRC64(sequence), alphabet )
  }
  
  @JsonProperty lazy val length = sequence.length()
  
  def getSequenceCoverage( seqPositions: Array[Tuple2[Int,Int]] ): Float = {
    Protein.calcSequenceCoverage( this.length, seqPositions )
  }

}

object ProteinMatch extends InMemoryIdGen

case class ProteinMatch(
    
  // Required fields
  val accession: String,
  var description: String,
   
  // Immutable optional fields
  val isDecoy: Boolean = false,
  val isLastBioSequence: Boolean = false,
   
  // Mutable optional fields
  var id: Long = 0,
  var taxonId: Long = 0,
  var resultSetId: Long = 0,

  protected var proteinId: Long = 0,
  @transient var protein: Option[Protein] = null,

  var seqDatabaseIds: Array[Long] = null,
   
  var geneName: String = null,
  var score: Float = 0,
  var scoreType: String = null,
  var coverage: Float = 0,
  var peptideMatchesCount: Int = 0,
  var sequenceMatches: Array[SequenceMatch] = null,
  
  var properties: Option[ProteinMatchProperties] = None
  
) {
  
  // Requirements
  require( accession != null && description != null, "accession and description must be defined" )
  
  @JsonProperty lazy val peptidesCount: Int = {
    if( sequenceMatches == null) 0
    else sequenceMatches.map( _.getPeptideId ).distinct.length
  }

  def getProteinId: Long = { if(protein != null && protein.isDefined) protein.get.id else proteinId }
  
}

case class ProteinMatchProperties()

 
object ProteinSet extends InMemoryIdGen

case class ProteinSet ( 
  // Required fields
  @transient val peptideSet: PeptideSet,
  var hasPeptideSubset: Boolean,
  var isDecoy: Boolean,
  
  // Immutable optional fields
  
  // Mutable optional fields
  var id: Long = 0,
  var resultSummaryId: Long = 0,
  
  // Must be only proteinMatchIds which are not in a subset
  var samesetProteinMatchIds : Array[Long] = null, //One of these 2 values should be specified. Should be coherent with subsetProteinMatches
  @transient var samesetProteinMatches: Option[Array[ProteinMatch]] = null,

  // Must be only proteinMatchIds which are in a subset
  var subsetProteinMatchIds : Array[Long] = null, //One of these 2 values should be specified. Should be coherent with samesetProteinMatches
  @transient var subsetProteinMatches: Option[Array[ProteinMatch]] = null,
 
  protected var representativeProteinMatchId: Long = 0,
  @transient protected var representativeProteinMatch: Option[ProteinMatch] = null, // TODO: remove me, the id is sufficient here
  
  var proteinMatchCoverageById: Map[Long, Float] = null,
  
  var masterQuantComponentId: Long = 0,
  
  var isValidated: Boolean = true,
  var selectionLevel: Int = 2,
  
  var properties: Option[ProteinSetProperties] = None,
  var proteinMatchPropertiesById: Map[Long, ProteinMatchResultSummaryProperties ] = null
) {

  @JsonProperty lazy val peptideSetId = peptideSet.id
  
  // Requirements
  require( samesetProteinMatchIds != null  || samesetProteinMatches != null )

  // TODO: work only with ids to simplify the OM
  def setRepresentativeProteinMatch(newReprPM: ProteinMatch): Unit = {
    require(newReprPM != null ,"A representative ProteinMatch should be defined !")
    
    val requirementMsg = "Representative ProteinMatch should belong to this ProteinSet's sameset !"
    
    val samesetPMsOpt = samesetProteinMatches
    if(samesetPMsOpt!= null && samesetPMsOpt.isDefined) 
      require(samesetPMsOpt.get.toSet.contains(newReprPM), requirementMsg)
    else
      require(samesetProteinMatchIds.toSet.contains(newReprPM.id), requirementMsg)
    
    representativeProteinMatchId = newReprPM.id
    representativeProteinMatch = Some(newReprPM)
  }
  
  def getRepresentativeProteinMatch(): Option[ProteinMatch] = representativeProteinMatch
  
  /**
   * Return all proteinMatchIds sameset and subsets 
   */
  def getProteinMatchIds: Array[Long] = {
	  getSameSetProteinMatchIds ++ getSubSetProteinMatchIds
  }

  /**
   * Return sameset proteinMatchIds
   */
  def getSameSetProteinMatchIds: Array[Long] = {
    val samesetPMsOpt = samesetProteinMatches
    
    if (samesetPMsOpt != null && samesetPMsOpt.isDefined) samesetPMsOpt.get.map(_.id) else samesetProteinMatchIds
  }

  /**
   * Return subset proteinMatchIds
   */
  def getSubSetProteinMatchIds: Array[Long] = {
    val subsetPMsOpt = subsetProteinMatches
    
    if (subsetPMsOpt != null && subsetPMsOpt.isDefined) subsetPMsOpt.get.map(_.id) else subsetProteinMatchIds
  }
  
  def getRepresentativeProteinMatchId(): Long = {
    val reprPMOpt = representativeProteinMatch
    
    if(reprPMOpt != null && reprPMOpt.isDefined) reprPMOpt.get.id else representativeProteinMatchId
  }
   
  /**
   * Return a list of all ProteinMatch ids, identified as same set or sub set of this ProteinSet, 
   * referenced by their PeptideSet. SubsumableSubsets are not taken into account !
   * If PeptideSet are not accessible, a IllegalAccessException will be thrown. 
   *	
   */
  @throws(classOf[IllegalAccessException])
  def getAllProteinMatchesIdByPeptideSet: Map[PeptideSet, Array[Long]] = {
    
    if (peptideSet.hasStrictSubset && (peptideSet.strictSubsets == null || !peptideSet.strictSubsets.isDefined))
      throw new IllegalAccessException("Strict subsets not accessible")
    
    if (peptideSet.hasSubsumableSubset && (peptideSet.subsumableSubsets == null || !peptideSet.subsumableSubsets.isDefined))
      throw new IllegalAccessException("Subsumable subsets not accessible")
    
    val resultMapBuilder = Map.newBuilder[PeptideSet, Array[Long]]

    resultMapBuilder += peptideSet -> peptideSet.proteinMatchIds
    
    if (peptideSet.hasStrictSubset) {
      peptideSet.strictSubsets.get.foreach(pepSet => {
        resultMapBuilder += pepSet -> pepSet.proteinMatchIds
      })
    }
//    if (peptideSet.hasSubsumableSubset) {
//      peptideSet.subsumableSubsets.get.foreach(pepSet => {
//        resultMapBuilder += pepSet -> pepSet.proteinMatchIds
//      })
//    }
    
    resultMapBuilder.result
  }

  override def hashCode = {
    id.hashCode
  }

  override def toString(): String = {
    val toStrBulider = new StringBuilder(id.toString)
    
    val reprPMOpt = representativeProteinMatch
    if (reprPMOpt != null && reprPMOpt.isDefined)
      toStrBulider.append(" representativeProteinMatch AC : ").append(reprPMOpt.get.accession)
    else
      toStrBulider.append(" representativeProteinMatch ID : ").append(representativeProteinMatchId)
    
    toStrBulider.result
  }
 
}

case class ProteinSetProperties()

case class ProteinMatchResultSummaryProperties()

case class SequenceMatch (
  // Required fields
  val start: Int,
  val end: Int,
  val residueBefore: Char,
  val residueAfter: Char,
  
  // Immutable optional fields
  val isDecoy: Boolean = false,
  var resultSetId : Long = 0,
  
  // Mutable optional fields
  protected var peptideId: Long = 0,
  @transient var peptide: Option[Peptide] = null,
  
  var bestPeptideMatchId: Long = 0,
  @transient var bestPeptideMatch: Option[PeptideMatch] = null,
  
  var properties: Option[SequenceMatchProperties] = None
) {
  
  // Requirements
  require( start > 0 , "peptide sequence position must be striclty positive" )
  require( end > start , "peptide end position must be greater than start position" )
  
  def getPeptideId: Long = { if(peptide != null && peptide.isDefined) peptide.get.id else peptideId }

  def getBestPeptideMatchId: Long = { if(bestPeptideMatch != null && bestPeptideMatch.isDefined) bestPeptideMatch.get.id else bestPeptideMatchId }
 
  override def equals(other: Any): Boolean = {

    if (other.isInstanceOf[SequenceMatch]) {
      val otherSeqMatch = other.asInstanceOf[SequenceMatch]

      start.equals(otherSeqMatch.start) && end.equals(otherSeqMatch.end) && 
        getPeptideId.equals(otherSeqMatch.getPeptideId)
    } else {
      false
    }

  }
}

case class SequenceMatchProperties()


