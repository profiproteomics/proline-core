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
@JsonInclude(Include.NON_NULL)
case class ResultSet(
  // Required fields
  val peptides: Array[Peptide],
  val peptideMatches: Array[PeptideMatch],
  val proteinMatches: Array[ProteinMatch],
  val isDecoy: Boolean,
  val isNative: Boolean,

  // Immutable optional fields

  // Mutable optional fields
  var id: Long = 0,
  var name: String = null,
  var description: String = null,
  var isQuantified: Boolean = false,

  protected var msiSearchId: Long = 0,
  var msiSearch: Option[MSISearch] = None,

  protected var decoyResultSetId: Long = 0,
  @transient var decoyResultSet: Option[ResultSet] = None,

  var properties: Option[ResultSetProperties] = None) {

  // Requirements
  require(peptides != null && peptideMatches != null & proteinMatches != null)
  require(msiSearch != null, "MSI search can't be null => provide None instead")
  require(decoyResultSet != null, "decoy result set can't be null => provide None instead")

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
      if (protMatch.protein != None) proteins += protMatch.protein.get

    if (proteins.length == 0) None
    else Some(proteins.toArray)

  }

  // TMP workaround for result sets which have this value defined at the search settings level
  def getTargetDecoyMode(): Option[String] = {

    // Try to retrieve the parameter form SearchSettings first
    val msiSearchOpt = this.msiSearch
    val ssTDMode = if (msiSearchOpt.isDefined && msiSearchOpt.get.searchSettings.properties.isDefined) {
      msiSearchOpt.get.searchSettings.properties.get.getTargetDecoyMode
    } else None

    // If not defined in search settings => try to retrieve it from result set properties
    if (ssTDMode.isDefined) ssTDMode
    else if (this.properties.isDefined) this.properties.get.getTargetDecoyMode
    else None
  }

}

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class ResultSetProperties(
  @BeanProperty var targetDecoyMode: Option[String] = None,
  @BeanProperty var mascotImportProperties: Option[MascotImportProperties] = None)

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class MascotImportProperties(
  @BeanProperty var ionsScoreCutoff: Option[Float] = None, // it's ions score not ion score
  @BeanProperty var subsetsThreshold: Option[Float] = None,
  @BeanProperty var proteinsPvalueCutoff: Option[Float] = None)

object ResultSummary extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
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

  var decoyResultSummaryId: Long = 0,
  @transient var decoyResultSummary: Option[ResultSummary] = null,

  var properties: Option[ResultSummaryProperties] = None) {

  // Requirements
  require(peptideInstances != null && proteinSets != null)

  def getResultSetId: Long = { if (resultSet != None) resultSet.get.id else resultSetId }

  def getDecoyResultSummaryId: Long = { if (decoyResultSummary != null && decoyResultSummary != None) decoyResultSummary.get.id else decoyResultSummaryId }

  def peptideInstanceById: Map[Long, PeptideInstance] = {

    val tmpPepInstById = Map() ++ peptideInstances.map { pepInst => (pepInst.id -> pepInst) }
    if (tmpPepInstById.size != peptideInstances.length)
      throw new Exception("duplicated peptide instance id")

    tmpPepInstById

  }

  def proteinSetById: Map[Long, ProteinSet] = {

    val tmpProtSetById = Map() ++ proteinSets.map { protSet => (protSet.id -> protSet) }
    if (tmpProtSetById.size != proteinSets.length)
      throw new Exception("duplicated protein set id")

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
          if (bestPeptideMatch != None) {
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

      val pepMatchesByMsQueryId = new HashMap[Long, ArrayBuffer[PeptideMatch]]

      // Iterate over peptide instances of the peptide set
      val peptideInstances = peptideSet.getPeptideInstances
      for (peptideInstance <- peptideInstances; peptideMatchId <- peptideInstance.peptideMatchIds) {
        val pepMatch = peptideMatchMap(peptideMatchId)
        val msqPepMatches = pepMatchesByMsQueryId.getOrElseUpdate(pepMatch.msQueryId, new ArrayBuffer[PeptideMatch])
        msqPepMatches += pepMatch
      }

      // Take arbitrary the first isobaric peptide if we have multiple ones for a given MS query
      // FIXME: find an other solution
      val pepSetPeptideMatches = pepMatchesByMsQueryId.values.map { _(0) }
      peptideMatchesByPepSetId += peptideSet.id -> pepSetPeptideMatches.toArray

    }

    peptideMatchesByPepSetId.result

  }

}

// TODO: change privacy to protected => allows access only to getters/setters
@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class ResultSummaryProperties(
  @BeanProperty var validationProperties: Option[RsmValidationProperties] = None)

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class RsmValidationProperties(
  @BeanProperty var params: RsmValidationParamsProperties,
  @BeanProperty var results: RsmValidationResultsProperties)

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class RsmValidationParamsProperties(
  @BeanProperty var peptideExpectedFdr: Option[Float] = None,
  @BeanProperty var peptideFilters: Option[Array[FilterDescriptor]] = None,
  @BeanProperty var proteinExpectedFdr: Option[Float] = None,
  @BeanProperty var proteinFilters: Option[Array[FilterDescriptor]] = None)

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class RsmValidationResultsProperties(
  @BeanProperty var peptideResults: Option[RsmValidationResultProperties] = None,
  @BeanProperty var proteinResults: Option[RsmValidationResultProperties] = None)

@JsonSnakeCase
@JsonInclude(Include.NON_NULL)
case class RsmValidationResultProperties(
  // TODO: expectedRocPoint and RocPoints model
  @BeanProperty var targetMatchesCount: Int,
  @BeanProperty var decoyMatchesCount: Option[Int] = None,
  @BeanProperty var fdr: Option[Float] = None)


