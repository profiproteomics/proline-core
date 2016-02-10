package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.misc.InMemoryIdGen

object ResultSet extends InMemoryIdGen {
  
  def getPeptideMatchesByProteinMatch(
    pepMatchesByPepId: LongMap[Array[PeptideMatch]],
    proteinMatches: Array[ProteinMatch]
  ): Map[ProteinMatch, ArrayBuffer[PeptideMatch]] = {
    val resultBuilder = Map.newBuilder[ProteinMatch, ArrayBuffer[PeptideMatch]]
    resultBuilder.sizeHint(proteinMatches.length)
    
    for( protMatch <- proteinMatches ) {
      val protMatchPeptMatches = new ArrayBuffer[PeptideMatch]()
      if (protMatch.sequenceMatches != null) {
        protMatch.sequenceMatches.foreach { seqMatch => 
          val pepMatchesForCurrent = pepMatchesByPepId.getOrElse(seqMatch.getPeptideId, Array.empty[PeptideMatch])
          protMatchPeptMatches ++= pepMatchesForCurrent
        } //end go through protMatch SeqMatches 
      }
      resultBuilder += protMatch -> protMatchPeptMatches.distinct
    } //End go through ProtMatches
    
    resultBuilder.result()
  }
  
  def getPeptideMatchesByProteinMatchId(
    pepMatchesByPepId: LongMap[Array[PeptideMatch]],
    proteinMatches: Array[ProteinMatch]
  ): LongMap[Array[PeptideMatch]] = {
    val tmpMap = this.getPeptideMatchesByProteinMatch(pepMatchesByPepId, proteinMatches)
    val longMap = new LongMap[Array[PeptideMatch]](proteinMatches.length)
    
    for( (protMatch,pepMatches) <- tmpMap ) {
      longMap += protMatch.id -> pepMatches.toArray
    }
    
    longMap
  }
}

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

  def getPeptideById(): Map[Long, Peptide] = {

    val tmpPeptideById = Map() ++ peptides.map( pep => (pep.id -> pep) )
    if (tmpPeptideById.size != peptides.length)
      throw new Exception("duplicated peptide id")

    tmpPeptideById
  }

  def getPeptideMatchById(): Map[Long, PeptideMatch] = {

    val tmpPeptideMatchById = Map() ++ peptideMatches.map( pepMatch => (pepMatch.id -> pepMatch) )
    if (tmpPeptideMatchById.size != peptideMatches.length)
      throw new Exception("duplicated peptide match id")

    tmpPeptideMatchById

  }

  def getProteinMatchById(): Map[Long, ProteinMatch] = {

    val tmpProtMatchById = Map() ++ proteinMatches.map( protMatch => (protMatch.id -> protMatch) )
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
    val tmpPeptideMatchByPeptideId = peptideMatches.groupByLong(_.peptideId)    
    ResultSet.getPeptideMatchesByProteinMatch(tmpPeptideMatchByPeptideId, proteinMatches)
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

object ResultSummary extends InMemoryIdGen {
  
  def getBestValidatedPepMatchesByPepSetId(
    peptideMatches: Array[PeptideMatch],
    proteinMatchById: LongMap[ProteinMatch],
    peptideSets: Array[PeptideSet]
  ): Map[Long, Array[PeptideMatch]] = {

    // Filter peptide matches if wanted
    val rsPepMatches = peptideMatches.filter(_.isValidated == true)

    // Retrieve object maps
    val peptideMatchesByPepId = rsPepMatches.view.groupByLong(_.peptide.id)
    val peptideMatchById = rsPepMatches.mapByLong(_.id)

    val bestPepMatchesByPepSetIdBuilder = collection.immutable.Map.newBuilder[Long, Array[PeptideMatch]]
    bestPepMatchesByPepSetIdBuilder.sizeHint(peptideSets.length)
    
    for (peptideSet <- peptideSets) {

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

  def getBestPepMatchesByProtSetId(
    peptideMatchById: LongMap[PeptideMatch],
    proteinMatchById: LongMap[ProteinMatch],
    proteinSets: Array[ProteinSet]
  ): Map[Long, Array[PeptideMatch]] = {

    val bestPepMatchesByProtSetIdBuilder = collection.immutable.HashMap.newBuilder[Long, Array[PeptideMatch]]
    bestPepMatchesByProtSetIdBuilder.sizeHint(proteinSets.length)
    
    for (proteinSet <- proteinSets) {

      // Create a hash which will remove possible redundancy (same peptide located at different positions on the protein sequence) 
      val bestPepMatchByMsQueryId = new HashMap[Long, PeptideMatch]

      // Iterate over sequence matches of the protein set to find the best peptide matches
      for (proteinMatchId <- proteinSet.getProteinMatchIds) {

        val proteinMatch = proteinMatchById(proteinMatchId)
        val seqMatches = proteinMatch.sequenceMatches

        for (seqMatch <- seqMatches) {
          val bestPeptideMatch = peptideMatchById.get(seqMatch.getBestPeptideMatchId)

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

  def getPeptideMatchesByPeptideSetId(
    peptideMatchById: LongMap[PeptideMatch],
    peptideSets: Array[PeptideSet]
  ): Map[Long, Array[PeptideMatch]] = {

    val peptideMatchesByPepSetId = Map.newBuilder[Long, Array[PeptideMatch]]
    for (peptideSet <- peptideSets) {

      val pepMatches = new ArrayBuffer[PeptideMatch]

      // Iterate over peptide instances of the peptide set
      val peptideInstances = peptideSet.getPeptideInstances
      for (peptideInstance <- peptideInstances; peptideMatchId <- peptideInstance.getPeptideMatchIds) {
        val pepMatch = peptideMatchById(peptideMatchId)
        pepMatches += pepMatch
      }
      
      peptideMatchesByPepSetId += peptideSet.id -> pepMatches.toArray
    }

    peptideMatchesByPepSetId.result
  }
  
}

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

  protected var decoyResultSummaryId: Long = 0,
  @transient var decoyResultSummary: Option[ResultSummary] = null,

  var properties: Option[ResultSummaryProperties] = None,
  
  var peptideValidationRocCurve: Option[MsiRocCurve] = None,
  var proteinValidationRocCurve: Option[MsiRocCurve] = None
  
) extends LazyLogging  {

  // Requirements
  require(peptideInstances != null && proteinSets != null)
  
  def getValidatedResultSet(): Option[ResultSet] = {
    resultSet.map { rs =>
      if( rs.isValidatedContent ) rs
      else {
        ValidatedResultSetBuilder.getValidatedResultSet(this)
      }
    }
  }

  def getResultSetId: Long = { if (resultSet.isDefined) resultSet.get.id else resultSetId }

  def setDecoyResultSummaryId(decoyRSMId: Long) {
    require((decoyResultSummary == null) || decoyResultSummary.isEmpty || (decoyResultSummary.get.id == decoyRSMId), "Inconsistent decoyRSId")

    decoyResultSummaryId = decoyRSMId
  }
  
  def getDecoyResultSummaryId: Long = { if (decoyResultSummary != null && decoyResultSummary.isDefined) decoyResultSummary.get.id else decoyResultSummaryId }

  def getPeptideInstanceById: Map[Long, PeptideInstance] = {

    val tmpPepInstById = Map() ++ peptideInstances.map( pepInst => (pepInst.id -> pepInst) )
    require(tmpPepInstById.size == peptideInstances.length, "duplicated peptide instance id")

    tmpPepInstById

  }

  def getProteinSetById(): Map[Long, ProteinSet] = {

    val tmpProtSetById = Map() ++ proteinSets.map( protSet => (protSet.id -> protSet) )
    require(tmpProtSetById.size == proteinSets.length, "duplicated protein set id")

    tmpProtSetById
  }

  def getBestValidatedPepMatchesByPepSetId(): Map[Long, Array[PeptideMatch]] = {

    require(this.resultSet.isDefined, "a result set should be linked to the result summary first")

    // Retrieve the result set
    val resultSet = this.resultSet.get

    val proteinMatchById = new LongMap[ProteinMatch](resultSet.proteinMatches.length)
    proteinMatchById ++= resultSet.getProteinMatchById

    ResultSummary.getBestValidatedPepMatchesByPepSetId(
      resultSet.peptideMatches,
      proteinMatchById,
      this.peptideSets
    )
  }

  def getBestPepMatchesByProtSetId(): Map[Long, Array[PeptideMatch]] = {

    require(this.resultSet.isDefined, "a result set should be linked to the result summary first")

    val rs = this.resultSet.get

    // Retrieve object maps
    val peptideMatchById = new LongMap[PeptideMatch](rs.peptideMatches.length)
    peptideMatchById ++= rs.getPeptideMatchById
    val proteinMatchById = new LongMap[ProteinMatch](rs.proteinMatches.length)
    proteinMatchById ++= rs.getProteinMatchById
    
    ResultSummary.getBestPepMatchesByProtSetId(peptideMatchById, proteinMatchById, proteinSets)
  }

  def getAllPeptideMatchesByPeptideSetId(): Map[Long, Array[PeptideMatch]] = {

    val rs = this.resultSet.get
    val peptideMatchById = new LongMap[PeptideMatch](rs.peptideMatches.length)
    peptideMatchById ++= rs.getPeptideMatchById
    
    ResultSummary.getPeptideMatchesByPeptideSetId(peptideMatchById, peptideSets)
  }
  
}

// TODO: change privacy to protected => allows access only to getters/setters
case class ResultSummaryProperties(
  @BeanProperty var isCoverageUpdated: Option[Boolean] = None,
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
  @BeanProperty var targetMatchesCount: Int,
  @BeanProperty var decoyMatchesCount: Option[Int] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var fdr: Option[Float] = None
)

object ValidatedResultSetBuilder {
  
  def getValidatedResultSet(rsm: ResultSummary): ResultSet = {
   require( rsm.resultSet.isDefined, "a resultSet must be defined in the result summary")
    
    val rs = rsm.resultSet.get
    val( validatedPeptides, validatedPepMatches, validatedProtAndSeqMatches ) =
      ValidatedResultSetBuilder.getValidatedMatches(rsm.proteinSets,rs.peptideMatches,rs.proteinMatches)
   
    rs.copy(
      peptides = validatedPeptides,
      peptideMatches = validatedPepMatches,
      proteinMatches = validatedProtAndSeqMatches
    )
  }
  
  def getValidatedMatches(
    proteinSets: Array[ProteinSet],
    peptideMatches: Array[PeptideMatch],
    proteinMatches: Array[ProteinMatch]
  ): Tuple3[Array[Peptide],Array[PeptideMatch],Array[ProteinMatch]] = {

    val entitySelector = new ValidatedEntitySelector(proteinSets)
    val validatedProtMatches = entitySelector.getValidatedProteinMatches(proteinMatches).toArray
    val validatedPepMatches = entitySelector.getValidatedPeptideMatches(peptideMatches).toArray
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
    
    ( validatedPeptides, validatedPepMatches, validatedProtAndSeqMatches )
  }
  
  class ValidatedEntitySelector( proteinSets: Array[ProteinSet] ) {
    
    protected val( validPepIdSet, validPepMatchIdSet, validProtMatchIdSet ) = {
    
      // Build the set of unique valid entities ids
      val validPepIdSetBuilder = Set.newBuilder[Long]
      val validPepMatchIdSetBuilder = Set.newBuilder[Long]
      val validProtMatchIdSetBuilder = Set.newBuilder[Long]
      
      // Retrieve the ID of valid peptide matches (having a corresponding peptide instance)
      for (proteinSet <- proteinSets) {
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
  
    def getValidatedPeptideMatches(peptideMatches: Array[PeptideMatch]): Iterable[PeptideMatch] = {
      peptideMatches.filter { pm => validPepMatchIdSet.contains(pm.id) }
    }
  
    def getValidatedProteinMatches(proteinMatches: Array[ProteinMatch]): Iterable[ProteinMatch] = {
      proteinMatches.filter { pm => validProtMatchIdSet.contains(pm.id) }
    }
  
    def getValidatedSequenceMatches(proteinMatch: ProteinMatch): Iterable[SequenceMatch] = {
      proteinMatch.sequenceMatches.filter {sm => validPepIdSet.contains(sm.getPeptideId) }
    }
  }
  
}

object ResultSetType extends EnhancedEnum {
  val SEARCH, DECOY_SEARCH, QUANTITATION, USER = Value
}

case class ResultSetDescriptor(
  val id: Long,
  var name: String,
  var description: String,
  val contentType: ResultSetType.Value,
  val decoyResultSetId: Long = 0,
  val msiSearchId: Long = 0,
  val mergedResultSummaryId: Long = 0,
  var properties: Option[ResultSetProperties] = None
) {
  import ResultSetType._
  
  lazy val isSearchResult = (contentType == SEARCH || contentType == DECOY_SEARCH)
  lazy val isDecoy = (contentType == DECOY_SEARCH)
  lazy val isQuantified = (contentType == QUANTITATION)
}

class LazyResultSet(
  val descriptor: ResultSetDescriptor,
  val isValidatedContent: Boolean, // true if only validated entities are loaded, false otherwise
  //var lazyDecoyResultSet: Option[LazyResultSet] = None,
  protected val loadPeptideMatches: (ResultSetDescriptor) => Array[PeptideMatch],
  protected val loadProteinMatches: (ResultSetDescriptor) => Array[ProteinMatch],
  protected val loadMsiSearch: Option[(ResultSetDescriptor) => MSISearch],
  protected val loadLazyDecoyResultSet: Option[(ResultSetDescriptor) => LazyResultSet] = None,
  protected val loadChildMsiSearches: Option[(ResultSetDescriptor) => Array[MSISearch]] = None
) {
  
  // Shortcut to RS ID
  def id = descriptor.id
  
  // Required fields
  lazy val uniquePeptideSequences = peptides.map(_.sequence).distinct
  lazy val peptides: Array[Peptide] = peptideMatches.map(_.peptide).distinct
  lazy val peptideById: LongMap[Peptide] = peptides.mapByLong(_.id)
  lazy val peptideMatches: Array[PeptideMatch] = loadPeptideMatches(descriptor)
  lazy val peptideMatchById: LongMap[PeptideMatch] = peptideMatches.mapByLong(_.id)
  lazy val peptideMatchesByPeptideId: LongMap[Array[PeptideMatch]] = peptideMatches.groupByLong(_.peptide.id)
  lazy val proteinMatches: Array[ProteinMatch] = loadProteinMatches(descriptor)
  lazy val proteinMatchById: LongMap[ProteinMatch] = proteinMatches.mapByLong(_.id)
  lazy val msiSearch: Option[MSISearch] = loadMsiSearch.map( _(descriptor) )
  lazy val lazyDecoyResultSet: Option[LazyResultSet] = loadLazyDecoyResultSet.map( _(descriptor) )
  lazy val childMsiSearches: Array[MSISearch] = loadChildMsiSearches.map( _(descriptor) ).getOrElse( Array() )
  
  /*def getProteins(): Option[Array[Protein]] = {

    val proteins = new ArrayBuffer[Protein](0)
    for (protMatch <- proteinMatches)
      if (protMatch.protein.isDefined) proteins += protMatch.protein.get

    if (proteins.length == 0) None
    else Some(proteins.toArray)
  }*/
  
  lazy val peptideMatchesByProteinMatchId: LongMap[Array[PeptideMatch]] = {
    ResultSet.getPeptideMatchesByProteinMatchId(peptideMatchesByPeptideId, proteinMatches)
  }

}

case class ResultSummaryDescriptor(
  val id: Long,
  var description: String,
  val modificationTimestamp: java.util.Date = new java.util.Date,
  val isQuantified: Boolean,
  val decoyResultSummaryId: Long,
  val resultSetId: Long,
  var properties: Option[ResultSummaryProperties] = None
)

class LazyResultSummary(
  val descriptor: ResultSummaryDescriptor,
  val lazyResultSet: LazyResultSet,
  val linkResultSetEntities: Boolean = false, // fill links between RSM and RS entities
  val linkPeptideSets: Boolean = false, // fill links between samesets and subsets
  protected val loadPeptideInstances: (ResultSummaryDescriptor) => Array[PeptideInstance],
  protected val loadPeptideSets: (ResultSummaryDescriptor) => Array[PeptideSet],
  protected val loadProteinSets: (ResultSummaryDescriptor) => Array[ProteinSet],
  protected val loadLazyDecoyResultSummary: Option[(ResultSummaryDescriptor) => LazyResultSummary] = None,
  protected val loadPeptideValidationRocCurve: Option[(ResultSummaryDescriptor) => MsiRocCurve] = None,
  protected val loadProteinValidationRocCurve: Option[(ResultSummaryDescriptor) => MsiRocCurve] = None
) extends LazyLogging {
  
  // Shortcut to RSM ID
  def id = descriptor.id
  
  lazy val peptideInstances: Array[PeptideInstance] = {
    val pepInstances = loadPeptideInstances(descriptor)
    
    if(linkResultSetEntities) {
      this.linkPeptideMatchesToPeptideInstances(lazyResultSet.peptideMatches, pepInstances)
    }
    
    pepInstances
  }
  
  lazy val peptideInstanceById: LongMap[PeptideInstance] = peptideInstances.mapByLong(_.id)
  
  lazy val peptideSets: Array[PeptideSet] = {
    this.peptideInstances // lazy loading of peptide instances to enable linking with peptide matches
    val pepSets = loadPeptideSets(descriptor)
    
    if(linkPeptideSets) {
      this.linkPeptideSets(pepSets)
    }
    
    /*if(linkResultSetEntities) {
      this.linkProteinMatchesToPeptideSets(lazyResultSet.proteinMatches, pepSets)
    }*/
    
    pepSets
  }
  
  lazy val peptideSetById: LongMap[PeptideSet] = peptideSets.mapByLong(_.id)
  
  lazy val proteinSets: Array[ProteinSet] = {
    this.peptideSets // lazy loading of peptide sets to enable linking between peptide sets
    val protSets = loadProteinSets(descriptor)
    
    if(linkResultSetEntities) {
      this.linkProteinMatchesToProteinSets(lazyResultSet.proteinMatches, protSets)
    }
    
    protSets
  }
  
  lazy val proteinSetById: LongMap[ProteinSet] = proteinSets.mapByLong(_.id)
  
  lazy val bestValidatedPepMatchesByPepSetId: Map[Long, Array[PeptideMatch]] = {
    ResultSummary.getBestValidatedPepMatchesByPepSetId(
      this.lazyResultSet.peptideMatches,
      this.lazyResultSet.proteinMatchById,
      this.peptideSets
    )
  }

  lazy val bestPepMatchesByProtSetId: Map[Long, Array[PeptideMatch]] = {
    val rs = this.lazyResultSet
    ResultSummary.getBestPepMatchesByProtSetId(rs.peptideMatchById, rs.proteinMatchById, proteinSets)
  }

  lazy val peptideMatchesByPeptideSetId: Map[Long, Array[PeptideMatch]] = {
    ResultSummary.getPeptideMatchesByPeptideSetId(this.lazyResultSet.peptideMatchById, peptideSets)
  }
  
  lazy val peptideValidationRocCurve: Option[MsiRocCurve] = loadPeptideValidationRocCurve.map( _(descriptor) )
  lazy val proteinValidationRocCurve: Option[MsiRocCurve] = loadProteinValidationRocCurve.map( _(descriptor) )

  protected def linkPeptideSets(pepSets: Array[PeptideSet]): Unit = {

    val pepSetById = pepSets.mapByLong(_.id)

    for (pepSet <- pepSets) {
      if (pepSet.strictSubsetIds != null && pepSet.strictSubsets == null) {
        pepSet.strictSubsets = Some(pepSet.strictSubsetIds.map(pepSetById(_)))
      }
      if (pepSet.subsumableSubsetIds != null && pepSet.subsumableSubsets == null) {
        pepSet.subsumableSubsets = Some(pepSet.subsumableSubsetIds.map(pepSetById(_)))
      }
    }

    ()
  }
  
  protected def linkPeptideMatchesToPeptideInstances(pepMatches: Array[PeptideMatch], pepInstances: Array[PeptideInstance]) = {
    val pepMatchById = pepMatches.mapByLong(_.id)
    
    pepInstances.foreach { pepInst =>
      pepInst.peptideMatches = pepInst.getPeptideMatchIds.map( pepMatchById(_) )
    }
  }

  protected def linkProteinMatchesToProteinSets(protMatches: Array[ProteinMatch], protSets: Array[ProteinSet]) = {
    val protMatchById = protMatches.mapByLong(_.id)
    
    protSets.foreach { protSet =>
      
      val samesetProtMatches = protSet.samesetProteinMatchIds.map(protMatchById(_))
      protSet.samesetProteinMatches = Some(samesetProtMatches)
      protSet.subsetProteinMatches = Some(protSet.subsetProteinMatchIds.map(protMatchById(_)))
      
      val samesetProtMatchById = samesetProtMatches.mapByLong( _.id )
      
      val reprProtMatchId = protSet.getRepresentativeProteinMatchId
      val reprProtMatchOpt = samesetProtMatchById.get(reprProtMatchId)
      if(reprProtMatchOpt.isDefined) {
        protSet.setRepresentativeProteinMatch(reprProtMatchOpt.get)
      } else {
        logger.warn(s"Representative ProteinMatch (id=$reprProtMatchId) should belong to this ProteinSet sameset !")
      }
    }
  }
  
  /*protected def linkProteinMatchesToPeptideSets(protMatches: Array[ProteinMatch], pepSets: Array[PeptideSet]) = {
    val protMatchById = protMatches.mapByLong(_.id)
    
    pepSets.foreach { pepSet =>
    }
  }*/
  
}