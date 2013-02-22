package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.reflect.BeanProperty

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.util.misc.InMemoryIdGen

object ResultSet extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ResultSet ( 
                   // Required fields
                   val peptides: Array[Peptide],
                   val peptideMatches: Array[PeptideMatch],
                   val proteinMatches: Array[ProteinMatch],
                   val isDecoy: Boolean,
                   val isNative: Boolean,
                   
                   // Immutable optional fields
                   
                   // Mutable optional fields
                   var id: Int = 0,
                   var name: String = null,
                   var description: String = null,
                   var isQuantified: Boolean = false,                    
                   
                   protected var msiSearchId: Int = 0,
                   var msiSearch: MSISearch = null,
                  
                   protected var decoyResultSetId: Int = 0,
                   @transient var decoyResultSet: Option[ResultSet] = null,
                   
                   var properties: Option[ResultSetProperties] = None
                   
                   ) {
  
  // Requirements
  require( peptides != null && peptideMatches != null & proteinMatches != null )
  
  def getMSISearchId : Int = { if(msiSearch != null) msiSearch.id else msiSearchId }
  
  def getDecoyResultSetId : Int = { if(decoyResultSet != null && decoyResultSet != None) decoyResultSet.get.id else decoyResultSetId }
  
  def peptideById: Map[Int, Peptide] = {
    
    val tmpPeptideById = Map() ++ peptides.map { pep => ( pep.id -> pep ) }      
    if( tmpPeptideById.size != peptides.length ) 
      throw new Exception( "duplicated peptide id" )

    tmpPeptideById

  }
  
  def peptideMatchById: Map[Int, PeptideMatch] = {
    
    val tmpPeptideMatchById = Map() ++ peptideMatches.map { pepMatch => ( pepMatch.id -> pepMatch ) }      
    if( tmpPeptideMatchById.size != peptideMatches.length ) 
      throw new Exception( "duplicated peptide match id" )

    tmpPeptideMatchById

  }
  
  def proteinMatchById: Map[Int, ProteinMatch] = {
    
    val tmpProtMatchById = Map() ++ proteinMatches.map { protMatch => ( protMatch.id -> protMatch ) }      
    if( tmpProtMatchById.size != proteinMatches.length ) 
      throw new Exception( "duplicated protein match id" )

    tmpProtMatchById

  }
  
  def getUniquePeptideSequences(): Array[String] = {    
    this.peptides map { _.sequence } distinct
  }
  
  def getProteins(): Option[Array[Protein]] = {
    
    val proteins = new ArrayBuffer[Protein](0)
    for( protMatch <- proteinMatches )
      if( protMatch.protein != None ) proteins += protMatch.protein.get
    
    if( proteins.length == 0 ) None
    else Some(proteins.toArray)
    
  }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ResultSetProperties


object ResultSummary extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ResultSummary (
                   // Required fields
                   val peptideInstances: Array[PeptideInstance],
                   val peptideSets: Array[PeptideSet],
                   val proteinSets: Array[ProteinSet],
                   //val isDecoy: Boolean,                   
                   
                   // Immutable optional fields
                   
                   // Mutable optional fields
                   var id: Int = 0,
                   var description: String = null,
                   var isQuantified: Boolean = false,
                   val modificationTimestamp: java.util.Date = new java.util.Date,
                   
                   protected var resultSetId: Int = 0,
                   @transient var resultSet: Option[ResultSet] = None,
                   
                   var decoyResultSummaryId: Int = 0,
                   @transient var decoyResultSummary: Option[ResultSummary] = null,
                   
                   var properties: Option[ResultSummaryProperties] = None
                   
                   ) {
  
  // Requirements
  require( peptideInstances != null && proteinSets != null )
  
  def getResultSetId : Int = { if(resultSet != None) resultSet.get.id else resultSetId }
  
  def getDecoyResultSummaryId : Int = { if(decoyResultSummary != null && decoyResultSummary != None) decoyResultSummary.get.id else decoyResultSummaryId }
   
  def peptideInstanceById: Map[Int, PeptideInstance] = {
    
    val tmpPepInstById = Map() ++ peptideInstances.map { pepInst => ( pepInst.id -> pepInst ) }      
    if( tmpPepInstById.size != peptideInstances.length ) 
      throw new Exception( "duplicated peptide instance id" )

    tmpPepInstById

  }
  
  def proteinSetById: Map[Int, ProteinSet] = {
    
    val tmpProtSetById = Map() ++ proteinSets.map { protSet => ( protSet.id -> protSet ) }      
    if( tmpProtSetById.size != proteinSets.length ) 
      throw new Exception( "duplicated protein set id" )

    tmpProtSetById

  }
  
  def getBestPepMatchesByProtSetId(): Map[Int,Array[PeptideMatch]] = {
    
    if( this.resultSet == None ) {
      throw new Exception("a result set should linked to the result summary first")
    }
    
    val resultSet = this.resultSet.get
    
    // Retrieve object maps
    val peptideMatchMap = resultSet.peptideMatchById
    val proteinMatchMap = resultSet.proteinMatchById 
    
    val bestPepMatchesByProtSetIdBuilder = collection.immutable.HashMap.newBuilder[Int,Array[PeptideMatch]]
    for( proteinSet <- this.proteinSets ) {
      
      // Create a hash which will remove possible redundancy (same peptide located at different positions on the protein sequence) 
      val bestPepMatchByMsQueryId = new HashMap[Int,PeptideMatch]
      
      // Iterate over sequence matches of the protein set to find the best peptide matches
      for( val proteinMatchId <- proteinSet.getProteinMatchIds ) {
        
        val proteinMatch = proteinMatchMap(proteinMatchId)
        val seqMatches = proteinMatch.sequenceMatches
        
        for( val seqMatch <- seqMatches ) {
          val bestPeptideMatch = peptideMatchMap.get( seqMatch.getBestPeptideMatchId )
          
          // if the peptide is not in the map (its score may be too low)
          if( bestPeptideMatch != None ) {
            bestPepMatchByMsQueryId += ( bestPeptideMatch.get.msQuery.id -> bestPeptideMatch.get )
          }
        }
      }
      
      // Retrieve a non-redundant list of best peptide matches for this protein set
      val protSetBestPeptideMatches = bestPepMatchByMsQueryId.values
      bestPepMatchesByProtSetIdBuilder += ( proteinSet.id -> protSetBestPeptideMatches.toArray )
      
    }
    
    bestPepMatchesByProtSetIdBuilder.result
    
  }
  
  def getAllPeptideMatchesByProteinSetId(): Map[Int,Array[PeptideMatch]] = {
    
    val peptideMatchMap = this.resultSet.get.peptideMatchById
    
    val peptideMatchesByProteinSetId = Map.newBuilder[Int,Array[PeptideMatch]]
    for( proteinSet <- this.proteinSets ) {
      
      val pepMatchesByMsQueryId = new HashMap[Int,ArrayBuffer[PeptideMatch]]
      
      // Iterate over peptide instances of the protein set to find the best peptide match of each peptide instance
      val peptideInstances = proteinSet.peptideSet.getPeptideInstances    
      for( peptideInstance <- peptideInstances ) {
        
        for( peptideMatchId <- peptideInstance.peptideMatchIds ) {
          val pepMatch = peptideMatchMap(peptideMatchId)
          val msqPepMatches = pepMatchesByMsQueryId.getOrElseUpdate( pepMatch.msQueryId, new ArrayBuffer[PeptideMatch] )
          msqPepMatches += pepMatch
        }
        
      }
      
      // Take arbitrary the first isobaric peptide if we have multiple ones for a given MS query
      // FIXME: find an other solution
      val protSetPeptideMatches = pepMatchesByMsQueryId.values.map { _(0) }
      peptideMatchesByProteinSetId += proteinSet.id -> protSetPeptideMatches.toArray
      
    }
    
    peptideMatchesByProteinSetId.result
    
  }
  
}

// TODO: change privacy to protected => allows access only to getters/setters
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ResultSummaryProperties (
  @BeanProperty protected var validationProperties: Option[RsmValidationProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmValidationProperties (
  @BeanProperty protected var params: RsmValidationParamsProperties,
  @BeanProperty protected var results: RsmValidationResultsProperties
) 

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmValidationParamsProperties (
  @BeanProperty protected var peptideExpectedFdr: Option[Float] = None,
  @BeanProperty protected var peptideFilters: Option[Array[FilterDescriptor]] = None,
  @BeanProperty protected var proteinExpectedFdr: Option[Float] = None,
  @BeanProperty protected var proteinFilters: Option[Array[FilterDescriptor]] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmValidationResultsProperties (
  @BeanProperty protected var peptideResults: Option[RsmPepMatchValidationResultsProperties] = None,
  @BeanProperty protected var proteinResults: Option[RsmProtSetValidationResultsProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmPepMatchValidationResultsProperties (
  @BeanProperty protected var targetMatchesCount: Int,
  @BeanProperty protected var decoyMatchesCount: Option[Int] = None,
  @BeanProperty protected var fdr: Option[Float] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RsmProtSetValidationResultsProperties (
  //@BeanProperty var results: Option[RsmValidationProperties] = None
  // TODO: expectedRocPoint and RocPoints model
)
