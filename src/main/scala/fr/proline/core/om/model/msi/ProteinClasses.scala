package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import fr.proline.core.utils.misc.InMemoryIdGen

object Protein extends InMemoryIdGen{
  
  /** A percentage (between 0 and 100) expressing the sequence coverage of the protein */
  def calcSequenceCoverage( protSeqLength: Int, seqPositions: Array[Tuple2[Int,Int]] ): Float = {
    
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
}

//def calcMolecularWeight
//def calcIsoelectricPoint
  
case class Protein ( // Required fields                     
                   val sequence: String,
                   
                   // Immutable optional fields
                   val id: Int = 0,
                   val alphabet: String = "aa",
                   
                   // Mutable optional fields
                   var mass: Double = 0,
                   var pi: Float = 0,
                   var crc64: String = null

                   ) {
  // Requirements
  require( sequence != null )
  
  lazy val length = sequence.length()
  
  def getSequenceCoverage( seqPositions: Array[Tuple2[Int,Int]] ): Float = {
    Protein.calcSequenceCoverage( this.length, seqPositions )
  }

}

case class SequenceMatch ( // Required fields                     
                   val start: Int,
                   val end: Int,
                   val residueBefore: Char,
                   val residueAfter: Char,
                   
                   // Immutable optional fields
                   val isDecoy: Boolean = false,
                   val resultSetId : Int = 0,
                   
                   // Mutable optional fields
                   private var peptideId: Int = 0,
                   var peptide: Option[Peptide] = null,
                   
                   private var bestPeptideMatchId: Int = 0,
                   var bestPeptideMatch: Option[PeptideMatch] = null,
                   
                   var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                   
                   ) {
  
  // Requirements
  require( start > 0 && end > start )
  require( peptideId > 0 || peptide != null )
  
  def getPeptideId : Int = { if(peptide != null && peptide != None) peptide.get.id else peptideId }

  def getBestPeptideMatchId : Int = { if(bestPeptideMatch != null && bestPeptideMatch != None) bestPeptideMatch.get.id else bestPeptideMatchId }
  
}

object ProteinMatch extends InMemoryIdGen {
  
}  
  
case class ProteinMatch ( 
                   // Required fields                    
                   val accession: String,
                   val description: String,
                   
                   // Immutable optional fields
                   val isDecoy: Boolean = false,
                   
                   // Mutable optional fields                     
                   var id: Int = 0,
                   var taxonId: Int = 0,    
                   var resultSetId: Int = 0,

                   private var proteinId: Int = 0,
                   var protein: Option[Protein] = null,

                   private var seqDatabaseIds: Array[Int] = null,
                   var seqDatabases: Option[Array[SeqDatabase]] = null,
                   
                   var score: Float = 0,
                   var scoreType: String = null,
                   var coverage: Float = 0,
                   var peptideMatchesCount: Int = 0,
                   var sequenceMatches: Array[SequenceMatch] = null,
                   
                   var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                   
                   ) {
  
  // Requirements
  require( accession != null && description != null )

  def getProteinId : Int = { if(protein != null && protein != None) protein.get.id else proteinId }
 
  def getSeqDatabasesIds : Array[Int] = { if(seqDatabases != null && seqDatabases != None) seqDatabases.get.map(_.id)  else seqDatabaseIds  }
  
}
 
object ProteinSet extends InMemoryIdGen
case class ProteinSet ( 
                   // Required fields
                 val peptideSet: PeptideSet,
  
                 // Immutable optional fields
                  
                 // Mutable optional fields
                 var id: Int = 0,
                 var resultSummaryId: Int = 0,
                 
                 private var proteinMatchIds: Array[Int] = null, //One of these 2 values should be specified
                 var proteinMatches: Option[Array[ProteinMatch]] = null,
                 
                 private var typicalProteinMatchId: Int = 0,
                 var typicalProteinMatch: Option[ProteinMatch] = null,
                 
                 var score: Float = 0,
                 var scoreType: String = null,
                 var isValidated: Boolean = true,
                 var selectionLevel: Int = 2,
                 var hasPeptideSubset: Boolean = false,

                 var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                 
                 ) {

  // Requirements
  require( proteinMatchIds != null || proteinMatches != null )
  
  def getProteinMatchIds : Array[Int] = { if(proteinMatches != null && proteinMatches != None) proteinMatches.get.map(_.id)  else proteinMatchIds  }

  def getTypicalProteinMatchId : Int = { if(typicalProteinMatch != null && typicalProteinMatch != None) typicalProteinMatch.get.id else typicalProteinMatchId }
  
}
