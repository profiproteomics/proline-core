package fr.proline.core.service.uds

import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.collection._
import fr.profi.util.primitives._
import fr.proline.api.service.IService
import fr.proline.context.{IExecutionContext, DatabaseConnectionContext}
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal.context._
import fr.proline.core.dal.helper.{MsiDbHelper, UdsDbHelper}
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.service.msi.{ResultSetValidator, ResultSetMerger, ResultSummaryMerger, ValidationConfig}
import fr.proline.core.om.provider.msi.ResultSetFilter

// Singleton for Proline-Cortex
object IdentificationTreeValidator {
  
  def validateIdentificationTrees(
    execContext: IExecutionContext,
    parentDsIds: Array[Long],
    mergeResultSets: Boolean,
    useTdCompetition: Boolean,
    validationConfig: ValidationConfig
  ) {
    
    execContext.tryInTransactions( udsTx = true, msiTx = true, txWork = {
      
      for (parentDsId <- parentDsIds) {
        
        // Instantiate an identification validator
        val idfValidator = new IdentificationTreeValidator(
          //dsFactory = dsFactory,
          execContext = execContext,
          identTreeId = parentDsId,
          mergeResultSets = mergeResultSets,
          useTdCompetition = useTdCompetition,
          pepMatchFilters = validationConfig.pepMatchPreFilters,
          pepMatchValidator = validationConfig.pepMatchValidator,
          peptideSetScoring = Some( validationConfig.pepSetScoring.getOrElse(PepSetScoring.MASCOT_MODIFIED_MUDPIT_SCORE) ),
          protSetFilters = validationConfig.protSetFilters,
          protSetValidator = validationConfig.protSetValidator
        )
        
        idfValidator.run()
      }
      
    }) // end of tryInTransactions
    
  }
  
}

class IdentificationTreeValidator(
  //dsFactory: IDataStoreConnectorFactory,
  execContext: IExecutionContext,
  identTreeId: Long,
  mergeResultSets: Boolean,
  useTdCompetition: Boolean,
  pepMatchFilters: Option[Seq[IPeptideMatchFilter]] = None,
  pepMatchValidator: Option[IPeptideMatchValidator] = None,
  peptideSetScoring: Option[PepSetScoring.Value] = Some(PepSetScoring.MASCOT_STANDARD_SCORE),
  protSetFilters: Option[Seq[IProteinSetFilter]] = None,
  protSetValidator: Option[IProteinSetValidator] = None
) extends IService with LazyLogging {
  
  // TODO: uncomment this require when JPA vs SQL context instantiation is fixed
  //require( execContext.isJPA, "a JPA execution context is needed" )

  private val udsDbCtx = execContext.getUDSDbConnectionContext()
  private val psDbCtx = execContext.getPSDbConnectionContext()
  private val msiDbCtx = execContext.getMSIDbConnectionContext()
  private val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // TODO: remove this require when LCMS ORM is implemented
  require(udsDbCtx.isJPA, "a JPA execution context is needed")

  def runService(): Boolean = {

    // Retrieve UDSdb entity manager
    val udsEM = udsDbCtx.getEntityManager()

    // Retrieve identification datasets ids
    val udsIdentTreeDS = udsEM.find(classOf[UdsDataset], identTreeId)
    //println("udsIdentTreeDS.getChildren",udsIdentTreeDS.getChildren)
    val projectId = udsIdentTreeDS.getProject.getId
    val udsIdentDatasets = udsIdentTreeDS.getIdentificationDatasets()

    // Retrieve target result sets ids
    val targetRsIds = udsIdentDatasets.map(_.getResultSetId.longValue).toArray
    this.logger.debug("TARGET RS ids count = " + targetRsIds.length)
    this.logger.debug("First TARGET RS id = " + targetRsIds(0))

    // Retrieve decoy RS ids if they exists
    val decoyRsIdByTargetRsId = msiDbHelper.getDecoyRsIdByTargetRsId(targetRsIds)
    val decoyRsIdsAsOpts = targetRsIds.map { decoyRsIdByTargetRsId.get(_) } filter { _.isDefined }

    // Check that if we have decoy data we have the same number of target and decoy result sets
    val (nbTargetRs, nbDecoyRs) = (targetRsIds.length, decoyRsIdsAsOpts.length)
    this.logger.debug("DECOY RS ids count = " + nbDecoyRs)
    require(
      nbDecoyRs == nbTargetRs || nbDecoyRs == 0,
      "missing decoy result set for one of the provided result sets"
    )
    
    // Try to retrieve the pretty rank filter
    val rsFilterOpt = if (pepMatchFilters.isEmpty) None
    else {
      pepMatchFilters.get.find { filter =>
        // TODO: rename RANK to PRETTY_RANK
        filter.filterParameter == PepMatchFilterParams.RANK.toString()
      } map { rankFilter =>
        val maxPrettyRank = toInt(rankFilter.getThresholdValue())
        new ResultSetFilter(maxPeptideMatchPrettyRank = Some(maxPrettyRank))
      }
    }
    if (rsFilterOpt.nonEmpty) logger.debug("Will load result sets using rank filter = "+rsFilterOpt.get.maxPeptideMatchPrettyRank)
    else logger.debug("Unable to retrieve a rank filter")
    
    // Load result sets
    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)
    
    // Create a mapping between result set ids and computed result summaries
    val rsmByRsId = new LongMap[ResultSummary]
    
    // Group target result set ids 3 by 3 (to avoid loading too many result sets at a time)
    for (targetRsIdGroup <- targetRsIds.grouped(3)) {
      
      val decoyRsIdGroup = targetRsIdGroup.map( decoyRsIdByTargetRsId.get(_) ).withFilter(_.isDefined).map(_.get)
      val rsIds = targetRsIdGroup ++ decoyRsIdGroup
      
      logger.info(s"Loading one group of ${rsIds.length} result sets...")
      
      val (targetRsList, decoyRsList) = rsProvider.getResultSets(rsIds, rsFilterOpt).partition { _.isDecoy == false }
      val decoyRsById = decoyRsList.mapByLong(_.id)
      
      for (targetRS <- targetRsList) {
        
        val targetRsId = targetRS.id
        this.logger.debug(s"Current TARGET RS (id=$targetRsId) contains ${targetRS.peptideMatches.length} peptide matches (after filtering).")
        
        // Link decoy RS to target RS
        targetRS.decoyResultSet = decoyRsById.get(targetRS.getDecoyResultSetId)
        
        this.logger.debug("Validating target result set...")
        val rsm = this._validateResultSet(targetRS)
        
        if (mergeResultSets == false) {
          
          this.logger.debug("Removing non-validated entities from target RS and decoy RS...")
          val validatedRsOpt = rsm.getValidatedResultSet()
          
          // Replace the result set by the validated one
          rsm.resultSet = validatedRsOpt
          
          val decoyRsm = rsm.decoyResultSummary.get
          val validatedDecoyRsOpt = decoyRsm.getValidatedResultSet()
          
          // Set the decoy result set as the validated decoy one
          validatedRsOpt.get.decoyResultSet = validatedDecoyRsOpt
          
          // Do the same in the decoy result summary
          decoyRsm.resultSet = validatedDecoyRsOpt
        }
        
        rsmByRsId += targetRsId -> rsm
      }
    }

    // Link result summaries to the corresponding dataset id in UDSdb
    for (udsIdentDataset <- udsIdentDatasets) {
      val rsId = udsIdentDataset.getResultSetId
      udsIdentDataset.setResultSummaryId(rsmByRsId(rsId).id)

      udsEM.persist(udsIdentDataset)
    }

    // Validate datasets recursively from leaves to the root
    this._validateDatasetTree(udsEM, udsIdentDatasets.toList, rsmByRsId)

    true
  }

  private def _validateResultSet(rs: ResultSet): ResultSummary = {

    val tdAnalyzer = if (pepMatchValidator.isDefined && pepMatchValidator.get.validationFilter.isInstanceOf[IPeptideMatchSorter]) {
      BuildTDAnalyzer(useTdCompetition, rs, Some(pepMatchValidator.get.validationFilter.asInstanceOf[IPeptideMatchSorter]))
    } else {
      BuildTDAnalyzer(useTdCompetition, rs, None)
    }
    
    val linkTargetRsToFilter = (filter: IPeptideMatchFilter) => _linkTargetRsToFilter(filter, rs)

    // Test if there are some IFilterNeedingResultSet in Flilters.
    if (pepMatchFilters.isDefined) {
      pepMatchFilters.get.foreach(linkTargetRsToFilter)
    }
    
    if(pepMatchValidator.isDefined) linkTargetRsToFilter(pepMatchValidator.get.validationFilter)
    
    // Instantiate a result set validator
    val rsValidator = new ResultSetValidator(
      execContext = execContext,
      targetRs = rs,
      tdAnalyzer = tdAnalyzer,
      pepMatchPreFilters = pepMatchFilters,
      pepMatchValidator = pepMatchValidator,
      protSetFilters = protSetFilters,
      protSetValidator = protSetValidator,
      peptideSetScoring = peptideSetScoring
    )

    rsValidator.run()

    rsValidator.validatedTargetRsm
  }
  
  private def _linkTargetRsToFilter(filter: IPeptideMatchFilter, rs: ResultSet) {
    filter match {
      case filterNeedingRS: IFilterNeedingResultSet => filterNeedingRS.setTargetRS(rs)
      case _ => ()
    }
  }

  /**
   * Validates dataset tree recursively from leaves to root
   */
  private def _validateDatasetTree(udsEM: EntityManager, udsDatasets: List[UdsDataset], rsmByRsId: LongMap[ResultSummary]) {
    if (udsDatasets.length == 1 && udsDatasets(0).getParentDataset == null) return

    this.logger.info(s"Validating tree node with ${udsDatasets.length} dataset(s)")

    // Group RSMs by parent DS id
    val rsmsByUdsParentDs = new HashMap[UdsDataset, ArrayBuffer[ResultSummary]]
    for (dataset <- udsDatasets) {
      val udsParentDs = dataset.getParentDataset
      val rsm = rsmByRsId(dataset.getResultSetId)
      rsmsByUdsParentDs.getOrElseUpdate(udsParentDs, new ArrayBuffer[ResultSummary]()) += rsm
    }

    // Define some vars
    val newUdsParentDatasets = new ArrayBuffer[UdsDataset]
    val newRsmByRsId = new LongMap[ResultSummary]

    // Iterate over each group of result summaries to merge them
    for ((udsParentDs, rsms) <- rsmsByUdsParentDs) {

      newUdsParentDatasets += udsParentDs

      // Validate dataset child result summaries
      val mergedRsm = this._mergeAndValidateDatasets(rsms)
      newRsmByRsId += mergedRsm.getResultSetId -> mergedRsm

      // Replace existing result set/summary ids
      // Note: to preserve existing mappings it is needed to clone the tree in a previous step      
      udsParentDs.setResultSetId(mergedRsm.getResultSetId)
      udsParentDs.setResultSummaryId(mergedRsm.id)

      // TODO: store properties (do this in the merge RS/RSM service)
      //val mergedDataType = if( mergeResultSets ) "result_set" else "result_summary"
      //udsParentDs.setSerializedProperties() // merged_data_type = mergedDataType

      udsEM.persist(udsParentDs)
    }

    this._validateDatasetTree(udsEM, newUdsParentDatasets.toList, newRsmByRsId)
  }

  private def _mergeAndValidateDatasets(rsms: Seq[ResultSummary]): ResultSummary = {

    if (this.mergeResultSets) {

      //val targetRsIds = rsmIds.map { rsIdByRsmId(_) }
      val targetRsList = rsms.map(_.resultSet.get)
      val decoyRsList = for (rs <- targetRsList; if rs.decoyResultSet.isDefined) yield rs.decoyResultSet.get

      // Merge result set
      val targetRsMerger = new ResultSetMerger(execContext, None, Some(targetRsList), Some(AdditionMode.AGGREGATION))
      targetRsMerger.runService()
      val mergedTargetRs = targetRsMerger.mergedResultSet

      if (decoyRsList.nonEmpty) {
        val decoyRsMerger = new ResultSetMerger(execContext, None, Some(decoyRsList), Some(AdditionMode.AGGREGATION))
        decoyRsMerger.runService()
        mergedTargetRs.decoyResultSet = Some(decoyRsMerger.mergedResultSet)
      }

      // Build Target/Decoy analyzer
      val tdAnalyzer = if (pepMatchValidator.isDefined && pepMatchValidator.get.validationFilter.isInstanceOf[IPeptideMatchSorter]) {
        BuildTDAnalyzer(useTdCompetition, mergedTargetRs, Some(pepMatchValidator.get.validationFilter.asInstanceOf[IPeptideMatchSorter]))
      } else {
        BuildTDAnalyzer(useTdCompetition, mergedTargetRs, None)
      }
      
      val linkTargetRsToFilter = (filter: IPeptideMatchFilter) => _linkTargetRsToFilter(filter, mergedTargetRs)

      // Test if there are some IFilterNeedingResultSet in Filters
      if (pepMatchFilters.isDefined) {
        pepMatchFilters.get.foreach( linkTargetRsToFilter )
      }
      if (pepMatchValidator.isDefined) {
        linkTargetRsToFilter(pepMatchValidator.get.validationFilter)
      }
      
      // Instantiate a result set validator
      val rsValidator = new ResultSetValidator(
        execContext = execContext,
        targetRs = mergedTargetRs,
        tdAnalyzer = tdAnalyzer,
        pepMatchPreFilters = pepMatchFilters,
        pepMatchValidator = pepMatchValidator,
        protSetFilters = protSetFilters,
        protSetValidator = protSetValidator,
        peptideSetScoring = peptideSetScoring,
        storeResultSummary = true
      )

      rsValidator.run()

      rsValidator.validatedTargetRsm

    } else {
      
      // Merge result summaries
      val rsmMerger = new ResultSummaryMerger(
        execCtx = execContext,
        None,
        resultSummaries = Some(rsms),
        aggregationMode = Some(AdditionMode.AGGREGATION),
        useJPA = false
      )
      rsmMerger.runService()

      rsmMerger.mergedResultSummary
    }

  }

}