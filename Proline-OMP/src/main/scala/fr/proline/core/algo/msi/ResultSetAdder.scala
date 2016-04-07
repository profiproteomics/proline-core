package fr.proline.core.algo.msi

import scala.collection.mutable.{ ArrayBuffer, HashMap, HashSet }

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.StringUtils.isEmpty
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.om.model.msi._

object AdditionMode extends Enumeration {
  val AGGREGATION = Value("aggregation")
  val UNION = Value("union")
}

class ResultSetAdder(
  val resultSetId: Long,
  val isValidatedContent: Boolean = false,
  val isDecoy: Boolean = false,
  val additionMode: AdditionMode.Value = AdditionMode.AGGREGATION,
  val clonePeptideMatches: Boolean = true
) extends LazyLogging {

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
        val pepMatchAdder = if( additionMode == AdditionMode.AGGREGATION ) {
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
      
      // Use accession number instead of protMatchKey composed by the protein id and taxon id (2 proteinMatches can reference same Sequence!)
      val protMatchKey = proteinMatch.accession
    
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
    
    // Build protein matches
    val mergedProteinMatches = protMatchAdderByKey.values.map { proteinMatchAdder =>
      proteinMatchAdder.toProteinMatch(pepMatchesByPepId)
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
      isValidatedContent = isValidatedContent,
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
    var psmSC = 0
    peptideMatchChildren.foreach(psmChild => {
      if(psmChild.properties.isDefined && psmChild.properties.get.spectralCount.isDefined)
        psmSC = psmSC + psmChild.properties.get.spectralCount.get 
      else
        psmSC = psmSC + 1 //If not specified, suppose leaf child so PSM Count = 1
    })    
    
    // If cloning is disabled, update the bestChild object
    val aggregatedPepMatch = if( cloneObjects == false ) {
      bestChild.bestChildId = bestChild.id
      bestChild.id = PeptideMatch.generateNewId()
      bestChild.children = Some(peptideMatchChildren.toArray)
      bestChild.resultSetId = newResultSetId
      if(!bestChild.properties.isDefined)
        bestChild.properties = Some(new PeptideMatchProperties())
      bestChild.properties.get.spectralCount = Some(psmSC)
      
      bestChild
    // Else copy the bestChild object with some new values
    } else {
      val newPSM = bestChild.copy(
        id = PeptideMatch.generateNewId(),
        childrenIds = peptideMatchChildren.map(_.id).distinct.toArray,
        bestChildId = bestChild.id,
        resultSetId = newResultSetId
      )
       if(!newPSM.properties.isDefined)
          newPSM.properties = Some(new PeptideMatchProperties())
      newPSM.properties.get.spectralCount = Some(psmSC)
      newPSM
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

    var psmSC = 0

    if (peptideMatch.properties.isDefined && peptideMatch.properties.get.spectralCount.isDefined)
      psmSC = psmSC + peptideMatch.properties.get.spectralCount.get
    else
      psmSC = psmSC + 1 //If not specified, suppose leaf child so PSM Count = 1

    
    // If cloning is disabled, update the peptideMatch object // CBY : WARNING : children relationship will be wrong !!!!!
    val newPeptideMatch = if( cloneObjects == false ) {
      peptideMatch.bestChildId = peptideMatch.id
      peptideMatch.id = PeptideMatch.generateNewId()
      peptideMatch.resultSetId = newResultSetId
      peptideMatch.children = Some(Array(peptideMatch))
      peptideMatch
    }
    // Else copy the peptideMatch object with some new values
    else {
      peptideMatch.copy(
        id = PeptideMatch.generateNewId(),
        resultSetId = newResultSetId,
        childrenIds = Array(peptideMatch.id),
        bestChildId = peptideMatch.id
      )
    }
    
     if(!newPeptideMatch.properties.isDefined)
          newPeptideMatch.properties = Some(new PeptideMatchProperties())
      newPeptideMatch.properties.get.spectralCount = Some(psmSC)
      
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
  
  def toProteinMatch( pepMatchesByPepId: Map[Long, Array[PeptideMatch]]): ProteinMatch = {
    require( firstAddedProteinMatch != null, "at least one protein match must be added")
    
    // Retrieve all parent SequenceMatch and sort them by protein sequence location
    val parentSeqMatches = childSeqMatchByUniqueKey.values.toArray.sortBy(_.start)

    // Retrieve protein id for coverage computation
    val proteinId = firstAddedProteinMatch.getProteinId
    
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
      coverage = 0.0f, // protein match sequence coverage cannot be computed and depends on validated psm
      peptideMatchesCount = peptideMatchesCount,
      sequenceMatches = parentSeqMatches,
      seqDatabaseIds = seqDatabaseIdSet.toArray,
      resultSetId = newResultSetId
    )
  }
  
}

