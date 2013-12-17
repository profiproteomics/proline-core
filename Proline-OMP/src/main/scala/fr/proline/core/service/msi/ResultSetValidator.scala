package fr.proline.core.service.msi

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ArrayBuilder
import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.api.service.IService
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.algo.msi.validation.pepmatch.BasicPepMatchValidator
import fr.proline.core.algo.msi.validation.proteinset.BasicProtSetValidator
import fr.proline.core.dal._
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.algo.msi.scoring._
import fr.proline.core.algo.msi.InferenceMethods

object ResultSetValidator {

  def apply(
    execContext: IExecutionContext,
    targetRsId: Long,
    tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
    pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
    pepMatchValidator: Option[IPeptideMatchValidator] = None,
    protSetFilters: Option[Seq[IProteinSetFilter]] = None,
    protSetValidator: Option[IProteinSetValidator] = None,
    inferenceMethod: Option[InferenceMethods.InferenceMethods] = Some(InferenceMethods.communist),
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
  inferenceMethod: Option[InferenceMethods.InferenceMethods] = Some(InferenceMethods.communist),
  peptideSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
  storeResultSummary: Boolean = true
) extends IService with Logging {

  private val msiDbContext = execContext.getMSIDbConnectionContext
  var validatedTargetRsm: ResultSummary = null
  var validatedDecoyRsm: Option[ResultSummary] = null

  // Update the target analyzer of the validators
  pepMatchValidator.foreach(_.tdAnalyzer = tdAnalyzer)

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
    >>>

    // --- Validate PSM ---
    val appliedPSMFilters = this._validatePeptideMatches(targetRs, rsmValProperties)
    >>>

    // --- Compute RSM from validated PSMs ---

    // Instantiate a protein set inferer
    val protSetInferer = ProteinSetInferer(inferenceMethod.get)

    //-- createRSM
    def createRSM(currentRS: Option[ResultSet]): Option[ResultSummary] = {
      if (currentRS.isDefined) {

        // Create new result set with validated peptide matches and compute result summary
        val rsm = protSetInferer.computeResultSummary(currentRS.get)

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
    val targetRsm = createRSM(Some(targetRs)).get
    val decoyRsmOpt = createRSM(if (targetRs.decoyResultSet != null) targetRs.decoyResultSet else None)
    >>>

    // Set target RSM validation properties
    targetRsm.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))
    if (decoyRsmOpt.isDefined) {
      // Link decoy RSM to target RSM
      targetRsm.decoyResultSummary = decoyRsmOpt

      // Set decoy RSM validation properties
      decoyRsmOpt.get.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))
    }

    // --- Validate ProteinSet ---
    this._validateProteinSets(targetRsm, rsmValProperties)

    val took = curTimeInSecs() - startTime
    this.logger.info("Validation service took " + took + " seconds")

    if (storeResultSummary) {

      // Check if a transaction is already initiated
      val wasInTransaction = msiDbContext.isInTransaction()
      if (!wasInTransaction) msiDbContext.beginTransaction()

      // Instantiate a RSM storer
      val rsmStorer = RsmStorer(msiDbContext)

      // Store decoy result summary
      if (decoyRsmOpt != None) {
        rsmStorer.storeResultSummary(decoyRsmOpt.get, execContext)
      }

      // Store target result summary
      rsmStorer.storeResultSummary(targetRsm, execContext)
      >>>

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
  private def _validatePeptideMatches(targetRs: ResultSet, rsmValProperties: RsmValidationProperties): Seq[IPeptideMatchFilter] = {

    var appliedFilters = Seq.newBuilder[IPeptideMatchFilter]
    val filterDescriptors = new ArrayBuffer[FilterDescriptor](pepMatchPreFilters.map(_.length).getOrElse(0))
    var finalValidationResult: ValidationResult = null

    // Execute all peptide matches pre filters
    if (pepMatchPreFilters != None) {
      pepMatchPreFilters.get.foreach { psmFilter =>

        finalValidationResult = new BasicPepMatchValidator(psmFilter, tdAnalyzer).validatePeptideMatches(targetRs).finalResult
        logger.debug(
          "After Filter " + psmFilter.filterDescription +
          " Nbr PepMatch target validated = " + finalValidationResult.targetMatchesCount
        )
        appliedFilters += psmFilter
        filterDescriptors += psmFilter.toFilterDescriptor
      }
    } //End execute all PSM filters
    >>>

    // If define, execute peptide match validator  
    for (somePepMatchValidator <- pepMatchValidator) {

      val validationFilter = somePepMatchValidator.validationFilter
      logger.debug("Run peptide match validator: " + validationFilter.filterParameter)

      // Apply Filter
      val valResults = somePepMatchValidator.validatePeptideMatches(targetRs)
      finalValidationResult = valResults.finalResult

      appliedFilters += validationFilter
      // Store Validation Params obtained after filtering
      filterDescriptors += validationFilter.toFilterDescriptor
    }

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

    appliedFilters.result
  }

  private def _validateProteinSets(targetRsm: ResultSummary, rsmValProperties: RsmValidationProperties): Unit = {
    if (protSetFilters.isEmpty && protSetValidator.isEmpty) return ()

    val filterDescriptors = new ArrayBuffer[FilterDescriptor]()
    var finalValidationResult: ValidationResult = null

    // Execute all protein set filters
    if (protSetFilters != None) {
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

    >>>

    // If define, execute protein set validator  
    if (protSetValidator.isDefined) {

      logger.debug("Run protein set validator: " + protSetValidator.get.toFilterDescriptor.parameter)

      // Update the target/decoy mode of the protein set validator for this RSM
      val tdModeStr = targetRsm.resultSet.get.properties.get.targetDecoyMode.get
      val tdMode = TargetDecoyModes.withName(tdModeStr)
      protSetValidator.get.targetDecoyMode = Some(tdMode)

      finalValidationResult = protSetValidator.get.validateProteinSets(targetRsm).finalResult

      filterDescriptors += protSetValidator.get.toFilterDescriptor
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
  }
}