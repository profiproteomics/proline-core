package fr.proline.core.algo.msi

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.control.Breaks.break
import scala.util.control.Breaks.breakable
import fr.proline.context.IExecutionContext
import fr.profi.util.regex.RegexUtils._
import fr.proline.core.orm.msi.ProteinMatch
import com.typesafe.scalalogging.slf4j.Logging
import javax.persistence.EntityManager

/**
 * Algo to change the typical protein of all Protein Set for a given ResultSummary.
 * To new selected typical should satisfy the specified TypicalProteinChooserRule :
 * - Pattern matching accession or description
 * 
 * If multiple TypicalProteinChooserRules are specified 
 * 
 */
class TypicalProteinChooser () extends Logging {

  private var modifiedProteinSets : Seq[fr.proline.core.orm.msi.ProteinSet] = null
  
  
  /**
   * Search for a protein in each protein set which response to one of the specified TypicalProteinChooserRule.
   * The rules are ordered by preference this means that a protein which response to rule 1 will be prefered
   * than protein that response to second rule in specified list.
   *   
   */
	def changeTypical(rsmId: Long, priorityRulesToApply : Seq[TypicalProteinChooserRule], msiEM : EntityManager){
   
	logger.info(" Load data for Typical Protein Chooser multi rules")
    val ormProtSetRSM = msiEM.createQuery("FROM fr.proline.core.orm.msi.ProteinSet protSet WHERE resultSummary.id = :rsmId", 
    		  	classOf[fr.proline.core.orm.msi.ProteinSet]).setParameter("rsmId",rsmId).getResultList().toList
    
    		  	
    var modifiedProtSet = Seq.newBuilder[fr.proline.core.orm.msi.ProteinSet]
    ormProtSetRSM.foreach(protSet => {
      
    	val associatedSameSetProtMatchesById  = protSet.getProteinSetProteinMatchItems().filter(!_.getIsInSubset()).map(pspmi => { pspmi.getProteinMatch().getId() -> pspmi.getProteinMatch()}).toMap
    	var currentTypical =associatedSameSetProtMatchesById(protSet.getProteinMatchId())
    	
    	var newTypical = currentTypical
    	var foundNewTypical = false
    	var ruleIndex =0
    	while (ruleIndex < priorityRulesToApply.length && !foundNewTypical) {
    	  val ruleToApply = priorityRulesToApply(ruleIndex)
    	  ruleIndex +=1
    	  breakable {
    		val typicalValueToTest: String = if (ruleToApply.applyToAcc) currentTypical.getAccession() else currentTypical.getDescription()
			if (typicalValueToTest =~ ruleToApply.rulePattern) {
			   foundNewTypical = true // Typical respect current constraint
			   break
			}
    		
    		//Typical doesn't respect current constraint, search in samesets
          	associatedSameSetProtMatchesById.foreach(entry => {
            val valueToTest: String = if (ruleToApply.applyToAcc) entry._2.getAccession() else entry._2.getDescription()
            if (valueToTest =~ ruleToApply.rulePattern) {
              newTypical = entry._2
              foundNewTypical = true // sameset respect current constraint
              break
            }
          }) //End go through sameset 
        }
      } // end go through rules
    	
	  //New typical to save ! 
	  if(!newTypical.equals(currentTypical)){
	  	protSet.setProteinMatchId(newTypical.getId())		
	  	modifiedProtSet += protSet
	  }
    })
    
    modifiedProteinSets = modifiedProtSet.result
    logger.info("Changed "+modifiedProteinSets.size+" typical proteins ")

  }
    
  def getChangedProteinSets = {modifiedProteinSets}
  
}

case class  TypicalProteinChooserRule(ruleName : String, applyToAcc : Boolean, rulePattern : String){  
  
}

