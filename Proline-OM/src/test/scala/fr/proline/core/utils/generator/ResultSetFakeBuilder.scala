package fr.proline.core.utils.generator

import java.text.SimpleDateFormat
import java.util.Date

import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.om.model.msi._
import fr.proline.util.primitives._
import fr.proline.util.random._

/**
 * Utility class to generate a fake ResultSet
 * Non-redundant trypsic peptides with an amino acid sequence length in a given range
 * Protein sequence is the exact concatenation of peptide sequences
 * No subset/sameset
 *
 *
 *
 * Required
 * @param pepNb Number of non redundant peptides to get
 * @param proNb Number of non redundant proteins to get
 *
 * Optional
 * @param deltaPepNb Delta on the number of peptides matching a given protein
 * @param pepSeqLengthMin Minimum length for peptide sequence
 * @param pepSeqLengthMax Maximum length for peptide sequence
 *
 */
class ResultSetFakeBuilder(
  pepNb: Int,
  proNb: Int,
  pepSeqLengthMin: Int = 8,
  pepSeqLengthMax: Int = 20) extends AnyRef with Logging {

  require(pepNb > 0, "Peptides # must be > 0")
  require(proNb > 0 && proNb <= pepNb, "Protein # must be > 0 and <= peptides #")

  val MAX_MISSED_CLEAVAGES: Int = 4
  val MIN_MISSED_CLEAVAGES: Int = 1
  val RESULT_SET_ID: Long = ResultSet.generateNewId

  //PeptideMatch 
  var allPepMatches = ListBuffer[PeptideMatch]()
  private var tmpPepMatchById = collection.mutable.Map[Long, PeptideMatch]()
  private var peptideIdByPeptideMatchId = Map[Long, Long]() // ++ allPepMatches.map { pepMatchInst => pepMatchInst.id -> pepMatchInst.peptide.id}    
  private var peptideMatchIdByPeptideId = Map[Long, Iterable[Long]]() //peptideIdByPeptideMatchId groupBy {_._2} map {case (key,value) => (key, value.unzip._1)}

  //ProteinMatch 
  var allProtMatches = ListBuffer[ProteinMatch]()
  private var tmpProtMatchById = collection.mutable.Map[Long, ProteinMatch]()

  //Protein 
  var allProts = ListBuffer[Protein]()

  //Peptide 
  var allPeps = ListBuffer[Peptide]()
  private var tmpPepById = collection.mutable.Map[Long, Peptide]()
  var allPepsByProtSeq = collection.mutable.Map[String, List[Peptide]]() //Peptides for Protein sequence  

  private val avgNbPepPerGroup: Int = pepNb / proNb
  logger.debug("Start building a ResultSet having " + pepNb + " peptides and " + proNb + " proteins.")
  logger.debug(avgNbPepPerGroup + " peptides match on each protein.")

  private var currPepList = ListBuffer[Peptide]() //List of Peptides matching on a given protein

  private val needToAdjustLastProt: Boolean = (avgNbPepPerGroup * proNb) != pepNb
  private var remainPepNb: Int = pepNb //For the last protein
  private var currPairNb: Int = 0
  private var currProtSeq = ""
  private var allPepSeqs = ListBuffer[String]() //to check sequence unicity
  private var currPepSeq = ""

  //Create a map: Protein sequence(String),List[Peptide]  
  //Build Peptide, PeptideMatch, Ms2Query  
  do {
    currPepSeq = ResultSetRandomator.randomAASequence(pepSeqLengthMin, pepSeqLengthMax)
    if (!(allPepSeqs contains (currPepSeq))) { //Check Peptide sequence is unique    	  
      allPepSeqs += currPepSeq

      val currPep = createPeptideAndPeptideMatch(pepSequence = currPepSeq, missCleavage = 0, RSId = RESULT_SET_ID)

      currPepList += currPep //Collect peptides that match on the current protein 	  	 

      //This part is for protein sequence creation (just an exact concatenation
      //of N peptide sequences	 
      //Define # of peptides matching on the current protein
      currProtSeq = currProtSeq.concat(currPepSeq)

      if (currPepList.size == avgNbPepPerGroup) {
        allPepsByProtSeq += (currProtSeq -> currPepList.toList)
        currPepList.clear()
        remainPepNb -= avgNbPepPerGroup
        currProtSeq = ""
      }

    } //end if
  } while (allPeps.size < pepNb) //end while 

  if (remainPepNb > 0) {
    allPepsByProtSeq += (currProtSeq -> currPepList.toList)
  }

  updatePeptideMaps()

  //Build Protein, SequenceMatch, ProteinMatch
  var currPepCount: Int = 0 //Current Peptide # in a protein group
  var currProtCount: Int = 0 //Current Protein #

  for ((currProtSequence, pepList) <- allPepsByProtSeq) {

    createNewProteinMatchFromPeptides(pepList)

    //    
    //    
    //    
    //    var currProt = new Protein(sequence = currProtSequence, id = Protein.generateNewId, alphabet = "aa")
    //    allProts += currProt
    //
    //    var allSeqMatches = ListBuffer[SequenceMatch]()
    //
    //    //Loop over peptides in this group
    //    for (currPep <- pepList) {
    //
    //      //For each Peptide of the group, create a PeptideMatch
    //      val startIdx: Int = currProtSequence.indexOf(currPep.sequence)
    //      val endIdx: Int = startIdx + currPep.sequence.length - 1
    //      val resBefore: Char = currProtSequence.charAt(if (startIdx > 0) startIdx - 1 else 0)
    //      val resAfter: Char = currProtSequence.charAt(if (endIdx == currProtSequence.length - 1) endIdx else endIdx + 1)
    //
    //      val startPos = startIdx + 1
    //      val endPos = endIdx + 1
    //
    //      //Retrieve best PeptideMatch Id    
    //      //Get the list of PeptideMatch IDs for the current Peptide ID
    //      //ascending sort PeptideMatch by score and take last one (the PeptideMatch w max score)
    //      //peptideMatchIdByPeptideId(currPep.id).map{pmId=>println("PM ID "+pmId+", score: "+tmpPepMatchById(pmId).score)}               
    //      val maxScorePMId = peptideMatchIdByPeptideId(currPep.id).toList.sortBy(tmpPepMatchById(_).score).last
    //
    //      allSeqMatches += new SequenceMatch(
    //        start = startPos, end = endPos,
    //        residueBefore = resBefore, residueAfter = resAfter,
    //        peptide = Option[Peptide](currPep),
    //        bestPeptideMatch = Option[PeptideMatch](tmpPepMatchById(maxScorePMId)),
    //        resultSetId = RESULT_SET_ID)
    //
    //    } //end while pepGroup        
    //
    //    //Create a new ProteinMatch for each Protein 
    //    allProtMatches += new ProteinMatch(
    //      id = ProteinMatch.generateNewId,
    //      accession = ResultSetRandomator.protAccession,
    //      description = "Generated accession",
    //      proteinId = currProt.id,
    //      protein = Option(currProt),
    //      scoreType = "mascot:standard score",
    //      peptideMatchesCount = pepList.size, //1 PeptideMatch per Peptide
    //      coverage = 100,
    //      sequenceMatches = allSeqMatches.toArray,
    //      resultSetId = RESULT_SET_ID)

  } //end for

  tmpProtMatchById = collection.mutable.Map() ++ allProtMatches.map { protMatch => (protMatch.id -> protMatch) }

  /**
   * Create one new Peptide (+Ms2Query, +PeptideMatch) from the peptideList
   * and associate new SequenceMatch with an EXISTING ProteinMatch
   *
   * Update allPepMatches & allPepsByProtSeq collections
   */
  private def createPepAndCoForProtMatchId(pepIdList: List[Long], proMatchId: Long,
                                           missCleavage: Int, RSId: Long): ProteinMatch = {

    var proMatch = tmpProtMatchById(proMatchId)

    val builtSequence: String = pepIdList.foldLeft("")(_ + tmpPepById(_).sequence)
    //    logger.info("MERGED Sequence = "+builtSequence+" from Pep IDs: "+pepIdList)

    var builtPep = createPeptideAndPeptideMatch(pepSequence = builtSequence, missCleavage = missCleavage, RSId = RSId)

    allPepsByProtSeq += proMatch.protein.get.sequence ->
      (allPepsByProtSeq.apply(proMatch.protein.get.sequence).+:(builtPep))

    //Create new SequenceMatch, add it to existing ProteinMatch's sequence matches
    //Retrieve SequenceMatch corresponding to the pepIdList    	
    var firstSM, lastSM: SequenceMatch = null
    proMatch.sequenceMatches.foreach(sm =>
      if (pepIdList.head == sm.peptide.get.id) firstSM = sm
      else if (pepIdList.last == sm.peptide.get.id) lastSM = sm
    )

    //There is one PeptideMatch. The just newly created. 
    val bestPM: PeptideMatch = allPepMatches(allPepMatches.length - 1)

    var nSM = new SequenceMatch(
      start = firstSM.start,
      end = lastSM.end,
      residueBefore = firstSM.residueBefore,
      residueAfter = lastSM.residueAfter,
      peptide = Option[Peptide](builtPep),
      bestPeptideMatch = Option[PeptideMatch](bestPM),
      resultSetId = RSId
    )
    proMatch.sequenceMatches = (proMatch.sequenceMatches.toBuffer + nSM).toArray

    proMatch.peptideMatchesCount += 1

    proMatch
  }

  /**
   * Create a new missed cleaved Peptide from a Peptide list & PeptideMatch
   * Update allPepMatches collection
   */
  private def createPeptideAndPeptideMatch(pepSequence: String, missCleavage: Int, RSId: Long): Peptide = {

    val builtPep = new Peptide(
      id = Peptide.generateNewId,
      sequence = pepSequence,
      ptms = null,
      calculatedMass = Peptide.calcMass(pepSequence)
    )
    allPeps += builtPep
    tmpPepById = collection.mutable.Map() ++ allPeps.map { pep => (pep.id -> pep) }

    createPepMatch(pep = builtPep, missCleavage = missCleavage, RSId = RSId).peptide
  }

  private def createPepMatch(pep: Peptide, missCleavage: Int, RSId: Long): PeptideMatch = {
    val charge: Int = ResultSetRandomator.randomPepCharge
    val queryID: Long = Ms2Query.generateNewId

    val msq: Ms2Query = new Ms2Query(
      id = queryID,
      initialId = toInt(queryID),
      moz = getIonMzFromNeutralMass(neutralMass = pep.calculatedMass, charge = charge),
      charge = charge,
      spectrumTitle = "generated spectrum " + queryID
    )
    val builtPM: PeptideMatch = new PeptideMatch(
      id = PeptideMatch.generateNewId,
      rank = 1,
      score = ResultSetRandomator.randomPepMatchScore,
      scoreType = "mascot:standard score",
      deltaMoz = 0.15f,
      isDecoy = false,
      peptide = pep,
      missedCleavage = missCleavage,
      msQuery = msq,
      resultSetId = RSId
    )
    allPepMatches += builtPM

    tmpPepMatchById = collection.mutable.Map() ++ allPepMatches.map { pepMatch => (pepMatch.id -> pepMatch) }
    builtPM
  }

  /**
   * Used only for printing
   */
  private def updatePeptideMaps(): Unit = {
    peptideIdByPeptideMatchId = Map() ++ allPepMatches.map { pepMatchInst => pepMatchInst.id -> pepMatchInst.peptide.id }
    peptideMatchIdByPeptideId = peptideIdByPeptideMatchId groupBy { _._2 } map { case (key, value) => (key, value.unzip._1) }
  }

  /**
   * Add a given number of duplicated peptide matches
   * Randomly select a PeptideMatch
   */
  def addDuplicatedPeptideMatches(duplicatedPepNb: Int): ResultSetFakeBuilder = {

    val pepMatchCount: Int = allPepMatches.size + duplicatedPepNb

    while (allPepMatches.size < pepMatchCount) {

      var pepMatchIdx: Int = randomInt(minInclu = 0, maxInclu = allPepMatches.size - 1)
      val pmOrg: PeptideMatch = allPepMatches(pepMatchIdx)

      val builtPM: PeptideMatch = createPepMatch(pep = pmOrg.peptide, missCleavage = pmOrg.missedCleavage, RSId = pmOrg.resultSetId)

      //Retrieve the ProteinMatch associated to this PeptideMatch		      
      var protMatch = allProtMatches.filter(pm => {
        (!pm.sequenceMatches.filter(sm => sm.peptide.get.id == pmOrg.peptide.id).isEmpty)
      })(0) //Take first one
      protMatch.peptideMatchesCount += 1

      //Modify the SequenceMatch if best PeptideMatch ID has changed
      var sm = protMatch.sequenceMatches.filter(sm => sm.getPeptideId == pmOrg.peptide.id)(0)
      if (builtPM.score > sm.bestPeptideMatch.get.score) {
        sm.bestPeptideMatch = Option[PeptideMatch](builtPM)
      }

    } //endwhile

    updatePeptideMaps()

    logger.info(duplicatedPepNb + " duplicated PeptideMatch added")
    this
  }

  /**
   * Requirements: Create new peptides with NEW missed cleavages. It other words,
   * if peptides have been previously created with 2 missed cleavage, you cannot
   * recall this method with missCleavageNb=2, but can create with missCleavageNb=1 or 3...
   *
   */
  def addNewPeptidesWithMissCleavage(pepNb: Int, missCleavageNb: Int): ResultSetFakeBuilder = {
    require(missCleavageNb <= MAX_MISSED_CLEAVAGES && missCleavageNb >= MIN_MISSED_CLEAVAGES, "Number of miss cleavage must be >=" + MIN_MISSED_CLEAVAGES + " and <=" + MAX_MISSED_CLEAVAGES)

    val missCleavageExists: Boolean = (allPepMatches find (_.missedCleavage == missCleavageNb)).isDefined
    require(missCleavageExists == false, "Peptides with this missed cleavage have already been created")

    //Max # of peptides that can be created with the given missed cleavage (per ProteinMatch ID)
    //Example with a ProteinMatch with 6 Peptides (FYI, 1peptide <-> 1SequenceMatch) 
    //     1        .      2        .      3        .       4       .    5           =>  missCleavageNb
    //|_|_|_|_|_|_| . |_|_|_|_|_|_| . |_|_|_|_|_|_| . |_|_|_|_|_|_| . |_|_|_|_|_|_|
    //|_|_|			. |_|_|_|       . |_|_|_|_|     . |_|_|_|_|_|   . |_|_|_|_|_|_|
    //  |_|_|       .   |_|_|_|     .   |_|_|_|_|   .   |_|_|_|_|_|
    //    |_|_|     .     |_|_|_|   .     |_|_|_|_| .
    //      |_|_|   .       |_|_|_| . 
    //        |_|_| . 
    //     5        .      4        .      3        .       2       .    1           => max # of peptides with missed cleavages

    //Example with a ProteinMatch with 3 Peptides (FYI, 1peptide <-> 1SequenceMatch)
    //     1        .      2                                                         =>  missCleavageNb
    //|_|_|_|       . |_|_|_|       
    //|_|_|			. |_|_|_|           
    //  |_|_|       .                
    //     2        .      1                                                         => max # of peptides with missed cleavages

    //Collect peptides having no missed cleavage (It's from these peptides, the missed cleaved peptides will be created)    
    val peptideIdNoMissCleav: Array[Peptide] = allPeps.toArray.filter(p =>
      ((ResultSetRandomator.trypsicAA.toList mkString ("|")).r findFirstMatchIn p.sequence.substring(0, p.sequence.length - 1)).isEmpty)

    //For each ProteinMatch, get peptides without missed cleavages
    //and keep only ProteinMatch having enough peptides to create new ones w missed cleavages
    val peptideIdByProteinMatchId: Map[Long, List[Long]] = Map() ++ allProtMatches.map(proMatchInst => proMatchInst.id -> (proMatchInst.sequenceMatches.map(_.getPeptideId).toList))
    var eligiblePeptidesForProteinMatchId = peptideIdByProteinMatchId.filterNot(p => peptideIdNoMissCleav.contains(p._2))

    //Suppress entries having size list < (missCleavageNb + 1)   
    eligiblePeptidesForProteinMatchId = eligiblePeptidesForProteinMatchId.filter(e => e._2.size >= (missCleavageNb + 1))
    //    eligiblePeptidesForProteinMatchId map {e=> logger.info(e._1+" => "+e._2) }

    //Total max # of peptides that can be created with the given missed cleavage
    val maxSM: Int = eligiblePeptidesForProteinMatchId.foldLeft(0)(_ + _._2.size - (missCleavageNb))
    require(pepNb <= maxSM, "Maximum number of peptides with " + missCleavageNb + " missed cleavages is: " + maxSM)

    logger.info("Adding " + pepNb + " new peptides (to " + allPeps.size + " existing peptides) with " + missCleavageNb + " missed cleavage(s)")

    //Store keys in a buffer to be able to access a key from a random index
    var keyBuffer: Buffer[Long] = eligiblePeptidesForProteinMatchId.flatMap(e => List(e._1)).toBuffer

    var currPepNb: Int = 0
    while (currPepNb < pepNb) {

      //Randomly determine which ProteinMatch will have new Peptide w missed cleavage(s)       
      val idx = randomInt(0, eligiblePeptidesForProteinMatchId.size - 1)
      val currRandomProMatchKey = keyBuffer(idx) //ProteinMatch key

      //Take the (missCleavageNb+1)first peptides to create the new missed cleaved peptide & Co
      val pepIdList = eligiblePeptidesForProteinMatchId(currRandomProMatchKey).take(missCleavageNb + 1)
      var builtProMatch: ProteinMatch = createPepAndCoForProtMatchId(pepIdList = pepIdList, proMatchId = currRandomProMatchKey,
        missCleavage = missCleavageNb, RSId = RESULT_SET_ID)

      //      logger.debug("Created 1 new peptide for ProteinMatch ID: "+builtProMatch.id)    
      //      builtProMatch.sequenceMatches.map(sm=>
      //        logger.debug("...PeptideID"+sm.peptide.get.id.toString+", SeqMatch ["+sm.start+"/"+sm.end+", PepID"+sm.getPeptideId+"]")        )

      //Remove first peptide from list        
      eligiblePeptidesForProteinMatchId += (currRandomProMatchKey -> eligiblePeptidesForProteinMatchId(currRandomProMatchKey).tail)
      //      eligiblePeptidesForProteinMatchId map {e=> logger.info(e._1+" => "+e._2) }

      currPepNb += 1
      if (eligiblePeptidesForProteinMatchId(currRandomProMatchKey).size < (missCleavageNb + 1)) {
        //Remove entry
        eligiblePeptidesForProteinMatchId -= currRandomProMatchKey
        keyBuffer.remove(idx)
      }

    } //end while  

    updatePeptideMaps()

    this
  }

  def addSharedPeptide(protMatches: Seq[ProteinMatch]): ResultSetFakeBuilder = {
    val currPepSeq = ResultSetRandomator.randomAASequence(pepSeqLengthMin, pepSeqLengthMax)
    if (!(allPepSeqs contains (currPepSeq))) { //Check Peptide sequence is unique    	  
      allPepSeqs += currPepSeq
      val currPep = createPeptideAndPeptideMatch(pepSequence = currPepSeq, missCleavage = 0, RSId = RESULT_SET_ID)
      updatePeptideMaps()

      for (proMatch <- protMatches) {

        // add created peptide to allPepsByProtSeq 
        allPepsByProtSeq += proMatch.protein.get.sequence ->
          (allPepsByProtSeq.apply(proMatch.protein.get.sequence).+:(currPep))

        val pSeq = proMatch.protein.get.sequence.concat(currPepSeq)
        allPepsByProtSeq += pSeq -> allPepsByProtSeq(proMatch.protein.get.sequence)
        allPepsByProtSeq -= proMatch.protein.get.sequence

        val startPos = proMatch.protein.get.sequence.length + 1
        val endPos: Int = startPos + currPep.sequence.length - 1
        val resBefore: Char = proMatch.protein.get.sequence.last
        val resAfter: Char = currPepSeq.last

        val maxScorePMId = peptideMatchIdByPeptideId(currPep.id).toList.sortBy(tmpPepMatchById(_).score).last

        var nSM = new SequenceMatch(start = startPos, end = endPos,
          residueBefore = resBefore, residueAfter = resAfter,
          peptide = Option[Peptide](currPep),
          bestPeptideMatch = Option[PeptideMatch](tmpPepMatchById(maxScorePMId)),
          resultSetId = RESULT_SET_ID)

        proMatch.sequenceMatches = (proMatch.sequenceMatches.toBuffer + nSM).toArray
        proMatch.peptideMatchesCount += 1
      }

    }
    this
  }

  def createNewProteinMatchFromPeptides(peptides: Seq[Peptide]): ResultSetFakeBuilder = {
    var currProtSequence = ""
    for (peptide <- peptides) {
      currProtSequence = currProtSequence.concat(peptide.sequence)
    }

    var currProt = new Protein(sequence = currProtSequence, id = Protein.generateNewId, alphabet = "aa")
    allProts += currProt

    var allSeqMatches = ListBuffer[SequenceMatch]()

    for (currPep <- peptides) {

      //For each Peptide of the group, create a PeptideMatch
      val startIdx: Int = currProtSequence.indexOf(currPep.sequence)
      val endIdx: Int = startIdx + currPep.sequence.length - 1
      val resBefore: Char = currProtSequence.charAt(if (startIdx > 0) startIdx - 1 else 0)
      val resAfter: Char = currProtSequence.charAt(if (endIdx == currProtSequence.length - 1) endIdx else endIdx + 1)

      val startPos = startIdx + 1
      val endPos = endIdx + 1

      //Retrieve best PeptideMatch Id    
      //Get the list of PeptideMatch IDs for the current Peptide ID
      //ascending sort PeptideMatch by score and take last one (the PeptideMatch w max score)
      //peptideMatchIdByPeptideId(currPep.id).map{pmId=>println("PM ID "+pmId+", score: "+tmpPepMatchById(pmId).score)}               
      val maxScorePMId = peptideMatchIdByPeptideId(currPep.id).toList.sortBy(tmpPepMatchById(_).score).last

      allSeqMatches += new SequenceMatch(
        start = startPos, end = endPos,
        residueBefore = resBefore,
        residueAfter = resAfter,
        peptide = Option[Peptide](currPep),
        bestPeptideMatch = Option[PeptideMatch](tmpPepMatchById(maxScorePMId)),
        resultSetId = RESULT_SET_ID
      )

    }

    //Create a new ProteinMatch for each Protein 
    allProtMatches += new ProteinMatch(
      id = ProteinMatch.generateNewId,
      accession = ResultSetRandomator.randomProtAccession,
      description = "Generated accession",
      proteinId = currProt.id,
      protein = Option(currProt),
      scoreType = "mascot:standard score",
      peptideMatchesCount = peptides.size, //1 PeptideMatch per Peptide
      coverage = 100,
      sequenceMatches = allSeqMatches.toArray,
      resultSetId = RESULT_SET_ID
    )

    allPepsByProtSeq += (currProtSequence -> peptides.toList)

    this
  }

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
      isDecoy = false, isNative = true)

  }
  /**
   * Utility methods ---------------------------------------------------------
   *
   */

  /**
   * Compute the ion m/z at a given charge knowing its neutral mass
   * @param neutralMass neutral mass of the element in Da
   * @param charge charge of the resulting ion
   * @return m/z of the resulting ion
   */
  def getIonMzFromNeutralMass(neutralMass: Double, charge: Int): Double = {
    require(charge > 0, "charge cannot be null or negative")
    val mz: Double = (neutralMass + charge * 1.007825) / charge;
    mz;
  }

  def printForDebug(): Unit = {

    allPepsByProtSeq.flatMap(e => e._2).map(p => logger.debug("[allPeptidesByProtSeq] Peptide ID: " + p.id + ", seq: " + p.sequence))
    allPepsByProtSeq.map {
      e => logger.debug("[allPeptidesByProtSeq] Prot Sequence: " + e._1 + ", seq: " + e._2.map(p => p.id))
    }
    //	    val peptideIdByPeptideMatchId:Map[Int,Int] = Map() ++ allPepMatches.map { pepMatchInst => pepMatchInst.id -> pepMatchInst.peptide.id}    
    //	    val peptideMatchIdByPeptideId = peptideIdByPeptideMatchId groupBy {_._2} map {case (key,value) => (key, value.unzip._1)}  

    for (pm <- allProtMatches) {
      logger.debug("")
      logger.debug(">>>> ProteinMatch ID: " + pm.id + ", #SeqMatch: " + pm.sequenceMatches.size + ", ACC: " + pm.accession + ", peptideMatchCount: " + pm.peptideMatchesCount)
      for (sm <- pm.sequenceMatches) {
        logger.debug("      SequenceMatch START-STOP: " + sm.start + "-" + sm.end + ", best PM ID: " + sm.getBestPeptideMatchId)
        logger.debug("      		Peptide ID: " + sm.getPeptideId + ", " + sm.peptide.get.sequence)
        for (pmIt <- peptideMatchIdByPeptideId.get(sm.getPeptideId)) {
          pmIt.foreach(
            pmId => logger.debug("      		*PeptideMatch id " + pmId + ", score: " + tmpPepMatchById(pmId).score + ", miss cleavage: " + tmpPepMatchById(pmId).missedCleavage))
        }
      }
    }

  }

} 

