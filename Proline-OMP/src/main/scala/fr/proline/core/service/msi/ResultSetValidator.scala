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
    targetRsId: Int,
    tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
    pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
    pepMatchValidator: Option[IPeptideMatchValidator] = None,
    protSetFilters: Option[Seq[IProteinSetFilter]] = None,
    protSetValidator: Option[IProteinSetValidator] = None,
    inferenceMethod: Option[InferenceMethods.InferenceMethods] = Some(InferenceMethods.communist),
    proteinSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
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
      proteinSetScoring,
      storeResultSummary
    )
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
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
  proteinSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
  storeResultSummary: Boolean = true
) extends IService with Logging {

  private val msiDbContext = execContext.getMSIDbConnectionContext
  var validatedTargetRsm: ResultSummary = null
  var validatedDecoyRsm: Option[ResultSummary] = null

  // Update the target analyzer of the validators
  pepMatchValidator.foreach(_.tdAnalyzer = tdAnalyzer)

  override protected def beforeInterruption = {
  }

  def runService(): Boolean = {

    logger.info("ResultSet validator service starts")

    val startTime = curTimeInSecs()

    // --- Create RSM validation properties
    val rsmValProperties = RsmValidationProperties(
      params = RsmValidationParamsProperties(),
      results = RsmValidationResultsProperties())
    >>>

    // --- Validate PSM 
    this._validatePeptideMatches(targetRs, rsmValProperties)

    >>>

    // --- Compute RSM from validated PSMs

    // Instantiate a protein set inferer
    val protSetInferer = ProteinSetInferer(inferenceMethod.get)

    val decoyRs = if (targetRs.decoyResultSet != null) targetRs.decoyResultSet else None
    val resultSets = List(Some(targetRs), decoyRs)
    val resultSummaries = List.newBuilder[Option[ResultSummary]]

    // Build result summary for each individual result set
    for (rs <- resultSets) {
      if (rs.isDefined) {

        // Create new result set with validated peptide matches and compute result summary
        //        val validatedRs = this._copyRsWithValidatedPepMatches(rs.get)
        val rsm = protSetInferer.computeResultSummary(rs.get)
        //        rsm.resultSet = rs // affect complete RS : No More necessary , complete RS passed to inferer 

        // Update score of protein sets
        val pepSetScoreUpdater = PeptideSetScoreUpdater(proteinSetScoring.get)
        this.logger.debug("updating score of peptide sets using "+proteinSetScoring.get.toString)
        pepSetScoreUpdater.updateScoreOfPeptideSets(rsm)

        resultSummaries += Some(rsm)
      } else {
        resultSummaries += Option.empty[ResultSummary]
      }
    }
    >>>

    // Build the list of validated RSMs
    val rsmList = resultSummaries.result()

    // Retrieve target/decoy RSMs
    val targetRsm = rsmList(0).get
    val decoyRsmOpt = rsmList(1)

    // Set target RSM validation properties
    targetRsm.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))

    if (decoyRsmOpt.isDefined) {
      // Link decoy RSM to target RSM
      targetRsm.decoyResultSummary = decoyRsmOpt

      // Set decoy RSM validation properties
      decoyRsmOpt.get.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))
    }

    // --- Validate ProteinSet 
    this._validateProteinSets(targetRsm, rsmValProperties)

    val took = curTimeInSecs() - startTime
    this.logger.info("validation took " + took + " seconds")

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

      this.logger.info("result summary successfully stored !")
    }

    // Update the service results
    this.validatedTargetRsm = targetRsm
    this.validatedDecoyRsm = decoyRsmOpt

    this.beforeInterruption()

    true
  }

  private def _copyRsWithValidatedPepMatches(rs: ResultSet): ResultSet = {
    rs.copy(peptideMatches = rs.peptideMatches.filter { _.isValidated })
  }

  def curTimeInSecs() = System.currentTimeMillis() / 1000

  private def _validatePeptideMatches(targetRs: ResultSet, rsmValProperties: RsmValidationProperties): Unit = {

    val filterDescriptors = new ArrayBuffer[FilterDescriptor](pepMatchPreFilters.map(_.length).getOrElse(0))
    var finalValidationResult: ValidationResult = null

    // Execute all peptide matches pre filters
    if (pepMatchPreFilters != None) {
      pepMatchPreFilters.get.foreach { psmFilter =>

        //finalValidationResult = pepMatchValidator.applyPSMFilter(filter = psmFilter,targetDecoyMode = targetDecoyMode)
        //psmFilter.filterPeptideMatches(targetRs.peptideMatches, true, false)
        finalValidationResult = new BasicPepMatchValidator(psmFilter, tdAnalyzer).validatePeptideMatches(targetRs).finalResult
        logger.debug("After Filter " + psmFilter.filterDescription + " Nbr PepMatch target validated = " + finalValidationResult.targetMatchesCount)

        filterDescriptors += psmFilter.toFilterDescriptor
      }
    } //End execute all PSM filters
    >>>

    // If define, execute peptide match validator  
    if (pepMatchValidator.isDefined) {

      logger.debug("Run peptide match validator")

      //Apply Filter
      val valResults = pepMatchValidator.get.validatePeptideMatches(targetRs)
      finalValidationResult = valResults.finalResult

      //Set RSM Validation Param : VDS TODO
      filterDescriptors += pepMatchValidator.get.validationFilter.toFilterDescriptor

    }

    // Save PSM Filters properties
    rsmValProperties.getParams.setPeptideFilters(Some(filterDescriptors.toArray))
    val expectedFdr = if (pepMatchValidator.isDefined) pepMatchValidator.get.expectedFdr else None
    rsmValProperties.getParams.setPeptideExpectedFdr(expectedFdr)

    // Save final PSM Filtering result
    if (finalValidationResult != null) {

      val pepValResults = RsmPepMatchValidationResultsProperties(
        targetMatchesCount = finalValidationResult.targetMatchesCount,
        decoyMatchesCount = finalValidationResult.decoyMatchesCount,
        fdr = finalValidationResult.fdr
      )
      rsmValProperties.getResults.setPeptideResults(Some(pepValResults))
    }
    //VDS TODO : value if no filter ? 
  }

  private def _validateProteinSets(targetRsm: ResultSummary, rsmValProperties: RsmValidationProperties): Unit = {
    if (protSetFilters.isEmpty && protSetValidator.isEmpty) return ()

    val filterDescriptors = new ArrayBuffer[FilterDescriptor]()
    var finalValidationResult: ValidationResult = null

    // Execute all protein set filters
    if (protSetFilters != None) {
      protSetFilters.get.foreach { protSetFilter =>

        val protSetValidatorForFiltering = new BasicProtSetValidator(protSetFilter)
        finalValidationResult = protSetValidatorForFiltering.validateProteinSets(targetRsm).finalResult

        logger.debug("After Filter " + protSetFilter.filterDescription + " Nbr protein set target validated = " + finalValidationResult.targetMatchesCount + " <> " + targetRsm.proteinSets.filter(_.isValidated).length)

        filterDescriptors += protSetFilter.toFilterDescriptor

      }
    } //End go through all Prot Filters

    >>>

    // If define, execute protein set validator  
    if (protSetValidator.isDefined) {

      logger.debug("Run protein set validator")

      finalValidationResult = protSetValidator.get.validateProteinSets(targetRsm).finalResult

      // TODO: log something about the validator
      filterDescriptors += protSetValidator.get.toFilterDescriptor
    }

    if (finalValidationResult == null) {
      logger.debug("FinalValidationResult is NULL")
    } else {
      logger.debug("Final target protein sets count = " + finalValidationResult.targetMatchesCount)

      if ((finalValidationResult.decoyMatchesCount != null) && finalValidationResult.decoyMatchesCount.isDefined) {
        logger.debug("Final decoy protein sets count = " + finalValidationResult.decoyMatchesCount.get)
      }

      if ((finalValidationResult.fdr != null) && finalValidationResult.fdr.isDefined) {
        logger.debug("Final protein sets FDR = " + finalValidationResult.fdr.get)
      }

    }

    // Save ProteinSets Filters properties
    rsmValProperties.getParams.setProteinFilters(Some(filterDescriptors.toArray))
    // TODO: save the expectedFDR

    // Select only validated protein sets
    val decoyRsmOpt = targetRsm.decoyResultSummary
    val allProteinSets = if(decoyRsmOpt != null && decoyRsmOpt.isDefined) targetRsm.proteinSets ++ decoyRsmOpt.get.proteinSets else targetRsm.proteinSets
    
    for (proteinSet <- allProteinSets) {
      if (proteinSet.isValidated) proteinSet.selectionLevel = 2
      else proteinSet.selectionLevel = 1
    }

    // TODO: Save validation params 
  }
}