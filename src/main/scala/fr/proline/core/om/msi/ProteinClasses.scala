package fr.proline.core.om.msi

package ProteinClasses {

  import scala.collection.mutable.HashMap
  import fr.proline.core.om.msi.PeptideClasses.PeptideSet
  import fr.proline.core.om.msi.PeptideClasses.PeptideMatch
  import fr.proline.core.om.msi.PeptideClasses.Peptide
  import fr.proline.core.om.msi.ResultSetClasses.ResultSet
  import fr.proline.core.om.msi.MsiSearchClasses.SeqDatabase
  import fr.proline.core.om.msi.PeptideClasses.PeptideInstance
  
  class BioSequence ( // Required fields
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
  
  }

  class SequenceMatch ( // Required fields                     
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
  
  class ProteinMatch ( 
                     // Required fields
                     val accession: String,
                     val description: String,
                     
                     // Immutable optional fields
                     val isDecoy: Boolean = false,
                     
                     // Mutable optional fields                     
                     var id: Int = 0,
                     var taxonId: Int = 0,
                     var resultSetId: Int = 0,
                     
                     private var bioSequenceId: Int = 0,
                     var bioSequence: Option[BioSequence] = null,
                     
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
  
    def getProteinId : Int = { if(bioSequence != null && bioSequence != None) bioSequence.get.id else bioSequenceId }
   
    def getSeqDatabasesIds : Array[Int] = { if(seqDatabases != null && seqDatabases != None) seqDatabases.get.map(_.id)  else seqDatabaseIds  }
    
  }
 
 
  class ProteinSet ( 
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
}