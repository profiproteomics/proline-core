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
 * @param deltaPepNb Delta for number of peptides matching a given protein
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
  private var currPepInProtNb: Int = 0
  private val nbProtPairs:Int = proNb/2
  private val needToAdjustLastProt:Boolean = (proNb % 2) != 0
  private var remainPepNb:Int = pepNb //For the last protein
  private var currPairNb:Int = 0
  
  private var allProtSequences = Map[String, Int]() 
  private var currProtSequence = ""
                     
  private var allPepSequences = ListBuffer[String]() //to check sequence unicity
  private var currPepSequence = ""
    
  private var isNewProt:Boolean = true
  private var evenProt:Boolean = true
    
  
  var rs:ResultSet = null  
  var allPepMatches:ListBuffer[PeptideMatch] = ListBuffer[PeptideMatch]()
  var allProtMatches:ListBuffer[ProteinMatch] = ListBuffer[ProteinMatch]()      
  var allProts:ListBuffer[Protein] = ListBuffer[Protein]()
  var allPeptides = ListBuffer[Peptide]()
    
  logger.debug("Building a ResultSet having "+pepNb+" peptides and "+proNb+" proteins.")
  if (deltaPepNb == 0) logger.debug(avgNbPepPerGroup+" peptides match on each protein.")
    else logger.debug(avgNbPepPerGroup+" peptides +/-"+deltaPepNb+" match on each protein.")
  
  val resultSetId:Int = ResultSet.generateNewId  
    
  //Generate peptides & match on Ms2Query
  do {   	
	currPepSequence = Randomator.aaSequence(pepSeqLengthMin, pepSeqLengthMax)
	val massPep:Double = Peptide.calcMass(currPepSequence)	    
	
	if (!(allPepSequences contains(currPepSequence))) {	//Peptide sequence is unique      
	  allPepSequences += currPepSequence 
	  val currPep = new Peptide( 
			id = Peptide.generateNewId,
	        sequence = currPepSequence,
	        ptms = null,
	        calculatedMass = massPep )
	  allPeptides += currPep
	  		  
	  val charge = Randomator.pepCharge      	  
	  val idq = Ms2Query.generateNewId
	  
      val msq = new Ms2Query(           
          id = idq, 
          initialId = idq,
          moz = getIonMzFromNeutralMass(neutralMass=currPep.calculatedMass, charge=charge),
          charge = charge,
          spectrumTitle = "generated spectrum "+idq)      
            
	  allPepMatches += new PeptideMatch( 
	        id = PeptideMatch.generateNewId, 
	        rank=1,
	        score = Randomator.matchScore, 
	        scoreType="Mascot", 
	        deltaMoz=0.15, 
	        isDecoy= false,
	        peptide=currPep, 
	        msQuery=msq,
	        resultSetId=resultSetId
	        )    	
	  
	  //This part is for protein sequence creation (just an exact concatenation
	  //of N peptide sequences	 
	  //Define # of peptides matching on the current protein
	  currPepInProtNb += 1	  
	  currProtSequence = currProtSequence.concat(currPepSequence)
	  if (isNewProt) {		    
		if (deltaPepNb > 0) { //Varying # of peptides matching on a protein 		    
		  if ((currPairNb == nbProtPairs) && needToAdjustLastProt) {//Last protein to readjust
		    currDelta = 0
		    currExpectedPepInProtNb = remainPepNb							
		  } else if (evenProt) { //evenProt		    	
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
	  	  
	  if (currPepInProtNb == currExpectedPepInProtNb) {		    
		  allProtSequences += (currProtSequence -> currExpectedPepInProtNb)		 
	      currPepInProtNb = 0	    
	      currProtSequence = ""
	      remainPepNb -= currExpectedPepInProtNb  
	      isNewProt = true
	  } 	  	  	 
	  
	} //end if
  } while(allPeptides.size < pepNb)  //end while 
   
  //Create proteins
  var currPepCount:Int = 0 //Current Peptide # in a protein group
  var currProtCount:Int = 0 //Current Protein #
  
  val pepIt = allPeptides.iterator
    
  for ((currProtSequence,currNbPepPerGroup) <- allProtSequences) {  
    var currProt = new Protein(
    	sequence=currProtSequence, 
    	id=Protein.generateNewId)
	allProts += currProt
	  	
    var allSeqMatches = ListBuffer[SequenceMatch]()
        
    //Loop through N peptides for this group
    val pepGroupIt = pepIt.take(currNbPepPerGroup)
    while (pepGroupIt.hasNext) {
      
      //For each peptide of the group, create a peptide match
      var currPep = pepGroupIt.next
     
      val startIdx: Int = currProtSequence.indexOf(currPep.sequence) 
      val endIdx: Int = startIdx + currPep.sequence.length -1
      val resBefore: Char = currProtSequence.charAt(if (startIdx>0)startIdx-1 else 0)
      val resAfter: Char = currProtSequence.charAt(if (endIdx == currProtSequence.length-1) endIdx else endIdx+1)
      
      val startPos = startIdx+1
      val endPos = endIdx+1                                                                                            
      
      allSeqMatches += new SequenceMatch(
          start=startPos, 
          end=endPos, 
          residueBefore=resBefore, 
          residueAfter=resAfter,
          peptide=Option[Peptide](currPep),
          resultSetId=resultSetId)
      
      
    } //end while pepGroup        
    
    //Create a ProteinMatch for each Protein 
    allProtMatches += new ProteinMatch(
          id=ProteinMatch.generateNewId,
	      accession=Randomator.protAccession, 
	      description="Generated accession", 
	      proteinId=currProt.id,
	      protein=Option(currProt),
	      peptideMatchesCount=currNbPepPerGroup, //?
	      sequenceMatches=allSeqMatches.toArray,
	      resultSetId=resultSetId)	
    
  }//end for
  
  rs = new ResultSet(
        id=resultSetId,
        peptides=allPeptides.toArray, 
        peptideMatches=allPepMatches.toArray, 
        proteinMatches=allProtMatches.toArray, 
        isDecoy=false, 
        isNative=true)    
      
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
  	  require(rs != null)
  	  
  	  logger.info("ResultSet id:"+rs.id+", name: "+rs.name+", description: "+rs.description+", isQuantified: "+rs.isQuantified)    	  
  	  for (prot <- rs.getProteins.get) {
  	    logger.info("=> Protein "+prot.id+" "+prot.sequence)
  	    logger.info("Length: "+prot.length+", mass: "+prot.mass+", pi: "+prot.pi+", alphabet: "+prot.alphabet+", crc64: "+prot.crc64)  	      	  	   
  	  }
  		
  	  rs.peptides
  	  for (pm <- rs.proteinMatches) {
  	    logger.info("ProteinMatch id: "+pm.id.toString+", accession: "+pm.accession+", peptide match count: "+pm.peptideMatchesCount)
  	    
  	  }
  	    	  
  	}
} 

