package fr.proline.core.algo.msi

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ProteinSet
import scala.util.control.Breaks.break
import scala.util.control.Breaks.breakable
import fr.proline.util.regex.RegexUtils._

class TypicalProteinChooser () {

  def changeTypical(rsm: ResultSummary, ruleToApply : TypicalProteinChooserRule, applyToHierarchy : Boolean){
        
    rsm.proteinSets.foreach(protSet => {
    	if( protSet.getTypicalProteinMatch.isEmpty)
    		throw new Exception("Unable to acess tp proteinMatches ")
      
    	var currentTypical = protSet.getTypicalProteinMatch.get      
    	var newTypical = currentTypical
    	
    	val protMatchesList = if(protSet.proteinMatches.isEmpty) throw new Exception("Unable to acess tp proteinMatches ") else protSet.proteinMatches.get
    	val sameSetPMIds =  protSet.peptideSet.proteinMatchIds
    	breakable {
    		protMatchesList.foreach(protMatch => {
    		  if(sameSetPMIds.contains(protMatch.id)){
    		    val valueToTest : String = if(ruleToApply.applyToAcc) protMatch.accession else protMatch.description
    			if( valueToTest =~ ruleToApply.rulePattern){
    				newTypical = protMatch
    				break
    			}
    		  }
    		})
    	}
    	
    	//New typical to save !
		if(!newTypical.equals(currentTypical)){
		  protSet.setTypicalProteinMatch (newTypical)		  
		}
    })
   
  }
  
  
}

case class  TypicalProteinChooserRule(ruleName : String, applyToAcc : Boolean, rulePattern : String){  
  
}

