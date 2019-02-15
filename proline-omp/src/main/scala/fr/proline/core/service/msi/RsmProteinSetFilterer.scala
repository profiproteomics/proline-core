package fr.proline.core.service.msi

import javax.persistence.EntityManager

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IProteinSetFilter
import fr.proline.core.algo.msi.validation.ValidationResult
import fr.proline.core.algo.msi.validation.proteinset.BasicProtSetValidator
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.ResultSummaryProperties
import fr.proline.core.om.model.msi.RsmValidationParamsProperties
import fr.proline.core.om.model.msi.RsmValidationProperties
import fr.proline.core.om.model.msi.RsmValidationResultProperties
import fr.proline.core.om.model.msi.RsmValidationResultsProperties
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider

import scala.collection.mutable.ArrayBuffer

object RsmProteinSetFilterer {
  
  def apply(execCtx: IExecutionContext,targetRsmId: Long, protSetFilters: Seq[IProteinSetFilter]  ) : RsmProteinSetFilterer = {
   	  val targetRsm = _loadResultSummary(targetRsmId, execCtx)

   	    // Load decoy result set if needed

	    
    new RsmProteinSetFilterer( execCtx, targetRsm, protSetFilters )

	}
  
  def _loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {
	    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(execContext))
	
	    val rsm = rsmProvider.getResultSummary(rsmId, loadResultSet = true)
	    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)
	    val targetRSM = rsm.get
	    if( (targetRSM.getDecoyResultSummaryId > 0) && (targetRSM.decoyResultSummary == null || targetRSM.decoyResultSummary.isEmpty)){
	    	targetRSM.decoyResultSummary = rsmProvider.getResultSummary(targetRSM.getDecoyResultSummaryId, loadResultSet = true)
	    }
	    targetRSM
	      
  }

}


/**
 * Filter ProteinSet of specified ResultSummary using specified protein ser filters.
 *
 */

class RsmProteinSetFilterer (  
	execCtx: IExecutionContext,
	targetRsm: ResultSummary, 
	protSetFilters: Seq[IProteinSetFilter] 
) extends IService with LazyLogging {
  
   val msiEM: EntityManager = execCtx.getMSIDbConnectionContext.getEntityManager
	
	def runService(): Boolean = {
	  
	  logger.info("ResultSummary filtering service starts")
	  // Get previous validation properties
	  val rsmPrp : RsmValidationProperties = if (targetRsm.properties.isDefined && targetRsm.properties.get.getValidationProperties.isDefined){	   
	      targetRsm.properties.get.getValidationProperties.get
	  }  else {
		  RsmValidationProperties(	  
			  params = RsmValidationParamsProperties(),
			  results = RsmValidationResultsProperties()
		  )	  
	  }	  
	  
	   _filterProteinSets(targetRsm, rsmPrp)
	  	 	  
  	}
	
 private def _filterProteinSets(targetRsm: ResultSummary, rsmValProperties: RsmValidationProperties): Boolean = {
	if (protSetFilters == null || protSetFilters.isEmpty ) 
		return false

    val filterDescriptors = new ArrayBuffer[FilterDescriptor]()
    var finalValidationResult: ValidationResult = null

    // Execute all protein set filters
    protSetFilters.foreach { protSetFilter =>

    	// Apply filter
    	val protSetValidatorForFiltering = new BasicProtSetValidator(protSetFilter)
        val valResults = protSetValidatorForFiltering.validateProteinSets(targetRsm)
        finalValidationResult = valResults.finalResult

        logger.debug(
          "After Filter " + protSetFilter.filterDescription +
            " Nbr protein set target validated = " + finalValidationResult.targetMatchesCount +
            " <> " + targetRsm.proteinSets.count(_.isValidated)
        )

        // Store Validation Params obtained after filtering
        filterDescriptors += protSetFilter.toFilterDescriptor

    }
    //End go through all Prot Filters

    executeOnProgress()


    // Save Protein Set Filters properties
    val existingFilters = rsmValProperties.getParams.getProteinFilters
    val newFilters = new ArrayBuffer[FilterDescriptor]
    if(existingFilters.isDefined){
      newFilters ++= existingFilters.get
    } 
    newFilters++= filterDescriptors
    rsmValProperties.getParams.setProteinFilters(Some(newFilters.toArray))

    // Save final Protein Set Filtering result
    val protSetValResults = if (finalValidationResult == null) {
      logger.debug("Final Validation Result is NULL")

      val targetMatchesCount = targetRsm.proteinSets.count(_.isValidated)
      val decoyMatchesCount = if (targetRsm.decoyResultSummary == null || targetRsm.decoyResultSummary.isEmpty) None
      else Some(targetRsm.decoyResultSummary.get.proteinSets.count(_.isValidated))

      RsmValidationResultProperties(
        targetMatchesCount = targetMatchesCount,
        decoyMatchesCount = decoyMatchesCount
      )
    } else {
      RsmValidationResultProperties(
        targetMatchesCount = finalValidationResult.targetMatchesCount,
        decoyMatchesCount = finalValidationResult.decoyMatchesCount,
        fdr = finalValidationResult.fdr
      )
    }

    // Update Protein Set validation result of the ResultSummary
    rsmValProperties.getResults.setProteinResults(Some(protSetValResults))
    if(targetRsm.properties.isDefined)
      targetRsm.properties.get.setValidationProperties(Some(rsmValProperties))
  	else{
  	  val rsmPrp = new ResultSummaryProperties()
  	  rsmPrp.setValidationProperties(Some(rsmValProperties))
  	  targetRsm.properties = Some(rsmPrp)
  	}
    
    val decoyRsmOpt = if(targetRsm.decoyResultSummary == null) None else targetRsm.decoyResultSummary   
    //VDS: Assume same properties for both decoy and target...
    if(decoyRsmOpt.isDefined ){
      if( decoyRsmOpt.get.properties.isDefined){
    	  decoyRsmOpt.get.properties.get.setValidationProperties(Some(rsmValProperties))
      } else{
    	  val rsmPrp = new ResultSummaryProperties()
    	  rsmPrp.setValidationProperties(Some(rsmValProperties))
    	  decoyRsmOpt.get.properties = Some(rsmPrp)
      }
    }
    
    logger.debug("Final target protein sets count = " + protSetValResults.targetMatchesCount)

    if (protSetValResults.decoyMatchesCount.isDefined)
      logger.debug("Final decoy protein sets count = " + protSetValResults.decoyMatchesCount.get)

    if (protSetValResults.fdr.isDefined)
      logger.debug("Final protein sets FDR = " + protSetValResults.fdr.get)

    //Update RSM properties
    
    val msitargetRSM = msiEM.find(classOf[fr.proline.core.orm.msi.ResultSummary],targetRsm.id)    
    msitargetRSM.setSerializedProperties(ProfiJson.serialize(targetRsm.properties.get))
    msiEM.merge(msitargetRSM)
    
    if(decoyRsmOpt.isDefined){
	    val msiDecoyRSM = msiEM.find(classOf[fr.proline.core.orm.msi.ResultSummary],decoyRsmOpt.get.id)    
	    msiDecoyRSM.setSerializedProperties(ProfiJson.serialize(decoyRsmOpt.get.properties.get))
	    msiEM.merge(msiDecoyRSM)      
    }
    
    
    //Update properties and save in MSI
    // protein sets & peptideInstance   
    val allProteinSets = if (decoyRsmOpt != null && decoyRsmOpt.isDefined) targetRsm.proteinSets ++ decoyRsmOpt.get.proteinSets else targetRsm.proteinSets
	val allPeptideInstances = if (decoyRsmOpt != null && decoyRsmOpt.isDefined) targetRsm.peptideInstances ++ decoyRsmOpt.get.peptideInstances else targetRsm.peptideInstances
	
    for (proteinSet <- allProteinSets) {
      val msiProtSet = msiEM.find(classOf[fr.proline.core.orm.msi.ProteinSet], proteinSet.id)
      msiProtSet.setIsValidated(proteinSet.isValidated)

      if (proteinSet.isValidated) proteinSet.selectionLevel = 2
      else proteinSet.selectionLevel = 1
      msiProtSet.setSelectionLevel(proteinSet.selectionLevel)
      msiEM.merge(msiProtSet)
    }
    
    for (pepInstance <- allPeptideInstances) {
      val msiPepInst = msiEM.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepInstance.id)
      msiPepInst.setProteinMatchCount(pepInstance.proteinMatchesCount)
      msiPepInst.setValidatedProteinSetCount(pepInstance.validatedProteinSetsCount)
      msiPepInst.setProteinSetCount(pepInstance.proteinSetsCount)            
      msiEM.merge(msiPepInst)
    }
    
    return true
  }
}