package fr.proline.core.service.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.TypicalProteinChooserRule
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.algo.msi.TypicalProteinChooser

class RSMTypicalProteinChooser (   
  execCtx: IExecutionContext,
  resultSummaryId: Long,  
  ruleToApply : TypicalProteinChooserRule) extends IService with Logging {

  require(execCtx.isJPA(), " Invalid connexion type for this service ")
  require(resultSummaryId > 0L , "Invalid  ResultSummary Id specified")
  private var modifiedProteinSetsCount = 0
  
  def runService(): Boolean = {
    
	  val msiDbContext = execCtx.getMSIDbConnectionContext()    	
	msiDbContext.beginTransaction()
	val msiEM =msiDbContext.getEntityManager()
	
    logger.info("Run Typical Protein Chooser")
    
    val typicalChooser = new TypicalProteinChooser()	
	typicalChooser.changeTypical(resultSummaryId,ruleToApply, msiEM)
	
	logger.info(" Save data for Typical Protein Chooser")
    	
	val changedPS = typicalChooser.getChangedProteinSets
	modifiedProteinSetsCount = changedPS.size
	changedPS.foreach( msiEM.merge(_))
	
	// Commit transaction if it was initiated locally
    msiDbContext.commitTransaction()
	true
  }
  
  def getChangedProteinSetsCount = {modifiedProteinSetsCount}
  
}