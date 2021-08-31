package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.{InferenceMethod, ProteinSetInferer}
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.scoring.{PepSetScoring, PeptideSetScoreUpdater}
import fr.proline.core.algo.msi.validation.pepmatch.TDPepMatchValidator
import fr.proline.core.algo.msi.validation.proteinset.BasicProtSetValidator
import fr.proline.core.algo.msi.validation.{IPeptideInstanceBuilder, _}
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.{ORMResultSetProvider, SQLResultSetProvider}
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.service.msi.ResultSetValidator.{getResultSetProvider, loadDecoyRS}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class ValidationConfig(
  var tdAnalyzerBuilder: Option[TDAnalyzerBuilder] = None,
  var pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
  var pepMatchValidator: Option[IPeptideMatchValidator] = None,
  var peptideBuilder: IPeptideInstanceBuilder = BuildPeptideInstanceBuilder(PeptideInstanceBuilders.STANDARD),
  var peptideFilters: Option[Seq[IPeptideInstanceFilter]] = None,
  var peptideValidator: Option[IPeptideInstanceValidator] = None,
  var pepSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
  var protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  var protSetValidator: Option[IProteinSetValidator] = None
)

object ResultSetValidator extends LazyLogging {

  def apply(
             execContext: IExecutionContext,
             targetRsId: Long,
             validationConfig: ValidationConfig,
             inferenceMethod: Option[InferenceMethod.Value] = Some(InferenceMethod.PARSIMONIOUS),
             storeResultSummary: Boolean = true,
             propagatePepMatchValidation: Boolean = false,
             propagateProtSetValidation: Boolean = false
  ): ResultSetValidator = {

    val rsProvider = getResultSetProvider(execContext)
    val targetRs = rsProvider.getResultSet(targetRsId)
    if (targetRs.isEmpty) {
      throw new IllegalArgumentException("Unknown ResultSet Id: " + targetRsId)
    }

    this.apply(execContext, targetRs.get, validationConfig, inferenceMethod, storeResultSummary, propagatePepMatchValidation, propagateProtSetValidation)
  }

  def apply(
      execContext: IExecutionContext,
      targetRs: ResultSet,
      validationConfig: ValidationConfig,
      inferenceMethod: Option[InferenceMethod.Value],
      storeResultSummary: Boolean,
      propagatePepMatchValidation: Boolean,
      propagateProtSetValidation: Boolean
  ): ResultSetValidator = {


    loadDecoyRS(execContext, targetRs)

    ResultSetValidator.fixTDComputer(validationConfig)
    val tdAnalyzer = if(validationConfig.tdAnalyzerBuilder.isDefined) { validationConfig.tdAnalyzerBuilder.get.build(Some(targetRs)) } else { None }

    new ResultSetValidator(
      execContext,
      targetRs,
      tdAnalyzer,
      validationConfig,
      inferenceMethod,
      storeResultSummary,
      propagatePepMatchValidation,
      propagateProtSetValidation
    )
  }

  private def loadDecoyRS(execContext: IExecutionContext, targetRs: ResultSet) = {
    // Load decoy result set if needed
    if (!execContext.isJPA) {
      val decoyRsId = targetRs.getDecoyResultSetId
      if ((decoyRsId > 0) && !targetRs.decoyResultSet.isDefined) {
        val rsProvider = getResultSetProvider(execContext)
        targetRs.decoyResultSet = rsProvider.getResultSet(decoyRsId)
      }
    }
  }

  private def fixTDComputer( validationConfig: ValidationConfig ) = {

    // WARNING: this is hack which enables TD competition when rank filtering is used
    // FIXME: find a better way to handle the TD competition
    if (validationConfig.pepMatchPreFilters.isDefined && validationConfig.tdAnalyzerBuilder.isDefined) {
      val rankFilterAsStr = PepMatchFilterParams.PRETTY_RANK.toString
      if (validationConfig.pepMatchPreFilters.get.exists(_.filterParameter == rankFilterAsStr)) {
        logger.debug("If TD Analyzer is BASIC, force TD estimator to GIGY (Concatenated) since a RANK filter is used")
        validationConfig.tdAnalyzerBuilder.get.estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER)
      }
    }

  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSetProvider(execContext: IExecutionContext): IResultSetProvider = {

    if (execContext.isJPA) {
      new ORMResultSetProvider(execContext.getMSIDbConnectionContext)
    } else {
      new SQLResultSetProvider(PeptideCacheExecutionContext(execContext))
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
class ResultSetValidator protected(
                          execContext: IExecutionContext,
                          targetRs: ResultSet,
                          tdAnalyzer: Option[ITargetDecoyAnalyzer],
                          validationConfig: ValidationConfig,
                          inferenceMethod: Option[InferenceMethod.Value] = Some(InferenceMethod.PARSIMONIOUS),
                          storeResultSummary: Boolean = true,
                          propagatePepMatchValidation: Boolean = false,
                          propagateProtSetValidation: Boolean = false

) extends IService with LazyLogging {

  private val msiDbContext = execContext.getMSIDbConnectionContext
  var validatedTargetRsm: ResultSummary = _
  var validatedDecoyRsm: Option[ResultSummary] = _

  var targetRSMIdPerRsId : mutable.HashMap[Long, Long] = new mutable.HashMap[Long, Long]()


  override protected def beforeInterruption: Unit = {}

  def curTimeInSecs(): Long = System.currentTimeMillis() / 1000

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
    val protSetInferer = ProteinSetInferer(inferenceMethod.get, validationConfig.peptideBuilder)

    //-- createRSM
    def createRSM(currentRS: Option[ResultSet], peptideValidationRocCurve: Option[MsiRocCurve]): Option[ResultSummary] = {
      if (currentRS.isDefined) {

        // compute result summary from specified result set (validated peptide matches )
        val rsm = protSetInferer.computeResultSummary(currentRS.get, keepSubsummableSubsets = true, peptideInstanceFilteringFunction = Some(_validatePeptideInstances))
        val validatorDescriptor = if (validationConfig.peptideValidator.isDefined) {
          Some(validationConfig.peptideValidator.get.toValidatorDescriptor())
        } else {
          None
        }
        rsmValProperties.getParams.setPeptideValidator(validatorDescriptor)

        // Describe used filters
        if (validationConfig.peptideFilters.isDefined) {
          val filterDescriptors = validationConfig.peptideFilters.get.map(_.toFilterDescriptor())
          rsmValProperties.getParams.setPeptideFilters(Some(filterDescriptors.toArray))
        }

        // Attach the ROC curve
        rsm.peptideValidationRocCurve = peptideValidationRocCurve

        // Update score of protein sets
        val pepSetScoreUpdater = PeptideSetScoreUpdater(validationConfig.pepSetScoring.get)
        this.logger.debug("updating score of peptide sets using " + validationConfig.pepSetScoring.get.toString)
        rsmValProperties.getParams.setProteinScoring(Some(validationConfig.pepSetScoring.get.toString))
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

    //-- Propagate Validation if asked
    if (propagatePepMatchValidation || propagateProtSetValidation) {
      appliedPSMFilters.foreach( _.setPropagateMode(true)) // Specify set Propagation mode
      val propagatedPSMFilters = if (propagatePepMatchValidation) Some(appliedPSMFilters) else None
      val propagatedProtSetFilters = if (propagateProtSetValidation) validationConfig.protSetFilters else None
      _propagateToChilds(propagatedPSMFilters, propagatedProtSetFilters, targetRs.id)
    }

    // Instantiate totalLeavesMatchCount
    this.logger.debug("updatePepInstanceSC for new validated result summaries ...")
    val rsms2Update = if (decoyRsmOpt.isDefined) Seq(targetRsm, decoyRsmOpt.get) else Seq(targetRsm)

    val pepInstanceFilteringLeafSCUpdater= new PepInstanceFilteringLeafSCUpdater()
    pepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsms2Update, execContext)

    if (storeResultSummary) {

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbContext.isInTransaction
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
    targetRSMIdPerRsId += (targetRs.id -> targetRsm.id)

    this.beforeInterruption()

    true
  }

  private def _propagateToChilds(propagatedPSMFilters: Option[Seq[IPeptideMatchFilter]], propagatedProtSetFilters: Option[Seq[IProteinSetFilter]], parentId: Long) {
    val childRsId = _getChildRS(targetRs.id)
    logger.debug(  " --  _propagateToChilds for parentId " + parentId+"; nbr child "+childRsId.length)

    val rsProvider = getResultSetProvider(execContext)

    val propagatedValidationConfig = ValidationConfig(
      tdAnalyzerBuilder = None,
      pepMatchPreFilters = propagatedPSMFilters,
      pepMatchValidator = None,
      pepSetScoring = validationConfig.pepSetScoring,
      protSetFilters = propagatedProtSetFilters,
      protSetValidator = None)

    childRsId.foreach(rsId => {
      logger.debug(  " ---- propagateToChild RS Id  "+rsId)
      val targetRs = rsProvider.getResultSet(rsId)
      loadDecoyRS(execContext, targetRs.get)

      val recursiveRSValidator = new ResultSetValidator(
          execContext,
          targetRs.get,
          tdAnalyzer,
          propagatedValidationConfig,
          inferenceMethod,
          storeResultSummary,
          propagatePepMatchValidation,
          propagateProtSetValidation)
      recursiveRSValidator.run()
      targetRSMIdPerRsId ++= recursiveRSValidator.targetRSMIdPerRsId
    })
  }

  private def _getChildRS(rsId: Long): Array[Long] = {

    val rsRelationQB = new SelectQueryBuilder1(MsiDbResultSetRelationTable)

    // WAS "select child_result_set_id from result_set_relation where result_set_relation.parent_result_set_id = ?"
    DoJDBCReturningWork.withEzDBC(execContext.getMSIDbConnectionContext) { ezDBC =>
      ezDBC.selectLongs(rsRelationQB.mkSelectQuery((t, cols) =>
        List(t.CHILD_RESULT_SET_ID) -> " WHERE " ~ t.PARENT_RESULT_SET_ID ~ " = " ~ rsId))
    }
  }
  
  /**
   * Validate PSM using filters specified in service and PSM validator specified is service.
   * PeptideMatches are set as isValidated or not by each filter and a FDR is calculated after each filter.
   *
   * All applied IPeptideMatchFilter are returned
   */
  private def _validatePeptideMatches(targetRs: ResultSet, rsmValProperties: RsmValidationProperties): (Seq[IPeptideMatchFilter],Option[MsiRocCurve]) = {

    var appliedFilters = Seq.newBuilder[IPeptideMatchFilter]
    val filterDescriptors = new ArrayBuffer[FilterDescriptor](validationConfig.pepMatchPreFilters.map(_.length).getOrElse(0))
    var finalValidationResult: ValidationResult = null

    // Execute all peptide matches pre-filters

    if (validationConfig.pepMatchPreFilters.isDefined) {
      validationConfig.pepMatchPreFilters.get.foreach { psmFilter =>
      	if(psmFilter.isInstanceOf[IFilterNeedingResultSet])
          psmFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(targetRs)
          finalValidationResult = new TDPepMatchValidator(psmFilter).validatePeptideMatches(targetRs, tdAnalyzer).finalResult
          logger.debug(  "After Filter " + psmFilter.filterDescription + " Nbr PepMatch target validated = " + finalValidationResult.targetMatchesCount)
          appliedFilters += psmFilter
          filterDescriptors += psmFilter.toFilterDescriptor
      } // End foreach filter
    } //End execute all PSM filters

    executeOnProgress() //execute registered action during progress

    // If define, execute peptide match validator
    val rocCurveOpt: Option[MsiRocCurve] = if( validationConfig.pepMatchValidator.isEmpty) None
    else {
      val somePepMatchValidator = validationConfig.pepMatchValidator.get
      val validationFilter = somePepMatchValidator.validationFilter

      if (validationFilter.isInstanceOf[IFilterNeedingResultSet])
        validationFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(targetRs)

      logger.debug("Run peptide match validator: " + validationFilter.filterParameter)

      // Apply Filter
      val valResults = somePepMatchValidator.validatePeptideMatches(targetRs, tdAnalyzer)
      
      if (valResults.finalResult != null) {
        finalValidationResult = valResults.finalResult

        appliedFilters += validationFilter
        // Store Validation Params obtained after filtering
        filterDescriptors += validationFilter.toFilterDescriptor

        rsmValProperties.getParams.setPsmValidator(Some(somePepMatchValidator.toValidatorDescriptor))

        // Retrieve the ROC curve
        valResults.getRocCurve()
      } else None
    }

    // Save PSM Filters properties
    val expectedFdr = if (validationConfig.pepMatchValidator.isDefined) validationConfig.pepMatchValidator.get.expectedFdr else None
    rsmValProperties.getParams.setPsmExpectedFdr(expectedFdr)
    rsmValProperties.getParams.setPsmFilters(Some(filterDescriptors.toArray))

    // Save final PSM Filtering result
    val pepValResults = if (finalValidationResult != null) {
      RsmValidationResultProperties(
        targetMatchesCount = finalValidationResult.targetMatchesCount,
        decoyMatchesCount = finalValidationResult.decoyMatchesCount,
        fdr = finalValidationResult.fdr
      )
    } else {

      // If finalValidationResult is null, simply count validated PSMs
      val targetMatchesCount = targetRs.peptideMatches.count(_.isValidated)
      val decoyMatchesCount = if (targetRs.decoyResultSet.isEmpty) None
      else Some(targetRs.decoyResultSet.get.peptideMatches.count(_.isValidated))

      RsmValidationResultProperties(
        targetMatchesCount = targetMatchesCount,
        decoyMatchesCount = decoyMatchesCount
      )
    }

    // Update PSM validation result of the ResultSummary
    rsmValProperties.getResults.setPsmResults(Some(pepValResults))

    (appliedFilters.result, rocCurveOpt)
  }

  private def _validatePeptideInstances(peptideInstances: Seq[PeptideInstance]): Unit = {

    if (validationConfig.peptideFilters.isDefined) {
      validationConfig.peptideFilters.get.foreach(f => f.filterPeptideInstances(peptideInstances));
    }

    if (validationConfig.peptideValidator.isDefined) {
      val (decoyPepInstances, targetPepInstances) = peptideInstances.partition(_.isDecoy)
      validationConfig.peptideValidator.get.validatePeptideMatches(targetPepInstances, Some(decoyPepInstances), tdAnalyzer)
    }

  }

  private def _validateProteinSets(targetRsm: ResultSummary, rsmValProperties: RsmValidationProperties): Option[MsiRocCurve] = {

    val filterDescriptors = new ArrayBuffer[FilterDescriptor]()
    var finalValidationResult: ValidationResult = null

    // Execute all protein set filters
    if (validationConfig.protSetFilters.isDefined) {
      validationConfig.protSetFilters.get.foreach { protSetFilter =>

        // Apply filter
        val protSetValidatorForFiltering = new BasicProtSetValidator(protSetFilter)
        val valResults = protSetValidatorForFiltering.validateProteinSets(targetRsm, tdAnalyzer)
        finalValidationResult = valResults.finalResult

        logger.debug(
          "After Filter " + protSetFilter.filterDescription +
            " Nbr protein set target validated = " + finalValidationResult.targetMatchesCount +
            " <> " + targetRsm.proteinSets.count(_.isValidated)
        )
         
        // Store Validation Params obtained after filtering
        filterDescriptors += protSetFilter.toFilterDescriptor

      }
    } //End go through all Prot Filters

    executeOnProgress() //execute registered action during progress

    // Get current FDR and execute validator only if expected FDR not already reached
    val expectedFdr = if (validationConfig.protSetValidator.isDefined) validationConfig.protSetValidator.get.expectedFdr else None
    val initialFdr = getFdrForRSM(targetRsm,tdAnalyzer)
    val fdrReached =  (initialFdr.isDefined && expectedFdr.isDefined) && (initialFdr.get < expectedFdr.get)

    // If define, execute protein set validator  
    val rocCurveOpt = if (validationConfig.protSetValidator.isEmpty || fdrReached ) None
    else {

      val protSetValidator = validationConfig.protSetValidator.get
      logger.debug("Run protein set validator: " + protSetValidator.toFilterDescriptor().parameter)
      val valResults: ValidationResults = protSetValidator.validateProteinSets(targetRsm, tdAnalyzer)
      finalValidationResult = valResults.finalResult

      filterDescriptors += protSetValidator.toFilterDescriptor

      rsmValProperties.getParams.setProteinValidator(Some(protSetValidator.toValidatorDescriptor))


      valResults.getRocCurve()
    }

    // Save Protein Set Filters properties
    rsmValProperties.getParams.setProteinExpectedFdr(expectedFdr)
    rsmValProperties.getParams.setProteinFilters(Some(filterDescriptors.toArray))

    // Save final Protein Set Filtering result
    val protSetValResults = if (finalValidationResult == null) {

      val fdr : Option[Float] = getFdrForRSM(targetRsm, tdAnalyzer)
      val targetMatchesCount = targetRsm.proteinSets.count(_.isValidated)
      val decoyMatchesCount = if (targetRsm.decoyResultSummary == null || targetRsm.decoyResultSummary.isEmpty) None
      else Some(targetRsm.decoyResultSummary.get.proteinSets.count(_.isValidated))

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

  private def getFdrForRSM(targetRsm: ResultSummary, tdAnalyzer: Option[ITargetDecoyAnalyzer]): Option[Float] = {

    val targetMatchesCount = targetRsm.proteinSets.count(_.isValidated)
    val decoyMatchesCount = if (targetRsm.decoyResultSummary == null || targetRsm.decoyResultSummary.isEmpty) None
    else Some(targetRsm.decoyResultSummary.get.proteinSets.count(_.isValidated))

    var fdr : Option[Float] =  if(decoyMatchesCount.isDefined && decoyMatchesCount.get > 0 && tdAnalyzer.isDefined) {
      tdAnalyzer.get.calcTDStatistics(targetMatchesCount, decoyMatchesCount.get).fdr
    } else {
      None
    }

    fdr
  }
}