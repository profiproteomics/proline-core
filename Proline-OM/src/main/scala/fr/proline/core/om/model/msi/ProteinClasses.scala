package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.StringUtils.isNotEmpty
import fr.proline.util.misc.InMemoryIdGen
import scala.collection.mutable.ArrayBuffer

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

//@JsonInclude( Include.NON_NULL )
case class Protein (
  // Required fields
  val id: Long,
  val sequence: String,
  var mass: Double,
  var pi: Float,
  val crc64: String,
  val alphabet: String,
  var properties: Option[ProteinProperties] = None
) {
  
  // Requirements
  require( isNotEmpty(sequence) )
  require( alphabet.matches("aa|rna|dna") ) // TODO: create an enumeration
  
  // Define secondary constructors
  def this( sequence: String, id: Long = Protein.generateNewId(), alphabet: String = "aa" ) = {
    this( id, sequence, Protein.calcMass(sequence), Protein.calcPI(sequence), Protein.calcCRC64(sequence), alphabet )
  }
  
  @JsonProperty lazy val length = sequence.length()
  
  def getSequenceCoverage( seqPositions: Array[Tuple2[Int,Int]] ): Float = {
    Protein.calcSequenceCoverage( this.length, seqPositions )
  }

}

//@JsonInclude( Include.NON_NULL )
case class ProteinProperties()


object ProteinMatch extends InMemoryIdGen

//@JsonInclude( Include.NON_NULL )
case class ProteinMatch (
    
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

//@JsonInclude( Include.NON_NULL )
case class ProteinMatchProperties()

 
object ProteinSet extends InMemoryIdGen

//@JsonInclude( Include.NON_NULL )
case class ProteinSet ( 
  // Required fields
  @transient val peptideSet: PeptideSet,
  var hasPeptideSubset: Boolean,
  var isDecoy: Boolean, // TODO: add to MSIdb
  
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
 
  protected var typicalProteinMatchId: Long = 0,
  @transient protected var typicalProteinMatch: Option[ProteinMatch] = null,
  
  var masterQuantComponentId: Long = 0,
  
  var isValidated: Boolean = true,
  var selectionLevel: Int = 2,
 
  var properties: Option[ProteinSetProperties] = None,
  var proteinMatchPropertiesById: Map[Long, ProteinMatchResultSummaryProperties ] = null
  
) {

  @JsonProperty lazy val peptideSetId = peptideSet.id
  
  // Requirements
  require( samesetProteinMatchIds != null  || samesetProteinMatches != null )


  def setTypicalProteinMatch(newTypicalPM: ProteinMatch): Unit = {
    require(newTypicalPM != null ,"A typical ProteinMatch should be defined !")
    
    if(samesetProteinMatches!= null && samesetProteinMatches.isDefined) 
      require(samesetProteinMatches.get.contains(newTypicalPM) ,"Typical ProteinMatch should belong to this ProteinSet's sameset !")
    else
      require(samesetProteinMatchIds.contains(newTypicalPM.id) ,"Typical ProteinMatch should belong to this ProteinSet's sameset !")
    
    typicalProteinMatchId = newTypicalPM.id
    typicalProteinMatch = Some(newTypicalPM)
  }
  
  def getTypicalProteinMatch: Option[ProteinMatch] = typicalProteinMatch
  
  /**
   * Return all proteinMatchIds sameset and subsets 
   */
  def getProteinMatchIds: Array[Long] = {
	if(samesetProteinMatches != null && samesetProteinMatches.isDefined){
		val allProtIds = new ArrayBuffer[Long]()
    	allProtIds ++= samesetProteinMatches.get.map(_.id) 
    	if(subsetProteinMatches != null && subsetProteinMatches.isDefined)
    	  allProtIds ++= subsetProteinMatches.get.map(_.id)
    	allProtIds.toArray  
	}  else {
	  samesetProteinMatchIds ++ subsetProteinMatchIds
	}
	
  }
  
  /**
   * Return sameset proteinMatchIds  
   */
  def getSameSetProteinMatchIds: Array[Long] = {
	if(samesetProteinMatches != null && samesetProteinMatches.isDefined){
		val allProtIds = new ArrayBuffer[Long]()
    	allProtIds ++= samesetProteinMatches.get.map(_.id) 
    	allProtIds.toArray  
	}  else 
	  samesetProteinMatchIds 
	
  } 
  
  /**
   * Return subset proteinMatchIds 
   */
  def getSubSetProteinMatchIds: Array[Long] = {
	if(subsetProteinMatches != null && subsetProteinMatches.isDefined){
		val allProtIds = new ArrayBuffer[Long]()
		allProtIds ++= subsetProteinMatches.get.map(_.id)
    	allProtIds.toArray  
	}  else {
	  subsetProteinMatchIds
	}
	
  }
  def getTypicalProteinMatchId: Long = { if(typicalProteinMatch != null && typicalProteinMatch.isDefined) typicalProteinMatch.get.id else typicalProteinMatchId }
   
  /**
   * Return a list of all ProteinMatch ids, identified as same set or sub set of this ProteinSet, 
   * referenced by their PeptideSet.
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
    if (peptideSet.hasSubsumableSubset) {
      peptideSet.subsumableSubsets.get.foreach(pepSet => {
        resultMapBuilder += pepSet -> pepSet.proteinMatchIds
      })
    }
    
    resultMapBuilder.result
  }

  override def hashCode = {
    id.hashCode
  }

  override def toString(): String = {
    val toStrBulider = new StringBuilder(id.toString)
    if (typicalProteinMatch != null && typicalProteinMatch.isDefined)
      toStrBulider.append(typicalProteinMatch.get.accession)
    else
      toStrBulider.append(" typicalProteinMatch ID : ").append(typicalProteinMatchId)
    toStrBulider.result
  }
 
}

//@JsonInclude( Include.NON_NULL )
case class ProteinSetProperties()

//@JsonInclude( Include.NON_NULL )
case class ProteinMatchResultSummaryProperties()

//@JsonInclude( Include.NON_NULL )
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
  
}

//@JsonInclude( Include.NON_NULL )
case class SequenceMatchProperties()


