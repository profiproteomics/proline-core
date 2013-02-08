package fr.proline.core.service.msi

import java.io.File
import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.{ MsiSearchStorer, RsStorer }
import fr.proline.api.service.IService
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal._
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filter.IPeptideMatchFilter
import fr.proline.core.algo.msi.filter.TargetDecoyModes
import fr.proline.core.algo.msi.filter.IProteinSetFilter
import fr.proline.core.algo.msi.filter.ComputedFDRPeptideMatchFilter
import scala.collection.mutable.ArrayBuilder
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.filter.FilterUtils
import fr.proline.core.algo.msi.filter.ParamProteinSetFilter
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider

object ResultSetValidator {

  def apply(execContext: IExecutionContext,
    targetRsId: Int,
    pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
    computerPSMFilter: Option[ComputedFDRPeptideMatchFilter] = None,
    protSetFilters: Option[Seq[IProteinSetFilter]] = None,
    targetDecoyMode: Option[TargetDecoyModes.Mode] = None,
    storeResultSummary: Boolean = true): ResultSetValidator = {
    val targetRs = getResultSetProvider(execContext).getResultSet(targetRsId)

    if (targetRs.isEmpty) {
      throw new IllegalArgumentException("Unknown ResultSet Id: " + targetRsId)
    }

    new ResultSetValidator(execContext,
      targetRs.get,
      pepMatchPreFilters,
      computerPSMFilter,
      protSetFilters,
      targetDecoyMode,
      storeResultSummary)
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
class ResultSetValidator(execContext: IExecutionContext,
  targetRs: ResultSet,
  pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
  computerPSMFilter: Option[ComputedFDRPeptideMatchFilter] = None,
  protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  targetDecoyMode: Option[TargetDecoyModes.Mode] = None,
  storeResultSummary: Boolean = true) extends IService with Logging {

  //  def this( execContext: IExecutionContext,
  //            targetRsId: Int,
  //            pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
  //            computerPSMFilter: Option[ComputedFDRPeptideMatchFilter] = None,
  //            protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  //            targetDecoyMode: Option[TargetDecoyModes.Mode] = None,
  //            storeResultSummary: Boolean = true ) {
  //    
  //      this( execContext, 
  //          ResultSetValidator.getResultSetProvider(execContext).getResultSet(targetRsId).get, 
  //          pepMatchPreFilters, 
  //          computerPSMFilter, 
  //          protSetFilters, 
  //          targetDecoyMode, 
  //          storeResultSummary )
  //  }

  private val msiDbContext = execContext.getMSIDbConnectionContext

  override protected def beforeInterruption = {
    // Release database connections -VDS: No more necessary... should be done by IExecutionContext initializer
    this.logger.info("releasing database connections before service interruption...")
    //    this.msiDbContext.close()
  }

  var validatedTargetRsm: ResultSummary = null
  var validatedDecoyRsm: Option[ResultSummary] = null
  //  val targetRs = msiDbContext.getEntityManager().find( classOf[ResultSet], targetRsId )

  def runService(): Boolean = {

    import fr.proline.util.primitives.DoubleOrFloatAsFloat._

    val startTime = curTimeInSecs()

    // Retrieve search engine name    
    val searchEngine = this._getSearchEngineName(targetRs)

    // Create RSM validation properties
    val rsmValProperties = RsmValidationProperties(
      params = RsmValidationParamsProperties(),
      results = RsmValidationResultsProperties())
    >>>
    filterPSMs(rsmValProperties, searchEngine)

    >>>

    // Instantiate a protein set inferer
    val protSetInferer = ProteinSetInferer(InferenceMethods.parsimonious)

    val resultSets = List(Some(targetRs), targetRs.decoyResultSet)
    val resultSummaries = List.newBuilder[Option[ResultSummary]]

    // Build result summary for each individual result set
    for (rs <- resultSets) {
      if (rs != None) {
        // Create new result set with validated peptide matches and compute result summary
        val validatedRs = this._copyRsWithValidatedPepMatches(rs.get)
        val rsm = protSetInferer.computeResultSummary(validatedRs)
        resultSummaries += Some(rsm)
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
    if (decoyRsmOpt.isDefined) decoyRsmOpt.get.properties = Some(ResultSummaryProperties(validationProperties = Some(rsmValProperties)))

    filterProteinSets(rsmValProperties, searchEngine, targetRsm, decoyRsmOpt)

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
        targetRsm.decoyResultSummary = decoyRsmOpt
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

  private def _getSearchEngineName(rs: ResultSet): String = {

    if (rs.msiSearch != null)
      return rs.msiSearch.searchSettings.softwareName;
    else if (rs.getMSISearchId > 0)
      return msiDbContext.getEntityManager.find(classOf[MSISearch], rs.getMSISearchId).searchSettings.softwareName
    else
      return "mascot"
  }

  private def _copyRsWithValidatedPepMatches(rs: ResultSet): ResultSet = {
    rs.copy(peptideMatches = rs.peptideMatches.filter { _.isValidated })
  }

  def curTimeInSecs() = System.currentTimeMillis() / 1000

  private def filterPSMs(rsmValProperties: RsmValidationProperties, searchEngine: String): Unit = {

    // Instantiate a peptide match validator
    val pepMatchValidator = PeptideMatchValidator(searchEngine, targetRs)

    var finalValidationResult: ValidationResult = null

    // Execute all peptide matches pre filters
    var filtersParam = HashMap.empty[Int, FilterProperties]
    var filterNbr = 1
    if (pepMatchPreFilters != None) {
      pepMatchPreFilters.get.foreach { psmFilter =>

        finalValidationResult = pepMatchValidator.applyPSMFilter(filter = psmFilter,
          targetDecoyMode = targetDecoyMode)
        filtersParam += (filterNbr -> new FilterProperties(name = psmFilter.filterName, psmFilter.getFilterProperties))
        filterNbr += 1
      }
    } //End execute all PSM filters
    >>>

    // If define, execute ComputedValidationPSMFilter  
    if (computerPSMFilter.isDefined) {

      //Apply Filter
      val valResults = pepMatchValidator.applyComputedPSMFilter(computerPSMFilter.get, targetDecoyMode)
      finalValidationResult = valResults.expectedResult

      //Set RSM Validation Param : VDS TODO
      filtersParam += (filterNbr -> new FilterProperties(name = computerPSMFilter.get.fdrValidationFilter.filterName,
        computerPSMFilter.get.fdrValidationFilter.getFilterProperties))
      filterNbr += 1
    }

    //Save PSM Filters properties 
    rsmValProperties.params.peptideFilters = Some(filtersParam.toMap)
    //Save final PSM Filtering result
    if (finalValidationResult != null) {

      val pepValResults = RsmPepMatchValidationResultsProperties(
        lastFilterThreshold = finalValidationResult.properties.getOrElse(null)(FilterUtils.THRESHOLD_PROP_NAME),
        targetMatchesCount = finalValidationResult.nbTargetMatches,
        decoyMatchesCount = finalValidationResult.nbDecoyMatches,
        fdr = finalValidationResult.fdr)
      rsmValProperties.results.setPeptideResults(Some(pepValResults))
    }
    //VDS TODO : value if no filter ? 
  }

  private def filterProteinSets(rsmValProperties: RsmValidationProperties, searchEngine: String, targetRsm: ResultSummary, decoyRsmOpt: Option[ResultSummary]): Unit = {

    // Instantiate a protein set validator
    val protSetValidator = ProteinSetValidator(searchEngine, ValidationMethods.proteinSetScore)

    var filtersParam = HashMap.empty[Int, FilterProperties]
    var filterNbr = 1

    //VDS TODO: Remove once ProteinFilter Refactoring will have been done !
    var threshold: Float = -1.0f
    if (rsmValProperties.getResults.getPeptideResults.isDefined)
      threshold = rsmValProperties.getResults.getPeptideResults.get.lastFilterThreshold.asInstanceOf[Float]

    // Run all protein set filters
    if (protSetFilters != None) {
      protSetFilters.get.foreach { protSetFilter =>
        //VDS TODO: Remove once ProteinFilter Refactoring will have been done !
        if (rsmValProperties.getResults.getPeptideResults.isDefined)
          protSetFilter.asInstanceOf[ParamProteinSetFilter].pValueThreshold = threshold

        protSetValidator.validateWithComputerParams(
          proteinSetsFilter = protSetFilter,
          targetRsm = targetRsm,
          decoyRsm = decoyRsmOpt.get)

        filtersParam += (filterNbr -> new FilterProperties(name = protSetFilter.filterName, protSetFilter.getFilterProperties))
        filterNbr += 1
      } //End go through all Prot Filters
    }

    >>>

    //Save ProteinSets Filters properties 
    rsmValProperties.params.proteinFilters = Some(filtersParam.toMap)

    // Select only validated protein sets
    val allProteinSets = targetRsm.proteinSets
    if (decoyRsmOpt.isDefined)
      allProteinSets ++ decoyRsmOpt.get.proteinSets

    for (proteinSet <- allProteinSets) {
      if (proteinSet.isValidated) {
        proteinSet.selectionLevel = 2
      } else {
        proteinSet.selectionLevel = 1
      }
    }

    //Save validation params 
  }
}