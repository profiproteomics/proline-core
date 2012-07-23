package fr.proline.core.utils.generator

import scala.collection.mutable.ListBuffer
import fr.proline.core.om.model.msi.Protein
import fr.proline.core.om.model.msi.SequenceMatch
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.om.model.msi.Ms2Query

/**
 * Utility class to generate ResultSet with random values
 */
class ResultSetFakeBuilder (val nbPep:Int, val nbProt:Int) {
  
  require(nbPep > 0)  
  require(nbProt > 0 && nbProt <= nbPep)  
  require(nbPep % nbProt == 0)
  
  //Compute number of peptides per group
  val nbPepPerGroup = nbPep/nbProt
  
  var rs:ResultSet = null  
  var allPepMatches = ListBuffer[PeptideMatch]()
  var allProtMatches = ListBuffer[ProteinMatch]()  
  
  var allProts = ListBuffer[Protein]()
  var allProtSequences = Map[String, Int]() 
  var currProtSequence = ""
                    
  var allPeptides = ListBuffer[Peptide]()
  var allPepSequences = ListBuffer[String]() //to check sequence unicity
  var currPepSequence = ""
  var currPepCountInGroup: Int = 0
  
  
  //Loop until # of peptides is reached
  do {   	
	currPepSequence = Randomator.aaSequence()
	val massPep:Double = Peptide.calcMass(currPepSequence)	    
	
	if (!(allPepSequences contains(currPepSequence))) {	//Peptide sequence is unique      
	  allPepSequences += currPepSequence 
	  var currPep = new Peptide( 
			id = Peptide.generateNewId,
	        sequence = currPepSequence,
	        ptms = null,
	        calculatedMass = massPep )
	  allPeptides += currPep
	  		  
	  val charge = Randomator.pepCharge      	  
	  val idq = Ms2Query.generateNewId
	  
      var msq = new Ms2Query(           
          id = idq, 
          initialId = idq,
          moz = getIonMzFromNeutralMass(neutralMass=currPep.calculatedMass, charge=charge),
          charge = charge,
          spectrumTitle = "spectrum "+idq)      
            
	  allPepMatches += new PeptideMatch( 
	        id = PeptideMatch.generateNewId, 
	        rank=1,
	        score = Randomator.matchScore, 
	        scoreType="Mascot", 
	        deltaMoz=0.15, 
	        isDecoy= false,
	        peptide=currPep, 
	        msQuery=msq)    	
	  
	  //This part is for protein sequence creation (just an exact concatenation
	  //of N peptide sequences	 
	  currPepCountInGroup += 1
	  currProtSequence = currProtSequence.concat(currPepSequence)
	  if (currPepCountInGroup == nbPepPerGroup) {
		  allProtSequences += (currProtSequence -> nbPepPerGroup)		 
	      currPepCountInGroup = 0	    
	      currProtSequence = ""
	  } 	  	  	 
	  
	} //end if
  } while(allPeptides.size < nbPep)  //end while 
   
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
          peptide=Option[Peptide](currPep))
      
      
    } //end while pepGroup
    
    //Create a ProteinMatch for each Protein 
    allProtMatches += new ProteinMatch(
          id=ProteinMatch.generateNewId,
	      accession=Randomator.protAccession, 
	      description="Generated accession", 
	      proteinId=currProt.id,
	      sequenceMatches=allSeqMatches.toArray)	
    
  }//end for
  
  rs = new ResultSet(
        //id=ResultSet.generateNewId
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
} 

