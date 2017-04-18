package fr.proline.core.service.msi

import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.LazyLogging
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.InferenceMethod
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.scoring._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.algo.msi.validation.pepmatch.BasicPepMatchValidator
import fr.proline.core.algo.msi.validation.proteinset.BasicProtSetValidator
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.algo.msi.filtering.proteinset.PeptidesCountPSFilter

// TODO: use this config in the constructors
case class ValidationConfig(
  var tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
  var pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
  var pepMatchValidator: Option[IPeptideMatchValidator] = None,
  var pepSetScoring: Option[PepSetScoring.Value] = None,
  var protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  var protSetValidator: Option[IProteinSetValidator] = None
)

object ResultSetValidator {

  def apply(
    execContext: IExecutionContext,
    targetRsId: Long,
    tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
    pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
    pepMatchValidator: Option[IPeptideMatchValidator] = None,
    protSetFilters: Option[Seq[IProteinSetFilter]] = None,
    protSetValidator: Option[IProteinSetValidator] = None,
    inferenceMethod: Option[InferenceMethod.Value] = Some(InferenceMethod.PARSIMONIOUS),
    peptideSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
    storeResultSummary: Boolean = true
  ): ResultSetValidator = {

    val rsProvider = getResultSetProvider(execContext)
    val targetRs = rsProvider.getResultSet(targetRsId)

    if (targetRs.isEmpty) {
      throw new IllegalArgumentException("Unknown ResultSet Id: " + targetRsId)
    }

    // Load decoy result set if needed
    if (execContext.isJPA == false) {
      val decoyRsId = targetRs.get.getDecoyResultSetId
      if (decoyRsId > 0) {
        targetRs.get.decoyResultSet = rsProvider.getResultSet(decoyRsId)
      }
    }

    
    new ResultSetValidator(
      execContext,
      targetRs.get,
      tdAnalyzer,
      pepMatchPreFilters,
      pepMatchValidator,
      protSetFilters,
      protSetValidator,
      inferenceMethod,
      peptideSetScoring,
      storeResultSummary
    )
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSetProvider(execContext: IExecutionContext): IResultSetProvider = {

    if (execContext.isJPA) {
      new ORMResultSetProvider(execContext.getMSIDbConnectionContext, execContext.getPSDbConnectionContext, execContext.getUDSDbConnectionContext)
    } else {
      new SQLResultSetProvider(
        execContext.getMSIDbConnectionContext,
        execContext.getPSDbConnectionContext,
        execContext.getUDSDbConnectionContext
      )
    }

  }

}

/**
 * Validate a ResultSet to create an associated ResultSummary.
 * This validation will be executed in multiple steps :
 * - Validate PSM : using eventually decoy information. Multiple Filters could be used (constant filter
 * or computed expected FDR filter)
 * - ProteinSetInferer : create protein set base on validated PSM
 * - Validate Protein Set : Multiple Filters could be used
 *
 */
class ResultSetValidator(
  execContext: IExecutionContext,
  targetRs: ResultSet,
  tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
  pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
  pepMatchValidator: Option[IPeptideMatchValidator] = None,
  protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  protSetValidator: Option[IProteinSetValidator] = None,
  inferenceMethod: Option[InferenceMethod.Value] = Some(InferenceMethod.PARSIMONIOUS),
  peptideSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
  storeResultSummary: Boolean = true
) extends IService with LazyLogging {

  private val msiDbContext = execContext.getMSIDbConnectionContext
  var validatedTargetRsm: ResultSummary = null
  var validatedDecoyRsm: Option[ResultSummary] = null
  
  // WARNING: this is hack which enables TD competition when rank filtering is used
  // FIXME: find a better way to handle the TD competition
  var useTdCompetition = false
  if( pepMatchPreFilters.isDefined ) {
    val rankFilterAsStr = PepMatchFilterParams.RANK.toString
    useTdCompetition = pepMatchPreFilters.get.exists(_.filterParameter == rankFilterAsStr)
    logger.debug(s"Auto-determination of 'useTdCompetition' gives '$useTdCompetition'")
  }
  
  // If no TDAnalyzer specified, specify default one
  val finalTDAnalyzer = if(tdAnalyzer.isEmpty) BuildTDAnalyzer(useTdCompetition, targetRs, null)
  else {
    // Update useTdCompetition (see above hack)
    tdAnalyzer.get.useTdCompetition = useTdCompetition
    tdAnalyzer
  }
    
  // Update the target analyzer of the validators
  pepMatchValidator.foreach(_.tdAnalyzer = finalTDAnalyzer)

  override protected def beforeInterruption = {
  }

  def curTimeInSecs() = System.currentTimeMillis() / 1000

  def runService(): Boolean = {

    logger.info("ResultSet validator service starts")

    val startTime = curTimeInSecs()

    // --- Create RSM validation properties ---
    val rsmValProperties = RsmValidationProperties(
      params = RsmValidationParamsProperties(),
      results = RsmValidationResultsProperties()
    )
    executeOnProgress() //execute registered action during progress

    // --- Validate PSM ---
    val( appliedPSMFilters, pepMatchValidationRocCurveOpt ) = this._validatePeptideMatches(targetRs, rsmValProperties)
    executeOnProgress() //execute registered action during progress

    // --- Compute RSM from validated PSMs ---

    // Instantiate a protein set inferer
    val protSetInferer = ProteinSetInferer(inferenceMethod.get)

    //-- createRSM
    def createRSM(currentRS: Option[ResultSet], peptideValidationRocCurve: Option[MsiRocCurve]): Option[ResultSummary] = {
      if (currentRS.isDefined) {

        // Create new result set with validated peptide matches and compute result summary
        val rsm = protSetInferer.computeResultSummary(currentRS.get, keepSubsummableSubsets = true)
        
        // Attach the ROC curve
        rsm.peptideValidationRocCurve = peptideValidationRocCurve

        // Update score of protein sets
        val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring.get)
        this.logger.debug("updating score of peptide sets using " + peptideSetScoring.get.toString)
        pepSetScoreUpdater.updateScoreOfPeptideSets(rsm)

        Some(rsm)
      } else {
        Option.empty[ResultSummary]
      }
    }

    // Build result summary for each individual result set
    val targetRsm = createRSM(Some(targetRs), pepMatchValidationRocCurveOpt).get
    val decoyRsmOpt = createRSM(if (targetRs.decoyResultSet != null) targetRs.decoyResultSet else None, None)
    executeOnProgress() //execute registered action during progress

    // Set target RSM validation properties
    targetRsm.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))
    if (decoyRsmOpt.isDefined) {
      // Link decoy RSM to target RSM
      targetRsm.decoyResultSummary = decoyRsmOpt

      // Set decoy RSM validation properties
      decoyRsmOpt.get.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))
    }

    // --- Validate ProteinSet ---
    targetRsm.proteinValidationRocCurve = this._validateProteinSets(targetRsm, rsmValProperties)

    val took = curTimeInSecs() - startTime
    this.logger.info("Validation service took " + took + " seconds")
  
    // Instantiate totalLeavesMatchCount 
    this.logger.debug("updatePepInstanceSC for new validated result summaries ...")
    val rsms2Update = if(decoyRsmOpt.isDefined) Seq(targetRsm, decoyRsmOpt.get) else Seq(targetRsm)
    
    val pepInstanceFilteringLeafSCUpdater= new PepInstanceFilteringLeafSCUpdater()
    pepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsms2Update, execContext)
      
    if (storeResultSummary) {

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbContext.isInTransaction()
      if (!wasInTransaction) msiDbContext.beginTransaction()

      // Instantiate a RSM storer
      val rsmStorer = RsmStorer(msiDbContext)

      // Store decoy result summary
      if (decoyRsmOpt.isDefined) {
        rsmStorer.storeResultSummary(decoyRsmOpt.get, execContext)
      }

      // Store target result summary
      rsmStorer.storeResultSummary(targetRsm, execContext)
      executeOnProgress() //execute registered action during progress
      
          
      // Commit transaction if it was initiated locally
      if (!wasInTransaction) msiDbContext.commitTransaction()

      this.logger.info("ResultSummary successfully stored !")
    }

    // Update the service results
    this.validatedTargetRsm = targetRsm
    this.validatedDecoyRsm = decoyRsmOpt

    this.beforeInterruption()

    true
  }

  /**
   * Validate PSM using filters specified in service and PSM validator specified is service.
   * PeptideMatches are set as isValidated or not by each filter and a FDR is calculated after each filter.
   *
   * All applied IPeptideMatchFilter are returned
   */
  private def _validatePeptideMatches(targetRs: ResultSet, rsmValProperties: RsmValidationProperties): (Seq[IPeptideMatchFilter],Option[MsiRocCurve]) = {

    var appliedFilters = Seq.newBuilder[IPeptideMatchFilter]
    val filterDescriptors = new ArrayBuffer[FilterDescriptor](pepMatchPreFilters.map(_.length).getOrElse(0))
    var finalValidationResult: ValidationResult = null

    // Execute all peptide matches pre filters
    //VDS TEST: Execute some filter - singlePerQuery- After FDR !
    val postValidationFilter : ArrayBuffer[IPeptideMatchFilter] = new ArrayBuffer()
    
    if (pepMatchPreFilters.isDefined) {
      pepMatchPreFilters.get.foreach { psmFilter =>
        
      	if(psmFilter.isInstanceOf[IFilterNeedingResultSet])
      		psmFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(targetRs)

        if (psmFilter.postValidationFilter) postValidationFilter += psmFilter
        else {
          finalValidationResult = new BasicPepMatchValidator(psmFilter, finalTDAnalyzer).validatePeptideMatches(targetRs).finalResult
          logger.debug(
            "After Filter " + psmFilter.filterDescription +
              " Nbr PepMatch target validated = " + finalValidationResult.targetMatchesCount
          )
          appliedFilters += psmFilter
          filterDescriptors += psmFilter.toFilterDescriptor
        }
          
      }
    } //End execute all PSM filters
    executeOnProgress() //execute registered action during progress

    // If define, execute peptide match validator
    val rocCurveOpt = if( pepMatchValidator.isEmpty) None
    else {
      val somePepMatchValidator = pepMatchValidator.get
      val validationFilter = somePepMatchValidator.validationFilter

      if (validationFilter.isInstanceOf[IFilterNeedingResultSet])
        validationFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(targetRs)

      logger.debug("Run peptide match validator: " + validationFilter.filterParameter)

      // Apply Filter
      val valResults = somePepMatchValidator.validatePeptideMatches(targetRs)
      
      if (valResults.finalResult != null) {
        finalValidationResult = valResults.finalResult

        appliedFilters += validationFilter
        // Store Validation Params obtained after filtering
        filterDescriptors += validationFilter.toFilterDescriptor
        
        // Retrieve the ROC curve
        valResults.getRocCurve()
        
      } else None
    }

    //VDS: FOR TEST ONLY : EXECUTE some filter - singlePerQuery- After FDR !
    postValidationFilter.foreach(psmFilter => {
      finalValidationResult = new BasicPepMatchValidator(psmFilter, finalTDAnalyzer).validatePeptideMatches(targetRs).finalResult
      logger.debug(
        "After Post Validation Filter " + psmFilter.filterDescription +
          " Nbr PepMatch target validated = " + finalValidationResult.targetMatchesCount
      )
      appliedFilters += psmFilter
      filterDescriptors += psmFilter.toFilterDescriptor
    })
    
    // Save PSM Filters properties
    val expectedFdr = if (pepMatchValidator.isDefined) pepMatchValidator.get.expectedFdr else None
    rsmValProperties.getParams.setPeptideExpectedFdr(expectedFdr)
    rsmValProperties.getParams.setPeptideFilters(Some(filterDescriptors.toArray))

    // Save final PSM Filtering result
    val pepValResults = if (finalValidationResult != null) {
      RsmValidationResultProperties(
        targetMatchesCount = finalValidationResult.targetMatchesCount,
        decoyMatchesCount = finalValidationResult.decoyMatchesCount,
        fdr = finalValidationResult.fdr
      )
    } else {

      // If finalValidationResult => count validated PSMs
      val targetMatchesCount = targetRs.peptideMatches.count(_.isValidated)
      val decoyMatchesCount = if (targetRs.decoyResultSet.isEmpty) None
      else Some(targetRs.decoyResultSet.get.peptideMatches.count(_.isValidated))

      RsmValidationResultProperties(
        targetMatchesCount = targetMatchesCount,
        decoyMatchesCount = decoyMatchesCount
      )
    }

    // Update PSM validation result of the ResultSummary
    rsmValProperties.getResults.setPeptideResults(Some(pepValResults))

    (appliedFilters.result, rocCurveOpt)
  }

  private def _validateProteinSets(targetRsm: ResultSummary, rsmValProperties: RsmValidationProperties): Option[MsiRocCurve] = {
//    if (protSetFilters.isEmpty && protSetValidator.isEmpty) return None

    val filterDescriptors = new ArrayBuffer[FilterDescriptor]()
    var finalValidationResult: ValidationResult = null

    // Execute all protein set filters
    if (protSetFilters.isDefined) {
      protSetFilters.get.foreach { protSetFilter =>

        // Apply filter
        val protSetValidatorForFiltering = new BasicProtSetValidator(protSetFilter)
        val valResults = protSetValidatorForFiltering.validateProteinSets(targetRsm)
        finalValidationResult = valResults.finalResult

        logger.debug(
          "After Filter " + protSetFilter.filterDescription +
            " Nbr protein set target validated = " + finalValidationResult.targetMatchesCount +
            " <> " + targetRsm.proteinSets.filter(_.isValidated).length
        )

        // Store Validation Params obtained after filtering
        filterDescriptors += protSetFilter.toFilterDescriptor

      }
    } //End go through all Prot Filters

    executeOnProgress() //execute registered action during progress

    
    val tdModeOpt = if(targetRsm.resultSet.get.properties.isDefined && targetRsm.resultSet.get.properties.get.targetDecoyMode.isDefined){
      val tdModeStr = targetRsm.resultSet.get.properties.get.targetDecoyMode.get  
      Some(TargetDecoyModes.withName(tdModeStr))
    } else None

    // If define, execute protein set validator  
    val rocCurveOpt = if (protSetValidator.isEmpty) None
    else {

      logger.debug("Run protein set validator: " + protSetValidator.get.toFilterDescriptor.parameter)

      // Update the target/decoy mode of the protein set validator for this RSM
      protSetValidator.get.targetDecoyMode = tdModeOpt

      val valResults = protSetValidator.get.validateProteinSets(targetRsm)
      finalValidationResult = valResults.finalResult

      filterDescriptors += protSetValidator.get.toFilterDescriptor
      
      valResults.getRocCurve()
    }

    // Save Protein Set Filters properties
    val expectedFdr = if (protSetValidator.isDefined) protSetValidator.get.expectedFdr else None
    rsmValProperties.getParams.setProteinExpectedFdr(expectedFdr)
    rsmValProperties.getParams.setProteinFilters(Some(filterDescriptors.toArray))

    // Save final Protein Set Filtering result
    val protSetValResults = if (finalValidationResult == null) {
      logger.debug("Final Validation Result is NULL")

      val targetMatchesCount = targetRsm.proteinSets.count(_.isValidated)
      val decoyMatchesCount = if (targetRsm.decoyResultSummary == null || targetRsm.decoyResultSummary.isEmpty) None
      else Some(targetRsm.decoyResultSummary.get.proteinSets.count(_.isValidated))

      //Compute FDR 
      var fdr : Option[Float] = None
      if(decoyMatchesCount.isDefined && decoyMatchesCount.get > 0 && tdModeOpt.isDefined){             
        tdModeOpt.get match {
          case TargetDecoyModes.CONCATENATED => { fdr = Some(TargetDecoyComputer.calcCdFDR(targetMatchesCount, decoyMatchesCount.get)) }
          case TargetDecoyModes.SEPARATED    => { fdr = Some(TargetDecoyComputer.calcSdFDR(targetMatchesCount, decoyMatchesCount.get)) }    
        }
      }
      
      RsmValidationResultProperties(
        targetMatchesCount = targetMatchesCount,
        decoyMatchesCount = decoyMatchesCount,
        fdr = fdr
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

    logger.debug("Final target protein sets count = " + protSetValResults.targetMatchesCount)

    if (protSetValResults.decoyMatchesCount.isDefined)
      logger.debug("Final decoy protein sets count = " + protSetValResults.decoyMatchesCount.get)

    if (protSetValResults.fdr.isDefined)
      logger.debug("Final protein sets FDR = " + protSetValResults.fdr.get)

    // Select only validated protein sets
    val decoyRsmOpt = targetRsm.decoyResultSummary
    val allProteinSets = if (decoyRsmOpt != null && decoyRsmOpt.isDefined) targetRsm.proteinSets ++ decoyRsmOpt.get.proteinSets else targetRsm.proteinSets

    for (proteinSet <- allProteinSets) {
      if (proteinSet.isValidated) proteinSet.selectionLevel = 2
      else proteinSet.selectionLevel = 1
    }
    
    rocCurveOpt
  }
}