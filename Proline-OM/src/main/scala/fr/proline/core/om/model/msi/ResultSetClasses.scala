package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.profi.util.misc.InMemoryIdGen
import com.typesafe.scalalogging.slf4j.Logging

object ResultSet extends InMemoryIdGen

case class ResultSet(
  
  // Required fields
  val peptides: Array[Peptide],
  val peptideMatches: Array[PeptideMatch],
  val proteinMatches: Array[ProteinMatch],
  val isDecoy: Boolean,
   // true if the ResultSet correspond to a "search result", false otherwise
  val isSearchResult: Boolean,
  // true if only validated entities are loaded, false otherwise
  val isValidatedContent: Boolean,
  
  // Immutable optional fields

  // Mutable optional fields
  var mergedResultSummaryId : Long = 0L,
  var id: Long = 0,
  var name: String = null,
  var description: String = null,
  var isQuantified: Boolean = false, // TODO: remove me ???

  protected var msiSearchId: Long = 0,
  var msiSearch: Option[MSISearch] = None,
  var childMsiSearches: Array[MSISearch] = Array(),

  protected var decoyResultSetId: Long = 0,
  @transient var decoyResultSet: Option[ResultSet] = None,

  var properties: Option[ResultSetProperties] = None
) extends Cloneable {

  // Requirements
  require(peptides != null && peptideMatches != null & proteinMatches != null)
  require(msiSearch != null, "MSI search can't be null => provide None instead")
  require(decoyResultSet != null, "decoy result set can't be null => provide None instead")
  
  // Make a deep clone of the ResultSet
  override def clone(): ResultSet = {
    this.copy(
      peptideMatches = this.peptideMatches.map(_.copy()),
      // do we need to clone sequence matches ???
      proteinMatches = this.proteinMatches.map(_.copy()),
      msiSearch = msiSearch.map(_.copy()),
      decoyResultSet = this.decoyResultSet.map(_.clone()),
      properties = this.properties.map( _.copy() )
    )
  }

  def getMSISearchId: Long = { if (msiSearch.isDefined) msiSearch.get.id else msiSearchId }
  
  def setDecoyResultSetId(decoyRSId: Long) {
    require(decoyResultSet.isEmpty || (decoyResultSet.get.id == decoyRSId), "Inconsistent decoyRSId")

    decoyResultSetId = decoyRSId
  }

  def getDecoyResultSetId: Long = { if (decoyResultSet.isDefined) decoyResultSet.get.id else decoyResultSetId }

  def peptideById: Map[Long, Peptide] = {

    val tmpPeptideById = Map() ++ peptides.map { pep => (pep.id -> pep) }
    if (tmpPeptideById.size != peptides.length)
      throw new Exception("duplicated peptide id")

    tmpPeptideById

  }

  def peptideMatchById: Map[Long, PeptideMatch] = {

    val tmpPeptideMatchById = Map() ++ peptideMatches.map { pepMatch => (pepMatch.id -> pepMatch) }
    if (tmpPeptideMatchById.size != peptideMatches.length)
      throw new Exception("duplicated peptide match id")

    tmpPeptideMatchById

  }

  def proteinMatchById: Map[Long, ProteinMatch] = {

    val tmpProtMatchById = Map() ++ proteinMatches.map { protMatch => (protMatch.id -> protMatch) }
    if (tmpProtMatchById.size != proteinMatches.length)
      throw new Exception("duplicated protein match id")

    tmpProtMatchById

  }

  def getUniquePeptideSequences(): Array[String] = {
    this.peptides map { _.sequence } distinct
  }

  def getProteins(): Option[Array[Protein]] = {

    val proteins = new ArrayBuffer[Protein](0)
    for (protMatch <- proteinMatches)
      if (protMatch.protein.isDefined) proteins += protMatch.protein.get

    if (proteins.length == 0) None
    else Some(proteins.toArray)

  }

  def getTargetDecoyMode(): Option[String] = {
    if (this.properties.isDefined) this.properties.get.getTargetDecoyMode
    else None
  }

  def getPeptideMatchesByProteinMatch(): Map[ProteinMatch, ArrayBuffer[PeptideMatch]] = {
    val resultBuilder = Map.newBuilder[ProteinMatch, ArrayBuffer[PeptideMatch]]
    val tmpPeptideMatchByPeptideId = peptideMatches.groupBy(_.peptideId)
    
    for( protMatch <- proteinMatches ) {
      val protMatchPeptMatches = new ArrayBuffer[PeptideMatch]()
      if (protMatch.sequenceMatches != null) {
        protMatch.sequenceMatches.foreach(seqMatch => {
          val pepMatchesForCurrent = tmpPeptideMatchByPeptideId.getOrElse(seqMatch.getPeptideId, Array.empty[PeptideMatch])
          protMatchPeptMatches ++= pepMatchesForCurrent
        }) //end go throudh protMatch SeqMarches 
      }
      resultBuilder += protMatch -> protMatchPeptMatches.distinct
    } //End go through ProtMarches
    
    resultBuilder.result()
  } 
}

case class ResultSetProperties(
  @BeanProperty var targetDecoyMode: Option[String] = None, // CONCATENATED | SEPARATED
  @BeanProperty var mascotImportProperties: Option[MascotImportProperties] = None,
  @BeanProperty var omssaImportProperties: Option[OmssaImportProperties] = None
)

case class MascotImportProperties(
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var ionsScoreCutoff: Option[Float] = None, // it's ions score not ion score
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var subsetsThreshold: Option[Float] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var proteinsPvalueCutoff: Option[Float] = None
)

case class OmssaImportProperties(
  @BeanProperty var rawSettings: Option[Map[String, String]] = None
)

object ResultSummary extends InMemoryIdGen

case class ResultSummary(
  // Required fields
  val peptideInstances: Array[PeptideInstance],
  val peptideSets: Array[PeptideSet],
  val proteinSets: Array[ProteinSet],
  //val isDecoy: Boolean,

  // Immutable optional fields

  // Mutable optional fields
  var id: Long = 0,
  var description: String = null,
  var isQuantified: Boolean = false,
  val modificationTimestamp: java.util.Date = new java.util.Date,

  protected var resultSetId: Long = 0,
  @transient var resultSet: Option[ResultSet] = None,

  private var decoyResultSummaryId: Long = 0,
  @transient var decoyResultSummary: Option[ResultSummary] = null,

  var properties: Option[ResultSummaryProperties] = None) extends Logging  {

  // Requirements
  require(peptideInstances != null && proteinSets != null)
  
  def getValidatedResultSet(): Option[ResultSet] = {
    resultSet.map { rs =>
      if( rs.isValidatedContent ) rs
      else {
        new ValidatedResultSetBuilder(this).getValidatedResultSet()
      }
    }
  }

  def getResultSetId: Long = { if (resultSet.isDefined) resultSet.get.id else resultSetId }

  def setDecoyResultSummaryId(decoyRSMId: Long) {
    require((decoyResultSummary == null) || decoyResultSummary.isEmpty || (decoyResultSummary.get.id == decoyRSMId), "Inconsistent decoyRSId")

    decoyResultSummaryId = decoyRSMId
  }
  
  def getDecoyResultSummaryId: Long = { if (decoyResultSummary != null && decoyResultSummary.isDefined) decoyResultSummary.get.id else decoyResultSummaryId }

  def peptideInstanceById: Map[Long, PeptideInstance] = {

    val tmpPepInstById = Map() ++ peptideInstances.map { pepInst => (pepInst.id -> pepInst) }
    require(tmpPepInstById.size == peptideInstances.length, "duplicated peptide instance id")

    tmpPepInstById

  }

  def proteinSetById: Map[Long, ProteinSet] = {

    val tmpProtSetById = Map() ++ proteinSets.map { protSet => (protSet.id -> protSet) }
    require(tmpProtSetById.size == proteinSets.length, "duplicated protein set id")

    tmpProtSetById
  }

  def getBestValidatedPepMatchesByPepSetId(): Map[Long, Array[PeptideMatch]] = {

    require(this.resultSet.isDefined, "a result set should be linked to the result summary first")

    // Retrieve the result set
    val resultSet = this.resultSet.get

    // Filter peptide matches if wanted
    val rsPepMatches = resultSet.peptideMatches.filter(_.isValidated == true)

    // Retrieve object maps
    val peptideMatchesByPepId = rsPepMatches.groupBy(_.peptide.id)
    val peptideMatchById = Map() ++ rsPepMatches.map(pm => pm.id -> pm)
    val proteinMatchById = resultSet.proteinMatchById

    val bestPepMatchesByPepSetIdBuilder = collection.immutable.HashMap.newBuilder[Long, Array[PeptideMatch]]
    for (peptideSet <- this.peptideSets) {

      // Create a hash which will remove possible redundancy (same peptide located at different positions on the protein sequence) 
      val bestPepMatchByMsQueryId = new HashMap[Long, PeptideMatch]

      // Iterate over sequence matches of the protein set to find the best peptide matches
      for (pepSetItem <- peptideSet.items) {

        val pepInstance = pepSetItem.peptideInstance
        var bestPeptideMatch = peptideMatchById.get(pepSetItem.peptideInstance.bestPeptideMatchId)

        // Try to find another best peptide match if the default one can't be found
        if (bestPeptideMatch.isEmpty) {
          val otherValidPepMatches = peptideMatchesByPepId.get(pepInstance.peptide.id)
          if (otherValidPepMatches.isDefined) {
            val sortedPepMatches = otherValidPepMatches.get.sortWith((a, b) => a.score > b.score)
            bestPeptideMatch = Some(sortedPepMatches(0))
          }
        }

        // If the best peptide match has been found
        if (bestPeptideMatch.isDefined) {
          bestPepMatchByMsQueryId += (bestPeptideMatch.get.msQuery.id -> bestPeptideMatch.get)
        }

      }

      // Retrieve a non-redundant list of best peptide matches for this protein set
      val pepSetBestPeptideMatches = bestPepMatchByMsQueryId.values
      bestPepMatchesByPepSetIdBuilder += (peptideSet.id -> pepSetBestPeptideMatches.toArray)

    }

    bestPepMatchesByPepSetIdBuilder.result

  }

  def getBestPepMatchesByProtSetId(): Map[Long, Array[PeptideMatch]] = {

    require(this.resultSet.isDefined, "a result set should be linked to the result summary first")

    val resultSet = this.resultSet.get

    // Retrieve object maps
    val peptideMatchMap = resultSet.peptideMatchById
    val proteinMatchMap = resultSet.proteinMatchById

    val bestPepMatchesByProtSetIdBuilder = collection.immutable.HashMap.newBuilder[Long, Array[PeptideMatch]]
    for (proteinSet <- this.proteinSets) {

      // Create a hash which will remove possible redundancy (same peptide located at different positions on the protein sequence) 
      val bestPepMatchByMsQueryId = new HashMap[Long, PeptideMatch]

      // Iterate over sequence matches of the protein set to find the best peptide matches
      for (proteinMatchId <- proteinSet.getProteinMatchIds) {

        val proteinMatch = proteinMatchMap(proteinMatchId)
        val seqMatches = proteinMatch.sequenceMatches

        for (seqMatch <- seqMatches) {
          val bestPeptideMatch = peptideMatchMap.get(seqMatch.getBestPeptideMatchId)

          // If the peptide is not in the map (its score may be too low)
          if (bestPeptideMatch.isDefined) {
            bestPepMatchByMsQueryId += (bestPeptideMatch.get.msQuery.id -> bestPeptideMatch.get)
          }
        }
      }

      // Retrieve a non-redundant list of best peptide matches for this protein set
      val protSetBestPeptideMatches = bestPepMatchByMsQueryId.values
      bestPepMatchesByProtSetIdBuilder += (proteinSet.id -> protSetBestPeptideMatches.toArray)

    }

    bestPepMatchesByProtSetIdBuilder.result

  }

  def getAllPeptideMatchesByPeptideSetId(): Map[Long, Array[PeptideMatch]] = {

    val peptideMatchMap = this.resultSet.get.peptideMatchById

    val peptideMatchesByPepSetId = Map.newBuilder[Long, Array[PeptideMatch]]
    for (peptideSet <- this.peptideSets) {

      val pepMatches = new ArrayBuffer[PeptideMatch]

      // Iterate over peptide instances of the peptide set
      val peptideInstances = peptideSet.getPeptideInstances
      for (peptideInstance <- peptideInstances; peptideMatchId <- peptideInstance.getPeptideMatchIds) {
        val pepMatch = peptideMatchMap(peptideMatchId)
        pepMatches += pepMatch
      }
      
      peptideMatchesByPepSetId += peptideSet.id -> pepMatches.toArray

    }

    peptideMatchesByPepSetId.result

  }
  
}

// TODO: change privacy to protected => allows access only to getters/setters
case class ResultSummaryProperties(
  @BeanProperty var validationProperties: Option[RsmValidationProperties] = None
)

case class RsmValidationProperties(
  @BeanProperty var params: RsmValidationParamsProperties,
  @BeanProperty var results: RsmValidationResultsProperties
)

case class RsmValidationParamsProperties(
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var peptideExpectedFdr: Option[Float] = None,
  
  @BeanProperty var peptideFilters: Option[Array[FilterDescriptor]] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var proteinExpectedFdr: Option[Float] = None,
  
  @BeanProperty var proteinFilters: Option[Array[FilterDescriptor]] = None
)

case class RsmValidationResultsProperties(
  @BeanProperty var peptideResults: Option[RsmValidationResultProperties] = None,
  @BeanProperty var proteinResults: Option[RsmValidationResultProperties] = None
)

case class RsmValidationResultProperties(
  // TODO: expectedRocPoint and RocPoints model
  @BeanProperty var targetMatchesCount: Int,
  @BeanProperty var decoyMatchesCount: Option[Int] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var fdr: Option[Float] = None
)

class ValidatedResultSetBuilder( rsm: ResultSummary ) {
  require( rsm.resultSet.isDefined, "a resultSet must be defined")
  
  protected val( validPepIdSet, validPepMatchIdSet, validProtMatchIdSet ) = {
    
    // Build the set of unique valid entities ids
    val validPepIdSetBuilder = Set.newBuilder[Long]
    val validPepMatchIdSetBuilder = Set.newBuilder[Long]
    val validProtMatchIdSetBuilder = Set.newBuilder[Long]
    
    // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
    for (proteinSet <- rsm.proteinSets) {
      if (proteinSet.isValidated) {
        val peptideSet = proteinSet.peptideSet
        
        for (pepInstance <- peptideSet.getPeptideInstances) {
          validPepIdSetBuilder += pepInstance.peptide.id
          validPepMatchIdSetBuilder ++= pepInstance.getPeptideMatchIds
        }
        
        validProtMatchIdSetBuilder ++= proteinSet.getProteinMatchIds
      }
    }
    
    (
      validPepIdSetBuilder.result(),
      validPepMatchIdSetBuilder.result(),
      validProtMatchIdSetBuilder.result()
    )
  }

  protected def getValidatedPeptideMatches(rs: ResultSet): Iterable[PeptideMatch] = {
    rs.peptideMatches.filter { pm => validPepMatchIdSet.contains(pm.id) }
  }

  protected def getValidatedProteinMatches(rs: ResultSet): Iterable[ProteinMatch] = {
    rs.proteinMatches.filter { pm => validProtMatchIdSet.contains(pm.id) }
  }

  protected def getValidatedSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch] = {
    proteinMatch.sequenceMatches.filter {sm => validPepIdSet.contains(sm.getPeptideId) }
  }
  
  def getValidatedResultSet(): ResultSet = {
    
    val rs = rsm.resultSet.get
    val validatedProtMatches = this.getValidatedProteinMatches(rs).toArray
    val validatedPepMatches = this.getValidatedPeptideMatches(rs).toArray
    val pepMatchesByPepId = validatedPepMatches.groupBy(_.peptide.id)
    val validatedPeptides = validatedPepMatches.map(_.peptide).distinct
    
    val validatedProtAndSeqMatches = for (proteinMatch <- validatedProtMatches) yield {
      
      val newSeqMatches = for (
        seqMatch <- proteinMatch.sequenceMatches;
        if pepMatchesByPepId.contains(seqMatch.getPeptideId)
      ) yield seqMatch
      
      proteinMatch.copy(
        sequenceMatches = newSeqMatches
      )
    }
    
    rs.copy(
      peptides = validatedPeptides,
      peptideMatches = validatedPepMatches,
      proteinMatches = validatedProtAndSeqMatches
    )
  }

}

