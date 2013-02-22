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
import fr.proline.core.algo.msi.filter._
import scala.collection.mutable.ArrayBuilder
import scala.collection.mutable.HashMap
import fr.proline.core.algo.msi.filter.PepMatchFilterPropertyKeys
import fr.proline.core.algo.msi.filter.ParamProteinSetFilter
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider

object ResultSetValidator {

  def apply(
    execContext: IExecutionContext,
    targetRsId: Int,
    tdAnalyzer: Option[ITargetDecoyAnalyzer] = None,
    pepMatchPreFilters: Option[Seq[IPeptideMatchFilter]] = None,
    pepMatchValidator: Option[IPeptideMatchValidator] = None,
    protSetFilters: Option[Seq[IProteinSetFilter]] = None,
    storeResultSummary: Boolean = true): ResultSetValidator = {

    val rsProvider = getResultSetProvider(execContext)    
    val targetRs = rsProvider.getResultSet(targetRsId)
    
    if (targetRs.isEmpty) {
      throw new IllegalArgumentException("Unknown ResultSet Id: " + targetRsId)
    }
    
    // Load decoy result set if needed
    if( execContext.isJPA == false ) {
      val decoyRsId = targetRs.get.getDecoyResultSetId
      if( decoyRsId > 0 ) {
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
  // TODO: add a protSetValidator
  storeResultSummary: Boolean = true) extends IService with Logging {

  private val msiDbContext = execContext.getMSIDbConnectionContext
  var validatedTargetRsm: ResultSummary = null
  var validatedDecoyRsm: Option[ResultSummary] = null
  
  // Update the target analyzer of the validators
  pepMatchValidator.foreach( _.tdAnalyzer = tdAnalyzer )

  override protected def beforeInterruption = {
    // Release database connections -VDS: No more necessary... should be done by IExecutionContext initializer
    this.logger.info("releasing database connections before service interruption...")
    //    this.msiDbContext.close()
  }
    
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
    filterPSMs(targetRs,rsmValProperties, searchEngine)

    >>>

    // Instantiate a protein set inferer
    val protSetInferer = ProteinSetInferer(InferenceMethods.parsimonious)

    val resultSets = List(Some(targetRs), targetRs.decoyResultSet)
    val resultSummaries = List.newBuilder[Option[ResultSummary]]

    // Build result summary for each individual result set
    for (rs <- resultSets) {
      if (rs != null && rs.isDefined) {        
        // Create new result set with validated peptide matches and compute result summary
        val validatedRs = this._copyRsWithValidatedPepMatches(rs.get)
        val rsm = protSetInferer.computeResultSummary(validatedRs)
        rsm.resultSet = rs // affect complete RS 
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

  private def filterPSMs(targetRs: ResultSet, rsmValProperties: RsmValidationProperties, searchEngine: String): Unit = {

    // Instantiate a peptide match validator
    //val pepMatchValidator = PeptideMatchValidator(searchEngine, targetRs)
    import fr.proline.core.algo.msi.validation.pepmatch.BasicPepMatchValidator
    
    val filterDescriptors = new ArrayBuffer[FilterDescriptor]( pepMatchPreFilters.map(_.length).getOrElse(0) )
    var finalValidationResult: ValidationResult = null
    
    // Execute all peptide matches pre filters
    if (pepMatchPreFilters != None) {
      pepMatchPreFilters.get.foreach { psmFilter =>
        
        //finalValidationResult = pepMatchValidator.applyPSMFilter(filter = psmFilter,targetDecoyMode = targetDecoyMode)
        //psmFilter.filterPeptideMatches(targetRs.peptideMatches, true, false)
        finalValidationResult = new BasicPepMatchValidator( psmFilter, tdAnalyzer ).validatePeptideMatches(targetRs).finalResult
        logger.debug("After Filter "+psmFilter.filterDescription+" Nbr PepMatch target validated = "+finalValidationResult.targetMatchesCount )
        
        filterDescriptors += psmFilter.toFilterDescriptor
      }
    } //End execute all PSM filters
    >>>
    
    // If define, execute ComputedValidationPSMFilter  
    if (pepMatchValidator.isDefined) {
      
      logger.debug(" Run validator")

      //Apply Filter
      val valResults = pepMatchValidator.get.validatePeptideMatches(targetRs)
      finalValidationResult = valResults.finalResult

      //Set RSM Validation Param : VDS TODO
      filterDescriptors += pepMatchValidator.get.validationFilter.toFilterDescriptor
      
    }

    //Save PSM Filters properties
    rsmValProperties.getParams.setPeptideFilters( Some(filterDescriptors.toArray) )
    val expectedFdr = if( pepMatchValidator.isDefined ) pepMatchValidator.get.expectedFdr else None
    rsmValProperties.getParams.setPeptideExpectedFdr( expectedFdr )
    
    //Save final PSM Filtering result
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

  private def filterProteinSets(rsmValProperties: RsmValidationProperties, searchEngine: String, targetRsm: ResultSummary, decoyRsmOpt: Option[ResultSummary]): Unit = {
    
    if(!protSetFilters.isDefined)
      return

    //VDS TODO: Remove once ProteinFilter Refactoring will have been done !
    val pepFilters = rsmValProperties.getParams.getPeptideFilters
    val lastFilterThr = if( pepFilters.isDefined ) {
      Some(
        pepFilters.get.last.getProperties.get( 
          PepMatchFilterPropertyKeys.THRESHOLD_PROP_NAME
        ).asInstanceOf[AnyVal]
      )
    } else None
    
    var threshold: Float = -1.0f
    if( lastFilterThr.isDefined == false ) {
      throw new Exception("No valid threshold was specified in RSM Validation Properties  ")
    } else {
      threshold = lastFilterThr.get match {
        case d: Double => d.toFloat
        case f: Float => f
        case _ => throw new Exception("invalid threshold value")
      }
    }
    
    // Instantiate a protein set validator
    val protSetValidator = ProteinSetValidator(searchEngine, ProtSetValidationMethods.proteinSetScore)
    val filterDescriptors = new ArrayBuffer[FilterDescriptor]()

    // Run all protein set filters
    protSetFilters.get.foreach { protSetFilter =>
      
      //VDS TODO: Remove once ProteinFilter Refactoring will have been done !
      if ( rsmValProperties.getResults.getPeptideResults.isDefined )
        protSetFilter.asInstanceOf[ParamProteinSetFilter].pValueThreshold = threshold

      protSetValidator.validateWithComputerParams(
        proteinSetsFilter = protSetFilter,
        targetRsm = targetRsm,
        decoyRsm = decoyRsmOpt.get
      )

      filterDescriptors += protSetFilter.toFilterDescriptor
      
    } //End go through all Prot Filters

    >>>

    // Save ProteinSets Filters properties
    rsmValProperties.getParams.setProteinFilters( Some(filterDescriptors.toArray) )
    // TODO: save the expectedFDR

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