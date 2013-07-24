package fr.proline.core.util.generator.msi

import java.text.SimpleDateFormat
import java.util.Date

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.om.model.msi._
import fr.proline.util.primitives._
import fr.proline.util.random._
import fr.proline.util.ms.massToMoz

/**
 * Utility class to generate a faked ResultSet.
 * 
 * Non-redundant trypsic peptides generated with an amino acid sequence length in a given range.
 * Each protein sequence is the exact concatenation of corresponding peptide sequences.
 * No management of subsets/samesets of peptides.
 *
 * Required
 * @param nbPeps Number of non redundant peptides to create
 * @param nbProts Number of non redundant proteins to create
 *
 * Optional
 * @param minPepSeqLength Minimum length for peptide sequence
 * @param maxPepSeqLength Maximum length for peptide sequence
 *
 */
class ResultSetFakeGenerator(
  nbPeps: Int, 
  nbProts: Int, 
  minPepSeqLength: Int = 8, 
  maxPepSeqLength: Int = 20
) extends AnyRef with Logging {

  require(nbPeps > 0, "Peptides # must be > 0")
  require(nbProts > 0 && nbProts <= nbPeps, "Protein # must be > 0 and <= peptides #")
  
  // Public fields
  val allPeps = ArrayBuffer[Peptide]()
  val allPepMatches = ArrayBuffer[PeptideMatch]()
  val allProtMatches = ArrayBuffer[ProteinMatch]()
  val proteinById = collection.mutable.Map[Long,Protein]()
  def allProts = proteinById.values // for compat with old API (before introduction of proteinById Map)

  // Constants
  private final val MAX_MISSED_CLEAVAGES = 4
  private final val MIN_MISSED_CLEAVAGES = 1
  private final val RESULT_SET_ID = ResultSet.generateNewId

  // Define some vars related to PeptideMatch entity
  private var tmpPepMatchById = collection.mutable.Map[Long, PeptideMatch]()
  private var pepIdByPepMatchId = collection.mutable.Map[Long, Long]()
  private var pepMatchIdsByPepId = collection.mutable.Map[Long, ArrayBuffer[Long]]()

  // Define some vars related to ProteinMatch entity  
  private var tmpProtMatchById = collection.mutable.Map[Long, ProteinMatch]()
  private var tmpProtMatchIdsByPepId = collection.mutable.Map[Long, ArrayBuffer[Long]]()

  // Define some vars related to Peptide entity
  private val allPepSeqs = HashSet[String]() //to check sequence unicity
  private val usedMissedCleavages = HashSet[Int]() //to missed cleavages unicity
  
  // Create a map of Peptides for each Protein sequence
  val allPepsByProtSeq = collection.mutable.Map[String, ArrayBuffer[Peptide]]()
  
  _buildResultSet()
  
  /**
   * Builds the result set.
   * This private method is only called once time by the constructor.
   * 
   */
  private def _buildResultSet() {

    // Estimate an average number of peptides for each protein
    val avgNbPepsPerGroup: Int = nbPeps / nbProts
    
    logger.debug("Start building a ResultSet having " + nbPeps + " peptides and " + nbProts + " proteins.")
    logger.debug(avgNbPepsPerGroup + " peptides match on each protein.")
    
    // Define some vars needed for the result set construction
    var needNewPeps = true
    val currPepArray = ArrayBuffer[Peptide]() // Array of Peptides matching on a given protein
    val currProtSeqBuilder = new StringBuilder
    
    // Build Peptide, PeptideMatch, Ms2Query
    while( needNewPeps ) {
      
      // Generate new peptide sequence
      val currPepSeq = ResultSetRandomator.randomAASequence(minPepSeqLength, maxPepSeqLength)
      
      // Check this is a new peptide sequence
      if ( (allPepSeqs contains currPepSeq ) == false ) {
        
        // Add peptide sequence to the HashSet
        allPepSeqs += currPepSeq
        
        // Create new peptide match and its corresponding peptide
        val newPepMatch = _createPeptideAndPeptideMatch(pepSequence = currPepSeq, missedCleavage = 0, RSId = RESULT_SET_ID)
        
        // Collect Peptides corresponding to the current protein
        currPepArray += newPepMatch.peptide
        
        // Check if we still need to create new peptides
        needNewPeps = allPeps.size < nbPeps
        
        // This part is for protein sequence creation (just an exact concatenation of N peptide sequences)
        // Define # of peptides matching on the current protein
        currProtSeqBuilder.append( currPepSeq )
        
        // Check if we have reached the needed number of peptides per protein
        // or if we reached the total number of expected peptides
        if (currPepArray.size == avgNbPepsPerGroup || needNewPeps == false) {
          
          // Map the collected peptides by the corresponding protein sequence
          allPepsByProtSeq += currProtSeqBuilder.result -> (new ArrayBuffer(currPepArray.length) ++ currPepArray)
          
          // Clear buffers/builders
          currPepArray.clear()
          currProtSeqBuilder.clear()
        }
  
      } // end of if
      
    } // end of while
    
    _updatePeptideMaps()
  
    // Build Protein ProteinMatch and SequenceMatch entities  
    for ( (currProtSequence, pepArray) <- allPepsByProtSeq ) {
      createNewProteinMatchFromPeptides(pepArray)
    }
    
  }
  
  /**
   * Creates one new Peptide (+Ms2Query, +PeptideMatch) from the peptideArray
   * and associate new SequenceMatch with an EXISTING ProteinMatch.
   *
   * Update allPepMatches & allPepsByProtSeq collections
   * 
   * @param pepIds the peptide id array
   * @param protMatchId the protein match id
   * @param missedCleavage the number missed cleavages
   * @param RSId the result set id
   * @return the extended ProteinMatch
   */
  private def _addCompositePeptideToProteinMatch(
    pepIds: Array[Long],
    protMatchId: Long,
    missedCleavage: Int,
    RSId: Long
  ): ProteinMatch = {
    
    // Try to retrieve the protein match
    val protMatchOpt = tmpProtMatchById.get(protMatchId)
    require( protMatchOpt.isDefined, "unknown protein match with id="+protMatchId )
    
    val protMatch = protMatchOpt.get
    val protMatchPepById: Map[Long,Peptide] = Map() ++ protMatch.sequenceMatches.map { sm => 
      sm.peptide.get.id -> sm.peptide.get
    }
    val protSeq = protMatch.protein.get.sequence
    
    val pepSeqBuilder = pepIds.foldLeft(new StringBuilder)( (sb,id) => sb.append(protMatchPepById(id).sequence) )
    val builtPepMatch = _createPeptideAndPeptideMatch(
      pepSequence = pepSeqBuilder.result,
      missedCleavage = missedCleavage,
      RSId = RSId
    )
    //    logger.info("MERGED Sequence = "+builtSequence+" from Pep IDs: "+pepIdArray)    

    // Add the built peptide to the allPepsByProtSeq Map
    allPepsByProtSeq(protSeq) += builtPepMatch.peptide
    
    // Add peptide id to the tmpProtMatchIdsByPepId Map
    tmpProtMatchIdsByPepId += builtPepMatch.peptide.id -> (new ArrayBuffer(1) + protMatch.id)

    // Create new SequenceMatch, add it to existing ProteinMatch sequence matches
    // Retrieve SequenceMatch corresponding to the pepIdArray    
    val seqMatchByPepId = Map() ++ protMatch.sequenceMatches.map( sm => sm.peptide.get.id -> sm )
    val( firstSM, lastSM ) = (seqMatchByPepId(pepIds.head),seqMatchByPepId(pepIds.last))
    
    val newSM = new SequenceMatch(
      start = firstSM.start,
      end = lastSM.end,
      residueBefore = firstSM.residueBefore,
      residueAfter = lastSM.residueAfter,
      peptide = Some(builtPepMatch.peptide),
      bestPeptideMatch = Some(builtPepMatch),
      resultSetId = RSId
    )
    protMatch.sequenceMatches = (protMatch.sequenceMatches.toBuffer + newSM).toArray
    
    protMatch.peptideMatchesCount += 1    
    
    protMatch
  }

  /**
   * Creates a new missed cleaved Peptide from a Peptide Array & PeptideMatch
   * Update allPepMatches collection
   * 
   * @param pepSequence the peptide sequence
   * @param missedCleavage the number of missed cleavages
   * @param RSId the result set id
   * @return the created PeptideMatch
   */
  private def _createPeptideAndPeptideMatch(pepSequence: String, missedCleavage: Int, RSId: Long): PeptideMatch = {

    val builtPep = new Peptide(
      id = Peptide.generateNewId,
      sequence = pepSequence,
      ptms = null,
      calculatedMass = Peptide.calcMass(pepSequence)
    )
    
    // Add peptide to the peptide array buffer
    allPeps += builtPep

    _createPeptideMatch(pep = builtPep, missedCleavage = missedCleavage, RSId = RSId)
  }

  /**
   * Creates a new peptide match.
   *
   * @param pep the corresponding peptide
   * @param missedCleavage the number missed cleavages
   * @param RSId the result set id
   * @return the created PeptideMatch
   */
  private def _createPeptideMatch(pep: Peptide, missedCleavage: Int, RSId: Long): PeptideMatch = {
    
    val charge: Int = ResultSetRandomator.randomPepCharge
    val queryID: Long = Ms2Query.generateNewId

    val msq = new Ms2Query(
      id = queryID,
      initialId = toInt(queryID),
      moz = massToMoz(mass = pep.calculatedMass, charge = charge),
      charge = charge,
      spectrumTitle = "generated spectrum " + queryID
    )
    
    val builtPM = new PeptideMatch(
      id = PeptideMatch.generateNewId,
      rank = 1,
      score = ResultSetRandomator.randomPepMatchScore,
      scoreType = "mascot:standard score",
      deltaMoz = 0.15f,
      isDecoy = false,
      peptide = pep,
      missedCleavage = missedCleavage,
      msQuery = msq,
      resultSetId = RSId
    )
    
    // Add peptide match to the peptide match array buffer
    allPepMatches += builtPM
    
    // Add peptide match to the peptide match map
    tmpPepMatchById +=  (builtPM.id -> builtPM)
    
    builtPM
  }

  /**
   * Used only for printing
   */
  private def _updatePeptideMaps(): Unit = {
    pepMatchIdsByPepId.clear
    
    pepIdByPepMatchId ++= allPepMatches.map { pepMatch =>
      val pepId = pepMatch.peptide.id      
      pepMatchIdsByPepId.getOrElseUpdate(pepId, new ArrayBuffer[Long]) += pepMatch.id      
      pepMatch.id -> pepMatch.peptide.id
    }

  }


  
  /**
   * Adds a given number of duplicated peptide matches
   * Randomly select a PeptideMatch
   *
   * @param nbDuplicatedPeps the number duplicated peptide matches
   * @return the ResultSetFakeBuilder
   */
  def addDuplicatedPeptideMatches(nbDuplicatedPeps: Int): ResultSetFakeGenerator = {

    val pepMatchCount: Int = allPepMatches.length + nbDuplicatedPeps

    while (allPepMatches.length < pepMatchCount) {
      
      val pepMatchIdx = randomInt(minInclu = 0, maxInclu = allPepMatches.length - 1)
      val randomPepMatch = allPepMatches(pepMatchIdx)
      
      // Retrieve the ProteinMatch associated to this PeptideMatch		      
      /*val protMatch = allProtMatches.filter( pm => {
        (!pm.sequenceMatches.filter(sm => sm.peptide.get.id == randomPepMatch.peptide.id).isEmpty)
      })(0) //Take first one*/
      val protMatchId = tmpProtMatchIdsByPepId(randomPepMatch.peptide.id)(0)
      val protMatch = tmpProtMatchById(protMatchId)
      
      // Create new peptide match by using the properties of one selected randomly
      val builtPepMatch = _createPeptideMatch(
        pep = randomPepMatch.peptide,
        missedCleavage = randomPepMatch.missedCleavage,
        RSId = randomPepMatch.resultSetId
      )
      
      protMatch.peptideMatchesCount += 1
      
      // Modify the SequenceMatch if best PeptideMatch ID has changed
      val seqMatch = protMatch.sequenceMatches.find( _.getPeptideId == randomPepMatch.peptide.id ).get
      if (builtPepMatch.score > seqMatch.bestPeptideMatch.get.score) {
        seqMatch.bestPeptideMatch = Some(builtPepMatch)
      }

    } //end of while

    _updatePeptideMaps()

    logger.info(nbDuplicatedPeps + " duplicated PeptideMatch added")
    
    ResultSetFakeGenerator.this
  }

  /**
   * Adds the new peptides with missed cleavage.
   * 
   * Note: it creates only NEW missed cleavages.
   * In other words, if peptides have been previously created with 2 missed cleavage, you cannot
   * recall this method with missedCleavageNb=2, but can create with missCleavageNb=1 or 3...
   *
   *
   * @param nbPeps the number of peptides
   * @param nbMissedCleavages the number of missed cleavages
   * @return the ResultSetFakeBuilder
   */
  def addNewPeptidesWithMissedCleavage(nbPeps: Int, nbMissedCleavages: Int): ResultSetFakeGenerator = {
    require(
      nbMissedCleavages <= MAX_MISSED_CLEAVAGES && nbMissedCleavages >= MIN_MISSED_CLEAVAGES,
      "Number of missed cleavage must be >=" + MIN_MISSED_CLEAVAGES + " and <=" + MAX_MISSED_CLEAVAGES
    )

    require(
      usedMissedCleavages(nbMissedCleavages) == false, 
      "Peptides with this missed cleavage have already been created"
    )
    
    // Update the set of used missed cleavages
    usedMissedCleavages += nbMissedCleavages

    // Max # of peptides that can be created with the given missed cleavage (per ProteinMatch ID)
    // Example with a ProteinMatch with 6 Peptides (FYI, 1peptide <-> 1SequenceMatch) 
    //     1        .      2        .      3        .       4       .    5           =>  nbMissedCleavages
    //|_|_|_|_|_|_| . |_|_|_|_|_|_| . |_|_|_|_|_|_| . |_|_|_|_|_|_| . |_|_|_|_|_|_|
    //|_|_|			    . |_|_|_|       . |_|_|_|_|     . |_|_|_|_|_|   . |_|_|_|_|_|_|
    //  |_|_|       .   |_|_|_|     .   |_|_|_|_|   .   |_|_|_|_|_|
    //    |_|_|     .     |_|_|_|   .     |_|_|_|_| .
    //      |_|_|   .       |_|_|_| . 
    //        |_|_| . 
    //     5        .      4        .      3        .       2       .    1           => max # of peptides with missed cleavages

    // Example with a ProteinMatch with 3 Peptides (FYI, 1peptide <-> 1SequenceMatch)
    //     1        .      2                                                         =>  nbMissedCleavages
    //|_|_|_|       . |_|_|_|       
    //|_|_|			    . |_|_|_|           
    //  |_|_|       .                
    //     2        .      1                                                         => max # of peptides with missed cleavages

    // Collect peptides having no missed cleavage
    // (It's from these peptides, the missed cleaved peptides will be created)    
    val noMCPeptideIds = Set() ++ allPeps.withFilter( p =>
      ResultSetRandomator.trypsicRegex.findAllIn( p.sequence ).length == 1
    ).map( _.id )

    // For each ProteinMatch, get peptides without missed cleavages
    // and keep only ProteinMatch having enough peptides to create new ones with missed cleavages
    var eligiblePepIdsByProtMatchId = Map() ++ allProtMatches.map { protMatchInst =>
      val peptideIds = protMatchInst.sequenceMatches.map(_.getPeptideId)
      protMatchInst.id -> peptideIds.filter( noMCPeptideIds.contains(_) ) // filterNot ???
    }

    // Suppress entries having size list < (missCleavageNb + 1) 
    // DBO: this is obscure for me
    // DBO: what about consecutivity of peptide sequences inside the protein sequence ???
    eligiblePepIdsByProtMatchId = eligiblePepIdsByProtMatchId.filter(e => e._2.size >= (nbMissedCleavages + 1))
    //    eligiblePeptidesForProteinMatchId map {e=> logger.info(e._1+" => "+e._2) }

    // Total max # of peptides that can be created with the given missed cleavage
    val maxSM: Int = eligiblePepIdsByProtMatchId.values.foldLeft(0)(_ + _.size - nbMissedCleavages)
    require(nbPeps <= maxSM, "Maximum number of peptides with " + nbMissedCleavages + " missed cleavages is: " + maxSM)

    logger.info("Adding " + nbPeps + " new peptides (to " + allPeps.size + " existing peptides) with " + nbMissedCleavages + " missed cleavage(s)")

    // Store keys in a buffer to be able to access a key from a random index
    val keyBuffer = eligiblePepIdsByProtMatchId.keys.toBuffer

    var currPepNb: Int = 0
    while (currPepNb < nbPeps) {

      // Randomly determine which ProteinMatch will have new Peptide with missed cleavage(s)       
      val idx = randomInt(0, eligiblePepIdsByProtMatchId.size - 1)
      val currRandomProMatchKey = keyBuffer(idx) //ProteinMatch key

      // Take the (missCleavageNb+1)first peptides to create the new missed cleaved peptide & Co
      val pepIdArray = eligiblePepIdsByProtMatchId(currRandomProMatchKey).take(nbMissedCleavages + 1)
      val builtProtMatch: ProteinMatch = _addCompositePeptideToProteinMatch(
        pepIds = pepIdArray,
        protMatchId = currRandomProMatchKey,
        missedCleavage = nbMissedCleavages,
        RSId = RESULT_SET_ID
      )

      //      logger.debug("Created 1 new peptide for ProteinMatch ID: "+builtProMatch.id)    
      //      builtProMatch.sequenceMatches.map(sm=>
      //        logger.debug("...PeptideID"+sm.peptide.get.id.toString+", SeqMatch ["+sm.start+"/"+sm.end+", PepID"+sm.getPeptideId+"]")        )

      //Remove first peptide from list        
      eligiblePepIdsByProtMatchId += (currRandomProMatchKey -> eligiblePepIdsByProtMatchId(currRandomProMatchKey).tail)
      //      eligiblePeptidesForProteinMatchId map {e=> logger.info(e._1+" => "+e._2) }

      currPepNb += 1
      if (eligiblePepIdsByProtMatchId(currRandomProMatchKey).size < (nbMissedCleavages + 1)) {
        //Remove entry
        eligiblePepIdsByProtMatchId -= currRandomProMatchKey
        keyBuffer.remove(idx)
      }

    } //end while  

    _updatePeptideMaps()

    ResultSetFakeGenerator.this
  }

  /**
   * Adds new shared peptide to provided protein matches.
   *
   * @param protMatches the protein matches
   * @return the ResultSetFakeBuilder
   */
  def addSharedPeptide(protMatches: Seq[ProteinMatch]): ResultSetFakeGenerator = {
    
    // Generate a peptide sequence until we have a new one
    var currPepSeq = ""
    do {
      currPepSeq = ResultSetRandomator.randomAASequence(minPepSeqLength, maxPepSeqLength)
    } while( allPepSeqs contains currPepSeq )
     
    // Update the HashSet of peptide sequences
    allPepSeqs += currPepSeq
    
    val currPepMatch = _createPeptideAndPeptideMatch(
      pepSequence = currPepSeq, missedCleavage = 0, RSId = RESULT_SET_ID
    )
    val currPep = currPepMatch.peptide
    
    // Update peptide mappings
    _updatePeptideMaps()

    for (protMatch <- protMatches) {
      
      val prot = protMatch.protein.get
      val oldProtSeq = protMatch.protein.get.sequence
      val protPeptides = allPepsByProtSeq(oldProtSeq)
      
      // Add created peptide to allPepsByProtSeq      
      protPeptides += currPep

      // Update protein sequence
      val newProtSeq = oldProtSeq.concat(currPepSeq)
      val newProt = prot.copy(sequence=newProtSeq)
      proteinById(prot.id) = newProt
      protMatch.protein = Some(newProt)
      
      // Remove old prot seq mapping
      allPepsByProtSeq -= oldProtSeq
      
      // Add new prot seq mapping
      allPepsByProtSeq += newProtSeq -> protPeptides
      
      // Update after residue of the previous last peptide
      val lastSM = protMatch.sequenceMatches.last.copy( residueAfter = currPep.sequence.charAt(0) )
      protMatch.sequenceMatches(protMatch.sequenceMatches.length - 1) = lastSM
      
      // Retrieve before/after residues for the new peptide sequence
      val startPos = oldProtSeq.length + 1
      val endPos = startPos + currPep.sequence.length - 1
      val resBefore = oldProtSeq.last
      val resAfter = '-'
      
      // Search for the best peptide match using the score value
      val bestPepMatch = pepMatchIdsByPepId(currPep.id).map(tmpPepMatchById(_)).sortBy(_.score).last

      val newSM = new SequenceMatch(
        start = startPos,
        end = endPos,
        residueBefore = resBefore,
        residueAfter = resAfter,
        peptide = Some(currPep),
        bestPeptideMatch = Some(bestPepMatch),
        resultSetId = RESULT_SET_ID
      )

      // Add newly created sequence match
      protMatch.sequenceMatches = (protMatch.sequenceMatches.toBuffer + newSM).toArray
      
      protMatch.peptideMatchesCount += 1
    }

    
    ResultSetFakeGenerator.this
  }

  /**
   * Creates new protein match from provided peptides.
   *
   * @param peptides the peptides
   * @return the ResultSetFakeBuilder
   */
  def createNewProteinMatchFromPeptides(peptides: Seq[Peptide]): ResultSetFakeGenerator = {
    require(peptides!= null && peptides.length > 0,"at least one peptide must be provided")
    
    val newProtMatchId = ProteinMatch.generateNewId
    val protSeqBuilder = new StringBuilder    
    val protMatchSeqMatches = ArrayBuffer[SequenceMatch]()
    var( startIdx, endIdx ) = (0,-1)

    for (currPep <- peptides) {
      
      // Update the mapping between peptides and protein matches
      tmpProtMatchIdsByPepId.getOrElseUpdate(currPep.id, new ArrayBuffer[Long]) += newProtMatchId
      
      var currPepSeq = currPep.sequence
      
      // Append the peptide sequence to the protein one
      protSeqBuilder.append(currPepSeq)

      // For each Peptide of the group, create a PeptideMatch
      startIdx = endIdx + 1
      endIdx = startIdx + currPep.sequence.length - 1
      val resBefore = if (startIdx > 0) protSeqBuilder.charAt(startIdx) else '-'
      val resAfter = if (endIdx < protSeqBuilder.length) protSeqBuilder.charAt(startIdx) else '-'      

      // Retrieve best PeptideMatch Id    
      // Get the list of PeptideMatch IDs for the current Peptide ID
      // ascending sort PeptideMatch by score and take last one (the PeptideMatch w max score)
      // peptideMatchIdByPeptideId(currPep.id).map{pmId=>println("PM ID "+pmId+", score: "+tmpPepMatchById(pmId).score)}               
      val maxScorePMId = pepMatchIdsByPepId(currPep.id).toArray.sortBy(tmpPepMatchById(_).score).last

      val newSeqMatch = new SequenceMatch(
        start = startIdx + 1,
        end = endIdx + 1,
        residueBefore = resBefore,
        residueAfter = resAfter,
        peptide = Some(currPep),
        bestPeptideMatch = Some(tmpPepMatchById(maxScorePMId)),
        resultSetId = RESULT_SET_ID
      )
      
      protMatchSeqMatches += newSeqMatch

    }
    
    // Build the protein entity
    val currProtSeq = protSeqBuilder.result    
    val currProt = new Protein(sequence = currProtSeq, id = Protein.generateNewId, alphabet = "aa")
    proteinById += currProt.id -> currProt

    // Create a new ProteinMatch
    val newProtMatch = new ProteinMatch(
      id = newProtMatchId,
      accession = ResultSetRandomator.randomProtAccession,
      description = "Generated protein match",
      proteinId = currProt.id,
      protein = Option(currProt),
      scoreType = "mascot:standard score",
      peptideMatchesCount = peptides.size, //1 PeptideMatch per Peptide
      coverage = 100,
      sequenceMatches = protMatchSeqMatches.toArray,
      resultSetId = RESULT_SET_ID
    )
    
    allProtMatches += newProtMatch

    // Update some mappings
    allPepsByProtSeq += (currProtSeq -> (new ArrayBuffer(peptides.length) ++ peptides) )
    tmpProtMatchById += newProtMatch.id -> newProtMatch

    ResultSetFakeGenerator.this
  }

  /**
   * Export the generated entities into a new result set.
   *
   * @return the generated ResultSet
   */
  def toResultSet(): ResultSet = {

    val peaklistSoft = new PeaklistSoftware(
      id = PeaklistSoftware.generateNewId,
      name = "Distiller",
      version = "4.3.2"
    )

    val peaklist = new Peaklist(
      id = Peaklist.generateNewId,
      fileType = "Mascot generic",
      path = "/fake/filepath/data/VENUS1234.RAW",
      rawFileName = "VENUS1234.RAW",
      msLevel = 2,
      peaklistSoftware = peaklistSoft
    )

    val instrum = new Instrument(
      id = Instrument.generateNewId, name = "VENUS", source = "ESI"
    )
    
    val instrumConf = new InstrumentConfig(
      id = InstrumentConfig.generateNewId,
      name = "VENUS_CFG",
      instrument = instrum,
      ms1Analyzer = "Analyzer",
      msnAnalyzer = null,
      activationType = Activation.CID.toString
    )

    val dtPparser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    val dateStr = "2012-07-24 16:28:39" // was "2012-07-24 16:28:39.085"
    val date = dtPparser.parse(dateStr)

    val seqDB = new SeqDatabase(
      id = SeqDatabase.generateNewId,
      name = "Fake_Seq_DB",
      filePath = "/fake/filepath/Fake_Seq_DB.fasta",
      sequencesCount = 9999,
      releaseDate = date,
      version = "1.0"
    )

    val settings = new SearchSettings(
      id = SearchSettings.generateNewId,
      softwareName = "Mascot",
      softwareVersion = "2.4",
      taxonomy = "All entries",
      maxMissedCleavages = MAX_MISSED_CLEAVAGES,
      ms1ChargeStates = "2+,3+",
      ms1ErrorTol = 10.0,
      ms1ErrorTolUnit = "ppm",
      isDecoy = false,
      usedEnzymes = Array(new Enzyme("TRYPSIC")),
      variablePtmDefs = Array(),
      fixedPtmDefs = Array(),
      seqDatabases = Array(seqDB),
      instrumentConfig = instrumConf,
      quantitation = ""
    )

    val search = new MSISearch(
      id = MSISearch.generateNewId,
      resultFileName = "F123456.dat",
      submittedQueriesCount = allPepMatches.size,
      searchSettings = settings,
      peakList = peaklist,
      date = new Date()
    )

    new ResultSet(
      id = RESULT_SET_ID,
      peptides = allPepsByProtSeq.flatMap(e => e._2).toArray, //Updated in createPepAndCoForProteinMatch
      peptideMatches = allPepMatches.toArray, //Updated in createPepAndCo
      proteinMatches = allProtMatches.toArray, //Created in constructor
      msiSearch = Some(search),
      isDecoy = false, isNative = true
    )

  }

  def printForDebug(): Unit = {

    allPepsByProtSeq.flatMap(e => e._2).map(p => logger.debug("[allPeptidesByProtSeq] Peptide ID: " + p.id + ", seq: " + p.sequence))
    allPepsByProtSeq.map {
      e => logger.debug("[allPeptidesByProtSeq] Prot Sequence: " + e._1 + ", seq: " + e._2.map(p => p.id))
    }
    
    for (protMatch <- allProtMatches) {
      
      logger.debug("---")
      logger.debug("ProteinMatch ID=%d, #SeqMatches=%d, ACC=%s, peptideMatchCount=%d".format(
        protMatch.id, protMatch.sequenceMatches.size, protMatch.accession, protMatch.peptideMatchesCount )
      )
                   
      for (sm <- protMatch.sequenceMatches) {
        
        logger.debug("  SequenceMatch START-STOP=%d-%d, best PM ID=%d".format(sm.start,sm.end,sm.getBestPeptideMatchId) )
        logger.debug("    Peptide ID=%d, sequence=%s".format( sm.getPeptideId, sm.peptide.get.sequence ) )
        
        for ( pepMatchIds <- pepMatchIdsByPepId.get(sm.getPeptideId) ) {
          pepMatchIds.foreach { pepMatchId => 
            logger.debug("      PeptideMatch ID=%d, score=%f, #missed cleavages=%d".format(
              pepMatchId, + tmpPepMatchById(pepMatchId).score, tmpPepMatchById(pepMatchId).missedCleavage )
            )
          }
        }
      }
    }

  }

} 

