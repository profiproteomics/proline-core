package fr.proline.core.utils.generator

import scala.collection.mutable.ListBuffer
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.Protein
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.SequenceMatch
import scala.collection.mutable.Buffer

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
 
 */
class ResultSetFakeBuilder (										
    pepNb:Int,     
    proNb:Int, 
    deltaPepNb:Int = 0,    									
    pepSeqLengthMin:Int = 8, 
    pepSeqLengthMax:Int = 20  
    ) extends AnyRef with Logging {
   
  require(pepNb > 0, "Peptides # must be > 0")  
  require(proNb > 0 && proNb <= pepNb, "Protein # must be > 0 and <= peptides #")   
  require(deltaPepNb >= 0, "deltaPepNb must be >= 0")  
  require((pepNb/proNb) > deltaPepNb, "Peptide# Protein# ratio must be > delta")   
  require(if(deltaPepNb==0) pepNb%proNb==0 else true, "deltaPepNb cannot be null if protein# is not an integer multiple of peptide#")
      
  private val avgNbPepPerGroup:Int = pepNb/proNb      
  private var currDelta:Int = deltaPepNb
  private var currExpectedPepInProtNb:Int = 0  
  private val nbProtPairs:Int = proNb/2
  private val needToAdjustLastProt:Boolean = (proNb % 2) != 0  
  private var remainPepNb:Int = pepNb //For the last protein
  private var currPairNb:Int = 0
   
  private var currProtSequence = ""
                     
  private var allPepSequences = ListBuffer[String]() //to check sequence unicity
  private var currPepSequence = ""
    
  private var isNewProt:Boolean = true
  private var evenProt:Boolean = true
    
  
//  var rs:ResultSet = null  
  
  var allPepMatches:ListBuffer[PeptideMatch] = ListBuffer[PeptideMatch]()
  var tmpPeptideMatchById:collection.mutable.Map[Int,PeptideMatch] = collection.mutable.Map[Int,PeptideMatch]()      
  var allProtMatches:ListBuffer[ProteinMatch] = ListBuffer[ProteinMatch]()    
  var tmpProtMatchById:collection.mutable.Map[Int,ProteinMatch] = collection.mutable.Map[Int,ProteinMatch]()
  var allProts:ListBuffer[Protein] = ListBuffer[Protein]()
  var allPeptides:ListBuffer[Peptide] = ListBuffer[Peptide]()
  var tmpPeptideById:collection.mutable.Map[Int,Peptide] = collection.mutable.Map[Int,Peptide]()
  private var currPeptideList:ListBuffer[Peptide] = ListBuffer[Peptide]() //List of Peptides matching on a given protein
  var allPeptidesByProtSeq = collection.mutable.Map[String,List[Peptide]]()
  var globalPepCount: Int = 0
  
  logger.debug("Building a ResultSet having "+pepNb+" peptides and "+proNb+" proteins.")
  if (deltaPepNb == 0) logger.debug(avgNbPepPerGroup+" peptides match on each protein.")
    else logger.debug(avgNbPepPerGroup+" peptides +/-"+deltaPepNb+" match on each protein.")
  
  val resultSetId:Int = ResultSet.generateNewId  
  
  do {   
   
    //Generate Peptide, MSQuery & PeptideMatch
	currPepSequence = Randomator.aaSequence(pepSeqLengthMin, pepSeqLengthMax)
	//val massPep:Double = Peptide.calcMass(currPepSequence)	    	
	if (!(allPepSequences contains(currPepSequence))) {	//Peptide sequence is unique    
	  allPepSequences += currPepSequence 
	   
	  val currPep = createPepAndCo(pepSequence=currPepSequence, missCleavage=0, RSId=resultSetId)	  	
	  
	  //Collect peptides that match on the current protein 
	  currPeptideList += currPep
	  
	  globalPepCount += 1		  
	  	
	  //This part is for protein sequence creation (just an exact concatenation
	  //of N peptide sequences	 
	  //Define # of peptides matching on the current protein
	  currProtSequence = currProtSequence.concat(currPepSequence)
	  if (isNewProt) {		    
		if (deltaPepNb > 0) { //Varying # of peptides matching on a protein 		    
		  if ((currPairNb == nbProtPairs) && needToAdjustLastProt) {//Last protein to readjust
		    currDelta = 0
		    currExpectedPepInProtNb = remainPepNb							
		  } else if (evenProt) { 		    	
	    	currDelta = Randomator.randomInt(1, deltaPepNb) 
	    	currExpectedPepInProtNb = avgNbPepPerGroup + currDelta  
	    	evenProt = false	    	
		  } else {		   
			currDelta = -currDelta
			currExpectedPepInProtNb = avgNbPepPerGroup + currDelta  
			evenProt = true
			currPairNb += 1					   
		  }	    
		} else {//Same # of peptides matching on each protein		  
		  currDelta = 0	 
		  currExpectedPepInProtNb = avgNbPepPerGroup + currDelta  
		}	    	    	   
	    isNewProt = false
	  }	 
	  	  
	  if (currPeptideList.size == currExpectedPepInProtNb) {		    
		  allPeptidesByProtSeq += (currProtSequence -> currPeptideList.toList)  		      
	      currPeptideList.clear()
	      currProtSequence = ""
	      remainPepNb -= currExpectedPepInProtNb  
	      isNewProt = true	      
	  } 	  	  	 
	  
	} //end if
  } while(globalPepCount < pepNb)  //end while 
           
    
  //Create Protein
  var currPepCount:Int = 0 //Current Peptide # in a protein group
  var currProtCount:Int = 0 //Current Protein #
  
  for ((currProtSequence,pepList) <- allPeptidesByProtSeq) {  
    var currProt = new Protein(sequence=currProtSequence, id=Protein.generateNewId)
	allProts += currProt
	
    var allSeqMatches = ListBuffer[SequenceMatch]()
        
    //Loop until to reach N peptides in this group
    for (currPep <- pepList) {
      
      //For each Peptide of the group, create a PeptideMatch
      val startIdx: Int = currProtSequence.indexOf(currPep.sequence) 
      val endIdx: Int = startIdx + currPep.sequence.length -1
      val resBefore: Char = currProtSequence.charAt(if (startIdx>0)startIdx-1 else 0)
      val resAfter: Char = currProtSequence.charAt(if (endIdx == currProtSequence.length-1) endIdx else endIdx+1)
      
      val startPos = startIdx+1
      val endPos = endIdx+1
      
      allSeqMatches += new SequenceMatch(start=startPos,end=endPos, 
          residueBefore=resBefore, residueAfter=resAfter,
          peptide=Option[Peptide](currPep), resultSetId=resultSetId)
            
    } //end while pepGroup        
   
    //Create a new ProteinMatch for each Protein 
    allProtMatches += new ProteinMatch(
          id=ProteinMatch.generateNewId,
	      accession=Randomator.protAccession, 
	      description="Generated accession", 
	      proteinId=currProt.id,
	      protein=Option(currProt),
	      peptideMatchesCount=pepList.size, //Pour le moment, 1 PeptideMatch / peptide
	      coverage=100,
	      sequenceMatches=allSeqMatches.toArray,
	      resultSetId=resultSetId)
    tmpProtMatchById = collection.mutable.Map() ++ allProtMatches.map { protMatch => ( protMatch.id -> protMatch ) }    
    
  }//end for
  
//  rs = new ResultSet(id=resultSetId,
//        peptides=allPeptidesByProtSeq.flatMap(e => e._2).toArray, 
//        peptideMatches=allPepMatches.toArray, 
//        proteinMatches=allProtMatches.toArray, 
//        isDecoy=false, 
//        isNative=true)  
            
  /**
   * Create one new Peptide (+Ms2Query, +PeptideMatch)
   * and associate new SequenceMatch with an EXISTING ProteinMatch
   * 
   * Update allPepMatches & allPeptidesByProtSeq collections
   */
  def createPepAndCoForProteinMatch(pepIdList:List[Int], proMatchId:Int, 
      missCleavage:Int, RSId:Int):ProteinMatch = {
        
    var proMatch = tmpProtMatchById(proMatchId)
       
    val builtSequence:String = pepIdList.foldLeft("")(_+ tmpPeptideById(_).sequence)      
//    logger.info("MERGED Sequence = "+builtSequence+" from Pep IDs: "+pepIdList)
            
    var builtPep = createPepAndCo(pepSequence=builtSequence, missCleavage=missCleavage, RSId=RSId)
    
    allPeptidesByProtSeq += proMatch.protein.get.sequence->
    	(allPeptidesByProtSeq.apply(proMatch.protein.get.sequence).+:(builtPep))  
		    
	//Create new SequenceMatch, add it to existing ProteinMatch's sequence matches
    //Retrieve SequenceMatch corresponding to the pepIdList    	
    var firstSM, lastSM:SequenceMatch = null	
    proMatch.sequenceMatches.foreach( sm=> 
      if(pepIdList.head == sm.peptide.get.id) {
        firstSM = sm
      } else if (pepIdList.last == sm.peptide.get.id) {
        lastSM = sm
      }
    )
           
	var nSM = new SequenceMatch(start=firstSM.start, end=lastSM.end, 
	    residueBefore=firstSM.residueBefore, residueAfter=lastSM.residueAfter,
		peptide=Option[Peptide](builtPep), resultSetId=RSId)	             	      	  
    proMatch.sequenceMatches = (proMatch.sequenceMatches.toBuffer + nSM).toArray
    proMatch   
  }
    
  /**
   * Create a new missed cleaved Peptide from a Peptide list & PeptideMatch
   * Update allPepMatches collection
   */  
  def createPepAndCo(pepSequence:String, missCleavage:Int, RSId:Int):Peptide = {  
    
    val builtPep = new Peptide(id=Peptide.generateNewId, sequence=pepSequence, 
        ptms=null,calculatedMass=Peptide.calcMass(pepSequence))	  
    allPeptides += builtPep
    tmpPeptideById = collection.mutable.Map() ++ allPeptides.map { pep => ( pep.id -> pep ) }    
    
    val charge = Randomator.pepCharge      	  
	val queryID = Ms2Query.generateNewId
	  
	val msq = new Ms2Query(id=queryID, initialId=queryID, 
	    	moz=getIonMzFromNeutralMass(neutralMass=builtPep.calculatedMass, charge=charge),
	        charge=charge, spectrumTitle="generated spectrum "+queryID)      
            
	allPepMatches += new PeptideMatch(id=PeptideMatch.generateNewId, rank=1,
	        score=Randomator.matchScore, scoreType="Mascot", deltaMoz=0.15, 
	        isDecoy= false, peptide=builtPep, missedCleavage=missCleavage,
	        msQuery=msq, resultSetId=RSId ) 
    tmpPeptideMatchById = collection.mutable.Map() ++ allPepMatches.map { pepMatch => ( pepMatch.id -> pepMatch ) }          
    builtPep
  }
  
  def addDuplicatedPeptides(duplicatedPepMinNb:Int, duplicatedPepMaxNb:Int): ResultSetFakeBuilder = {
    this
  }
  
  /**
   * Requirements: Create new peptides with NEW missed cleavages. It other words, 
   * if peptides have been previously created with 2 missed cleavage, you cannot 
   * recall this method missCleavageNb=2, but can create with missCleavageNb=1 or 3 or 4... 
   */
  def addNewPeptidesWithMissCleavage(pepNb:Int, missCleavageNb:Int):ResultSetFakeBuilder = {   
    require(missCleavageNb<=4 && missCleavageNb>0, "Number of miss cleavage must be >0 and <=4")
    
    val missCleavageExists:Boolean = (allPepMatches find(_.missedCleavage == missCleavageNb)).isDefined
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
    val peptideIdNoMissCleav:Array[Peptide] = allPeptides.toArray.filter(p => 
      ((Randomator.trypsicAA.toList mkString("|")).r findFirstMatchIn p.sequence.substring(0,p.sequence.length-1)).isEmpty)
  
    //For each ProteinMatch, get peptides without missed cleavages
    //and keep only ProteinMatch having enough peptides to create new ones w missed cleavages
    val peptideIdByProteinMatchId:Map[Int,List[Int]] = Map() ++ allProtMatches.map(proMatchInst 
        => proMatchInst.id -> (proMatchInst.sequenceMatches.map(_.getPeptideId).toList))
    var eligiblePeptideByProteinMatchId = peptideIdByProteinMatchId.filterNot(p => peptideIdNoMissCleav.contains(p._2) )        
    
    //Suppress entries having size list < (missCleavageNb + 1)   
    eligiblePeptideByProteinMatchId = eligiblePeptideByProteinMatchId.filter(e => e._2.size >= (missCleavageNb + 1))           
//    eligiblePeptideByProteinMatchId map {e=> logger.info(e._1+" => "+e._2) }
    
     //Total max # of peptides that can be created with the given missed cleavage
    val maxSM:Int = eligiblePeptideByProteinMatchId.foldLeft(0)(_+ _._2.size- (missCleavageNb))            
    require(pepNb <= maxSM, "Maximum number of peptides with "+missCleavageNb+" missed cleavages is: "+maxSM)     
    logger.info(pepNb+" new peptides (added to "+globalPepCount+" existing peptides) will have "+missCleavageNb+" missed cleavage(s)")
     
    
    //Store keys in a buffer to be able to access a key from a random index
    var keyBuffer:Buffer[Int] = eligiblePeptideByProteinMatchId.flatMap(e => List(e._1)).toBuffer     
        
    var currPepNb:Int = 0       
    while(currPepNb < pepNb) {  
       
       //Randomly determine which ProteinMatch will have new Peptide w missed cleavage(s)       
      val idx = Randomator.randomInt(0, eligiblePeptideByProteinMatchId.size-1)
      val currRandomProMatchKey = keyBuffer(idx) //ProteinMatch key
                        
      //Take the (missCleavageNb+1)first peptides to create the new missed cleaved peptide & Co
      val pepIdList = eligiblePeptideByProteinMatchId(currRandomProMatchKey).take(missCleavageNb+1)     
      var builtProMatch:ProteinMatch = createPepAndCoForProteinMatch(pepIdList=pepIdList, proMatchId=currRandomProMatchKey, 
           missCleavage=missCleavageNb, RSId=resultSetId)
//      logger.debug("Created 1 new peptide for ProteinMatch ID: "+builtProMatch.id)    
//      builtProMatch.sequenceMatches.map(sm=>
//        logger.debug("...PeptideID"+sm.peptide.get.id.toString+", SeqMatch ["+sm.start+"/"+sm.end+", PepID"+sm.getPeptideId+"]")        )
      
        //Remove first peptide from list        
      eligiblePeptideByProteinMatchId += (currRandomProMatchKey -> eligiblePeptideByProteinMatchId(currRandomProMatchKey).tail)            
//      eligiblePeptideByProteinMatchId map {e=> logger.info(e._1+" => "+e._2) }
    
      currPepNb += 1       
      if (eligiblePeptideByProteinMatchId(currRandomProMatchKey).size < (missCleavageNb+1)) {
        //Remove entry
        eligiblePeptideByProteinMatchId -= currRandomProMatchKey
        keyBuffer.remove(idx)
      }               
        
    } //end while  
        
//    allPeptidesByProtSeq.flatMap(e => e._2).map(p=>println("[allPeptidesByProtSeq] Peptide ID: "+p.id+", seq: "+p.sequence))
//    allPeptidesByProtSeq.map{
//      e=> println("[allPeptidesByProtSeq] Prot Sequence: "+e._1+", seq: "+e._2.map(p=>p.id))
//    }
//    val peptideIdByPeptideMatchId:Map[Int,Int] = Map() ++ allPepMatches.map { pepMatchInst => pepMatchInst.id -> pepMatchInst.peptide.id}    
//    val peptideMatchIdByPeptideId = peptideIdByPeptideMatchId groupBy {_._2} map {case (key,value) => (key, value.unzip._1)}  
//            
//    for (pm <- allProtMatches) {
//      logger.info("")
//      logger.info(">>>> ProteinMatch ID: "+pm.id+", #SeqMatch: "+pm.sequenceMatches.size+", ACC: "+pm.accession)                       
//      for (sm <- pm.sequenceMatches) {
//        logger.info("      SequenceMatch START-STOP: "+sm.start+"-"+sm.end)
//        logger.info("      		Peptide ID: "+sm.getPeptideId+", "+sm.peptide.get.sequence)
//        for (pmIt <- peptideMatchIdByPeptideId.get(sm.getPeptideId)) {    
//        	pmIt.foreach(        	    
//        	    pmId => logger.info("      		*PeptideMatch id "+pmId+", miss cleavage: "+tmpPeptideMatchById(pmId).missedCleavage))      
//        }        
//      }      
//    }   
    
    this
  }
    
  def toResultSet():ResultSet= {
    
    new ResultSet(id=resultSetId,
        peptides=allPeptidesByProtSeq.flatMap(e => e._2).toArray, //Updated in createPepAndCoForProteinMatch
        peptideMatches=allPepMatches.toArray, //Updated in createPepAndCo
        proteinMatches=allProtMatches.toArray, 
        isDecoy=false, isNative=true) 
    
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
	def getIonMzFromNeutralMass(neutralMass:Double, charge:Int):Double = {
		require(charge>0, "charge cannot be null or negative")
		val mz:Double = ( neutralMass + charge * 1.007825 ) / charge ;
		mz;
	}
  	
  	def print():Unit = {    	  
  	  
//  	  logger.info("ResultSet id:"+rs.id+", name: "+rs.name+", description: "+rs.description+", isQuantified: "+rs.isQuantified)    	  
//  	 
//  	  rs.getProteins.get map { prot=>
//  	     logger.info("=> Protein "+prot.id+" "+prot.sequence)
//  	     logger.info("Length: "+prot.length+", mass: "+prot.mass+", pi: "+prot.pi+", alphabet: "+prot.alphabet+", crc64: "+prot.crc64)
//  	  }
//  	  rs.proteinMatches map { pm => logger.info("ProteinMatch id: "+pm.id.toString+", accession: "+pm.accession+", peptide match count: "+pm.peptideMatchesCount) }
  	  
  	    	  
  	}
} 

