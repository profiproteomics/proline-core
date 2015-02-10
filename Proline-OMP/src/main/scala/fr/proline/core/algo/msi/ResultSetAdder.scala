package fr.proline.core.algo.msi

import scala.collection.mutable.{ ArrayBuffer, HashMap, HashSet }

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.util.StringUtils.isEmpty
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.om.model.msi._

object AdditionMode extends Enumeration {
  val AGGREGATE, UNION = Value
}

class ResultSetAdder(
  val resultSetId: Long,
  val isDecoy: Boolean = false,
  seqLengthByProtId: Option[Map[Long, Int]] = None,
  val additionMode: AdditionMode.Value = AdditionMode.AGGREGATE,
  val clonePeptideMatches: Boolean = true
) extends Logging {

  private val protMatchAdderByKey = new HashMap[String, ProteinMatchAdder]()
  private val pepMatchAdderByPeptideId = new HashMap[Long, IPeptideMatchAdder]()
  val peptideById = new HashMap[Long, Peptide]()

  private val distinctTdModes = new HashSet[String]()
  private val msiSearches = new ArrayBuffer[MSISearch]()
  
  def addResultSets(resultSets: Iterable[ResultSet]): ResultSetAdder = {
    for( resultSet <- resultSets ) this.addResultSet(resultSet)
    this
  }

  // Note: if you want to work with a filtered ResultSet, perform the filtering before this step
  def addResultSet(rs: ResultSet): ResultSetAdder = {

    logger.info("Start adding ResultSet #" + rs.id)
    val start = System.currentTimeMillis()
    
    if( rs.msiSearch.isDefined ) msiSearches += rs.msiSearch.get
    else msiSearches ++= rs.childMsiSearches
    
    distinctTdModes += (if (rs.properties.isDefined) { rs.properties.get.targetDecoyMode.getOrElse("") } else "")
    
    for (peptideMatch <- rs.peptideMatches) {
      
      val peptideId = peptideMatch.peptide.id
      
      // Check if this peptide is already mapped by its id in this class
      val peptide = peptideMatch.peptide
      if( peptideById.contains(peptide.id) == false ) {
        peptideById += (peptide.id -> peptide)
      }
      
      val pepMatchAdderOpt = pepMatchAdderByPeptideId.get(peptideId)
      
      // Check if a peptide match adder has been already created for this peptide
      if ( pepMatchAdderOpt.isEmpty ) {
        
        // Create new PeptideMatch adder
        val pepMatchAdder = if( additionMode == AdditionMode.AGGREGATE ) {
          new PeptideMatchAggregator(resultSetId, clonePeptideMatches)
        } else {
          new PeptideMatchAccumulator(resultSetId, clonePeptideMatches)
        }
        
        // Initialize the adder with this peptideMatch
        pepMatchAdder.addPeptideMatch(peptideMatch)
        
        pepMatchAdderByPeptideId += ( peptideId -> pepMatchAdder )
        
      } else {
        // Give peptideMatch to the adder
        pepMatchAdderOpt.get.addPeptideMatch(peptideMatch)
      }
    }
    
    // Iterate over protein matches to merge them by a unique key
    for (proteinMatch <- rs.proteinMatches ) {
      
      // If no protein attached the protMatchKey is the accession number
      val protMatchKey = if (proteinMatch.getProteinId == 0) proteinMatch.accession
      // Else protMatchKey is composed by the protein id and taxon id
      else proteinMatch.getProteinId + "%" + proteinMatch.taxonId
    
      val protMatchAdderOpt = protMatchAdderByKey.get(protMatchKey)
      
      if (protMatchAdderOpt.isEmpty) {
        // Create new ProteinMatchAdder and initialize it with this proteinMatch
        val proteinMatchAdder = new ProteinMatchAdder(resultSetId)
        proteinMatchAdder.addProteinMatch(proteinMatch)
        protMatchAdderByKey += (protMatchKey -> proteinMatchAdder)
      } else {
        // Give proteinMatch to the adder
        protMatchAdderOpt.get.addProteinMatch(proteinMatch)
      }

    }

    logger.info("ResultSet #" + rs.id + " added in " + (System.currentTimeMillis() - start) + " ms")
    
    this
  }
  
  /*protected def createParentPeptideMatch(
    id: Option[Long] = None,
    peptideMatch: PeptideMatch,
    peptide: Peptide
  ): PeptideMatch = {
    
    val newPepMatchId = id.getOrElse(PeptideMatch.generateNewId())
    peptideMatch.copy(
      id = newPepMatchId,
      children = Some(Array(peptideMatch)),
      resultSetId = resultSetId,
      peptide = peptide,
      bestChildId = peptideMatch.id
    )
  }*/

  def toResultSet(): ResultSet = {
    val start = System.currentTimeMillis()
    
    // Retrieve target/decoy mode
    val mergedTdModeOpt = if (distinctTdModes.size > 1) Some(TargetDecoyModes.MIXED.toString)
    else {
      val tdModeStr = distinctTdModes.head
      if (tdModeStr == "") None else Some(tdModeStr)
    }

    // Set merged RS properties
    val mergedProperties = new ResultSetProperties()
    mergedProperties.setTargetDecoyMode(mergedTdModeOpt)
    
    // Build peptide matches
    val mergedPeptideMatchesBuffer = new ArrayBuffer[PeptideMatch](pepMatchAdderByPeptideId.size)
    pepMatchAdderByPeptideId.values.map { pepMatchAdder =>
      pepMatchAdder match {
        case pepMatchAggregator: PeptideMatchAggregator => {
          mergedPeptideMatchesBuffer += pepMatchAggregator.toPeptideMatch()
        }
        case pepMatchAccumulator: PeptideMatchAccumulator => {
          mergedPeptideMatchesBuffer ++= pepMatchAccumulator.toPeptideMatches()
        }
      }
    } toArray
    
    val mergedPeptideMatches = mergedPeptideMatchesBuffer.toArray
    
    // Group peptide matches by peptide id
    val pepMatchesByPepId = mergedPeptideMatches.groupBy(_.peptide.id)
    
    println( peptideById.get(2304) )
    println( peptideById.get(2305) )
    println( pepMatchesByPepId.get(2305) )
    
    // Build protein matches
    val mergedProteinMatches = protMatchAdderByKey.values.map { proteinMatchAdder =>
      proteinMatchAdder.toProteinMatch(pepMatchesByPepId, seqLengthByProtId )
    }

    // Create merged result set
    val mergedResultSet = new ResultSet(
      id = resultSetId,
      childMsiSearches = msiSearches.toArray,
      proteinMatches = mergedProteinMatches.toArray.sortBy( - _.score ), // sort by descending score
      peptideMatches = mergedPeptideMatches,
      peptides = peptideById.values.toArray,
      isDecoy = isDecoy,
      isSearchResult = false,
      properties = Some(mergedProperties)
    )

    this.logger.info("Result Sets have been merged:")
    this.logger.info("- nb merged protein matches = " + mergedResultSet.proteinMatches.length)
    this.logger.info("- nb merged peptide matches = " + mergedResultSet.peptideMatches.length)
    this.logger.info("- nb merged peptides = " + mergedResultSet.peptides.length)

    logger.info("Merged ResultSet #" + resultSetId + " created in " + (System.currentTimeMillis() - start) + " ms")
    mergedResultSet
  }

}

private[this] trait IPeptideMatchAdder {
  
  val newResultSetId: Long
  val cloneObjects: Boolean
  
  def addPeptideMatch( peptideMatch: PeptideMatch ): Unit  

}

// Perform the addition in AGGREGATION mode
private[this] class PeptideMatchAggregator(
  val newResultSetId: Long,
  val cloneObjects: Boolean = true // clone objects by default
) extends IPeptideMatchAdder {
  
  private val peptideMatchChildren = new ArrayBuffer[PeptideMatch]()
  
  def addPeptideMatch( peptideMatch: PeptideMatch ): Unit = {
    peptideMatchChildren += peptideMatch
  }
  
  def toPeptideMatch(): PeptideMatch = {
    
    // Determine the best child using the score value
    val bestChild = peptideMatchChildren.maxBy(_.score)
    
    // If cloning is disabled, update the bestChild object
    val aggregatedPepMatch = if( cloneObjects == false ) {
      bestChild.id = PeptideMatch.generateNewId()
      bestChild.children = Some(peptideMatchChildren.toArray)
      bestChild.resultSetId = newResultSetId
      bestChild
    // Else copy the bestChild object with some new values
    } else {
      bestChild.copy(
        id = PeptideMatch.generateNewId(),
        childrenIds = peptideMatchChildren.map(_.id).distinct.toArray,
        bestChildId = bestChild.id,
        resultSetId = newResultSetId
      )
    }
    
    aggregatedPepMatch
  }
  
}

// Perform the addition in UNION mode
private[this] class PeptideMatchAccumulator(
  val newResultSetId: Long,
  val cloneObjects: Boolean = false  // do not clone objects by default
) extends IPeptideMatchAdder {
  
  private val peptideMatches = new ArrayBuffer[PeptideMatch]()
  
  def addPeptideMatch( peptideMatch: PeptideMatch ): Unit = {
    
    // If cloning is disabled, update the peptideMatch object
    val newPeptideMatch = if( cloneObjects == false ) {
      peptideMatch.id = PeptideMatch.generateNewId()
      peptideMatch.resultSetId = newResultSetId
      peptideMatch
    }
    // Else copy the peptideMatch object with some new values
    else {
      peptideMatch.copy(
        id = PeptideMatch.generateNewId(),
        resultSetId = newResultSetId,
        childrenIds = null,
        children = null,
        bestChildId = 0L
      )
    }
    
    peptideMatches += newPeptideMatch    
  }
  
  def toPeptideMatches(): Array[PeptideMatch] = {
    peptideMatches.toArray
  }
}

private[this] case class SeqMatchUniqueKey(
  peptideId: Long,
  start: Int,
  end: Int
)

private[this] class ProteinMatchAdder( newResultSetId: Long ) {
  
  private var firstAddedProteinMatch: ProteinMatch = null
  private var proteinMatchDescription: String = null
  private var proteinMatchScore: Float = 0f
  
  // Map all sequence matches of this protein match group by their corresponding peptide id
  private val childSeqMatchByUniqueKey = new HashMap[SeqMatchUniqueKey, SequenceMatch]()
  private val seqDatabaseIdSet = new HashSet[Long]()
  
  def addProteinMatch( proteinMatch: ProteinMatch ): Unit = {
    
    if( firstAddedProteinMatch == null ) {
      firstAddedProteinMatch = proteinMatch
      proteinMatchDescription = proteinMatch.description
    }
    else {
      // Check all protein matches to merge have the same isDecoy value
      require(
        proteinMatch.isDecoy == firstAddedProteinMatch.isDecoy,
        "inconsistent isDecoy parameter between protein matches to merge"
      )
      
      // Change description if previous one was empty
      if (isEmpty(proteinMatchDescription) && !isEmpty(proteinMatch.description))
        proteinMatchDescription = proteinMatch.description
        
      // Update score if greater
      if( proteinMatch.score > proteinMatchScore )
        proteinMatchScore = proteinMatch.score
    }
    
    // Update seqDatabaseIdSet
    if (proteinMatch.seqDatabaseIds != null) seqDatabaseIdSet ++= proteinMatch.seqDatabaseIds
    
    // Merge new child sequence matches with parent ones
    for (seqMatch <- proteinMatch.sequenceMatches) {
      val uniqueKey = SeqMatchUniqueKey(seqMatch.getPeptideId, seqMatch.start, seqMatch.end)
      
      if (!childSeqMatchByUniqueKey.contains(uniqueKey)) {
        // Create new sequenceMatch for the parent proteinMatch to be built
        // Note that bestPetideMatchId will be updated in the toProteinMatch method (at the end of the build process)
        childSeqMatchByUniqueKey(uniqueKey) = seqMatch.copy(resultSetId = newResultSetId)
      }
    }
  }
  
  def toProteinMatch( pepMatchesByPepId: Map[Long, Array[PeptideMatch]], seqLengthByProtId: Option[Map[Long, Int]] ): ProteinMatch = {
    require( firstAddedProteinMatch != null, "at least one protein match must be added")
    
    // Retrieve all parent SequenceMatch and sort them by protein sequence location
    val parentSeqMatches = childSeqMatchByUniqueKey.values.toArray.sortBy(_.start)

    // Retrieve protein id for coverage computation
    val proteinId = firstAddedProteinMatch.getProteinId
    
    // Compute protein match sequence coverage  
    var coverage = 0f
    if (proteinId != 0 && seqLengthByProtId.isDefined) {
      val seqLength = seqLengthByProtId.get.get(proteinId)
      require(seqLength.isDefined,"can't find a sequence length for the protein with id='" + proteinId + "'")

      val seqPositions = parentSeqMatches.map { s => (s.start, s.end) }
      coverage = Protein.calcSequenceCoverage(seqLength.get, seqPositions)
    }
    
    // Update total peptideMatchesCount and bestPeptideMatch for each sequenceMatch
    var peptideMatchesCount = 0
    for (seqMatch <- parentSeqMatches) {
      val peptideMatches = pepMatchesByPepId(seqMatch.getPeptideId)
      peptideMatchesCount += peptideMatches.length
      seqMatch.bestPeptideMatchId = pepMatchesByPepId(seqMatch.getPeptideId).maxBy(_.score).id
    }
    
    // Clone firstAddedProteinMatch while setting some values computed using the merged protein matches
    firstAddedProteinMatch.copy(
      id = ProteinMatch.generateNewId,
      score = proteinMatchScore,
      coverage = coverage,
      peptideMatchesCount = peptideMatchesCount,
      sequenceMatches = parentSeqMatches,
      seqDatabaseIds = seqDatabaseIdSet.toArray,
      resultSetId = newResultSetId
    )
  }
  
}

