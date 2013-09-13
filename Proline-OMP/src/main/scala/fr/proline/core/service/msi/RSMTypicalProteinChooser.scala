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

  require(execCtx.isJPA())
  
  def runService(): Boolean = {
    logger.info("Load data for Typical Protein Chooser")
    

    val rsmProvider = getResultSummaryProvider(execCtx)
    val rsProvider = getResultSetProvider(execCtx)
    val targetRsmOp = rsmProvider.getResultSummary(resultSummaryId, false) // Bug in link between RSM-RS
    if (targetRsmOp.isEmpty)
      throw new Exception("Unable to access IdentificationResult ")
    val targetRsm = targetRsmOp.get
    val targetRsOp = rsProvider.getResultSet(targetRsm.getResultSetId)
    if (targetRsOp.isEmpty)
      throw new Exception("Unable to access assciated SearchResult ")
    val targetRs = targetRsOp.get

    targetRsm.resultSet = targetRsOp

    //Load proteinMatches in all ProteinSet !!	
    val pmByIds = targetRs.proteinMatchById
    targetRsm.proteinSets.foreach(protSet => {
      var protSetProtMatches = Array.newBuilder[ProteinMatch]
      protSet.getProteinMatchIds.foreach(id => {
        protSetProtMatches += pmByIds(id)
      })
      protSet.proteinMatches = Some(protSetProtMatches.result)
      protSet.setTypicalProteinMatch(pmByIds(protSet.getTypicalProteinMatchId))
    })

    logger.info("Run Typical Protein Chooser")
    
    val typicalChooser = new TypicalProteinChooser()	
	typicalChooser.changeTypical(targetRsm,ruleToApply,false)
	
	
	val msiDbContext = execCtx.getMSIDbConnectionContext()
	// Check if a transaction is already initiated
	val wasInTransaction = msiDbContext.isInTransaction()
	if (!wasInTransaction) msiDbContext.beginTransaction()
      
	val msiEm =msiDbContext.getEntityManager
	msiEm.merge(targetRsm)
	
	// Commit transaction if it was initiated locally
	if (!wasInTransaction) msiDbContext.commitTransaction()

    false
  }
  
  private def getResultSummaryProvider(execContext: IExecutionContext): IResultSummaryProvider = {
  	new SQLResultSummaryProvider(execContext.getMSIDbConnectionContext(), execContext.getPSDbConnectionContext, execContext.getUDSDbConnectionContext)
  }
  
  private def getResultSetProvider(execContext: IExecutionContext): IResultSetProvider = {

    if (execContext.isJPA) {
      new ORMResultSetProvider(execContext.getMSIDbConnectionContext, execContext.getPSDbConnectionContext, execContext.getUDSDbConnectionContext)
    } else {
      new SQLResultSetProvider(execContext.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext],
        execContext.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext],
        execContext.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext])
    }

  }
}