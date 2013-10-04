package fr.proline.core.algo.msi

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.control.Breaks.break
import scala.util.control.Breaks.breakable
import fr.proline.context.IExecutionContext
import fr.proline.util.regex.RegexUtils._
import fr.proline.core.orm.msi.ProteinMatch
import com.weiglewilczek.slf4s.Logging
import javax.persistence.EntityManager

class TypicalProteinChooser () extends Logging {

  private var modifiedProteinSets : Seq[fr.proline.core.orm.msi.ProteinSet] = null
  
  def changeTypical(rsmId: Long, ruleToApply : TypicalProteinChooserRule, msiEM : EntityManager){
   
	logger.info(" Load data for Typical Protein Chooser")
    val ormProtSetRSM = msiEM.createQuery("FROM fr.proline.core.orm.msi.ProteinSet protSet WHERE resultSummary.id = :rsmId", 
    		  	classOf[fr.proline.core.orm.msi.ProteinSet]).setParameter("rsmId",rsmId).getResultList().toList
    
    		  	
    var modifiedProtSet = Seq.newBuilder[fr.proline.core.orm.msi.ProteinSet]
    ormProtSetRSM.foreach(protSet => {
      
    	val associatedProtMatchesById  = protSet.getProteinSetProteinMatchItems().map(pspmi => { pspmi.getProteinMatch().getId() -> pspmi.getProteinMatch()}).toMap
    	var currentTypical =associatedProtMatchesById(protSet.getProteinMatchId())
    	val nbrPepCountSameSet = currentTypical.getPeptideCount()
    	
    	var newTypical = currentTypical
    	    	
    	breakable {
    	  val typValueToTest : String = if(ruleToApply.applyToAcc) currentTypical.getAccession() else currentTypical.getDescription()
    	  if( typValueToTest =~ ruleToApply.rulePattern){
			break
		   }
    		associatedProtMatchesById.foreach(entry  => {
    		  if(entry._2.getPeptideCount().equals(nbrPepCountSameSet)){    		  
    		    val valueToTest : String = if(ruleToApply.applyToAcc) entry._2.getAccession() else entry._2.getDescription()
    			if( valueToTest =~ ruleToApply.rulePattern){
    				newTypical = entry._2
    				break
    			}
    		  }
    		})
    	}
    	
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
