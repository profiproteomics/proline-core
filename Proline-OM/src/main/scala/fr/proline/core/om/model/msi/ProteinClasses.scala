package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import scala.reflect.BeanProperty

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

import org.apache.commons.lang3.StringUtils.isNotEmpty
import fr.proline.util.misc.InMemoryIdGen

object Protein extends InMemoryIdGen{
  
  /** A percentage (between 0 and 100) expressing the sequence coverage of the protein */
  def calcSequenceCoverage( protSeqLength: Int, seqPositions: Iterable[Tuple2[Int,Int]] ): Float = {
    
    // Map sequence positions
    val seqIndexSet = new java.util.HashSet[Int]()
    for( seqPosition <- seqPositions ) {
      for( val seqIdx <- seqPosition._1 to seqPosition._2 ) {
        seqIndexSet.add(seqIdx)
      }
    }
    
    val coveredSeqLength = seqIndexSet.size() 
    val coverage = 100 * coveredSeqLength /protSeqLength
    
    coverage
  }
  
  import org.biojava.bio.BioException
  import org.biojava.bio.proteomics._
  import org.biojava.bio.seq._
  import org.biojava.bio.symbol._
  
  def calcMass( sequence: String ): Double = {
    try {
    	new MassCalc(SymbolPropertyTable.AVG_MASS, false).getMass( ProteinTools.createProtein(sequence) )
    } catch {
    	case e: BioException => Double.NaN
    }
  }
  
  def calcPI( sequence: String ): Float = {
    try {
    	(new IsoelectricPointCalc().getPI( ProteinTools.createProtein(sequence), true, true) ).toFloat
    } catch {
		  case e:IllegalAlphabetException => Float.NaN
		  case be:BioException =>  Float.NaN
	}    	
  }
  
  // TODO: compute the CRC64
  def calcCRC64( sequence: String ): String = {
    null
  }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Protein ( // Required fields
                   val id: Int,
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
  def this( sequence: String, id: Int = Protein.generateNewId(), alphabet: String = "aa" ) = {
      this( id,sequence, Protein.calcMass(sequence),
                         Protein.calcPI(sequence),
                         Protein.calcCRC64(sequence),
                         alphabet )
  }
  
  lazy val length = sequence.length()
  
  def getSequenceCoverage( seqPositions: Array[Tuple2[Int,Int]] ): Float = {
    Protein.calcSequenceCoverage( this.length, seqPositions )
  }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinProperties


object ProteinMatch extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinMatch ( 
                   // Required fields                    
                   val accession: String,
                   val description: String,
                   
                   // Immutable optional fields
                   val isDecoy: Boolean = false,
                   val isLastBioSequence: Boolean = false,
                   
                   // Mutable optional fields                     
                   var id: Int = 0,                   
                   var taxonId: Int = 0,
                   var resultSetId: Int = 0,

                   protected var proteinId: Int = 0,
                   @transient var protein: Option[Protein] = null,

                   var seqDatabaseIds: Array[Int] = null,
                   
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
  
  lazy val peptidesCount: Int = {
    if( sequenceMatches == null) 0
    else sequenceMatches.map( _.getPeptideId ).distinct.length
  }

  def getProteinId : Int = { if(protein != null && protein != None) protein.get.id else proteinId }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinMatchProperties

 
object ProteinSet extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinSet ( 
                 // Required fields
                 @transient val peptideSet: PeptideSet,
                 var hasPeptideSubset: Boolean,
  
                 // Immutable optional fields
                  
                 // Mutable optional fields
                 var id: Int = 0,
                 var resultSummaryId: Int = 0,
                 
                 var proteinMatchIds: Array[Int] = null, //One of these 2 values should be specified
                 @transient var proteinMatches: Option[Array[ProteinMatch]] = null,
                 
                 protected var typicalProteinMatchId: Int = 0,
                 @transient var typicalProteinMatch: Option[ProteinMatch] = null,
                 
                 var isValidated: Boolean = true,
                 var selectionLevel: Int = 2,

                 var properties: Option[ProteinSetProperties] = None,
                 var proteinMatchPropertiesById: Map[Int, ProteinMatchResultSummaryProperties ] = null
                 
                 ) {
  
  lazy val peptideSetId = peptideSet.id
  
  // Requirements
  require( proteinMatchIds != null || proteinMatches != null )
  
  def getProteinMatchIds : Array[Int] = { if(proteinMatches != null && proteinMatches != None) proteinMatches.get.map(_.id)  else proteinMatchIds  }

  def getTypicalProteinMatchId : Int = { if(typicalProteinMatch != null && typicalProteinMatch != None) typicalProteinMatch.get.id else typicalProteinMatchId }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinSetProperties

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ProteinMatchResultSummaryProperties

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SequenceMatch ( // Required fields                     
                   val start: Int,
                   val end: Int,
                   val residueBefore: Char,
                   val residueAfter: Char,
                   
                   // Immutable optional fields
                   val isDecoy: Boolean = false,
                   var resultSetId : Int = 0,
                   
                   // Mutable optional fields
                   protected var peptideId: Int = 0,
                   @transient var peptide: Option[Peptide] = null,
                   
                   var bestPeptideMatchId: Int = 0,
                   @transient var bestPeptideMatch: Option[PeptideMatch] = null,
                   
                   var properties: Option[SequenceMatchProperties] = None
                   
                   ) {
  
  // Requirements
  require( start > 0 , "peptide sequence position must be striclty positive" )
  require( end > start , "peptide end position must be greater than start position" )
  
  def getPeptideId : Int = { if(peptide != null && peptide != None) peptide.get.id else peptideId }

  def getBestPeptideMatchId : Int = { if(bestPeptideMatch != null && bestPeptideMatch != None) bestPeptideMatch.get.id else bestPeptideMatchId }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SequenceMatchProperties


