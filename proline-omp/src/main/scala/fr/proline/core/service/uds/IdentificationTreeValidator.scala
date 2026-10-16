package fr.proline.core.service.uds

import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.LongMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.primitives._
import fr.profi.api.service.IService
import fr.proline.context.{DatabaseConnectionContext, IExecutionContext}
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal.context._
import fr.proline.core.dal.helper.{MsiDbHelper, UdsDbHelper}
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.cache.PeptideCacheRegistry
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.service.msi.{ResultSetMerger, ResultSetValidator, ResultSummaryMerger, ValidationConfig}
import fr.proline.core.om.provider.msi.ResultSetFilter
import fr.proline.core.om.storer.msi.RsmStorer
import fr.proline.core.om.storer.msi.impl.StorerContext

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
          peptideSetScoring = Some( validationConfig.pepSetScoring.getOrElse(PepSetScoring.MASCOT_STANDARD_SCORE) ),
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
  private val msiDbCtx = execContext.getMSIDbConnectionContext()
  private val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // TODO: remove this require when LCMS ORM is implemented
  require(udsDbCtx.isJPA, "a JPA execution context is needed")

  def runService(): Boolean = {

    // Retrieve UDSdb entity manager
    val udsEM = udsDbCtx.getEntityManager

    // Retrieve identification datasets ids
    val udsIdentTreeDS = udsEM.find(classOf[UdsDataset], identTreeId)
    //println("udsIdentTreeDS.getChildren",udsIdentTreeDS.getChildren)
    val projectId = udsIdentTreeDS.getProject.getId
    val udsIdentDatasets = udsIdentTreeDS.getIdentificationDatasets

    // Retrieve target result sets ids
    val targetRsIds: Array[Long] = udsIdentDatasets.map(_.getResultSetId.longValue).toArray
    this.logger.debug("TARGET RS ids count = " + targetRsIds.length)
    this.logger.debug("first RS id = " + targetRsIds(0))

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
        filter.filterParameter == PepMatchFilterParams.PRETTY_RANK.toString
      } map { rankFilter =>
        val maxPrettyRank = toInt(rankFilter.getThresholdValue())
        new ResultSetFilter(maxPeptideMatchPrettyRank = Some(maxPrettyRank))
      }
    }
    if (rsFilterOpt.nonEmpty) logger.debug("Will load result sets using rank filter = "+rsFilterOpt.get.maxPeptideMatchPrettyRank)
    else logger.debug("Unable to retrieve a rank filter")
    
    // Load result sets
    val rsProvider = new SQLResultSetProvider(PeptideCacheExecutionContext(execContext))
    
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
        
        if (!mergeResultSets) {
          
          this.logger.debug("Removing non-validated entities from target RS and decoy RS...")
          val validatedRsOpt = rsm.getValidatedResultSet()
          
          // Replace the result set by the validated one
          rsm.resultSet = validatedRsOpt

          if (rsm.decoyResultSummary.isDefined) {
            val decoyRsm = rsm.decoyResultSummary.get
            val validatedDecoyRsOpt = decoyRsm.getValidatedResultSet()

            // Set the decoy result set as the validated decoy one
            validatedRsOpt.get.decoyResultSet = validatedDecoyRsOpt

            // Do the same in the decoy result summary
            decoyRsm.resultSet = validatedDecoyRsOpt
          }
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

    // Validate the dataset tree recursively (children before their parent), starting from the selected dataset
    val leafDsIds = udsIdentDatasets.map(_.getId).toSet
    this._validateDatasetNode(udsEM, udsIdentTreeDS, leafDsIds, rsmByRsId)

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
   * Validates a dataset node recursively: its children are processed first,
   * then their result summaries are merged and validated into the node (see _mergeAndValidateDatasets).
   *
   * The leaf datasets (identification datasets, as returned by getIdentificationDatasets) are not validated here:
   * - their result sets have already been validated by _validateResultSet,
   * - rsmByRsId maps each leaf result set id to the resulting result summary.
   * A leaf result summary is removed from this map as soon as it is consumed by the parent merge, so that it can be garbage collected.
   *
   * Processing is driven by the dataset tree itself, not by the depth of the leaves, so unbalanced trees
   * (leaves at different depths) are supported, and datasets above the selected one are never modified.
   * Nodes without any leaf below them (e.g. empty aggregates) are skipped and left unchanged.
   *
   * @return the result summary of the node, or None if the node has no leaf below it
   */
  private def _validateDatasetNode(
    udsEM: EntityManager,
    udsDataset: UdsDataset,
    leafDsIds: Set[Long],
    rsmByRsId: LongMap[ResultSummary]
  ): Option[ResultSummary] = {

    // Leaf: retrieve the result summary produced by _validateResultSet
    // (removed from rsmByRsId, so that it is only referenced by the parent call until the parent merge is done)
    if (leafDsIds.contains(udsDataset.getId)) {
      val rsId = udsDataset.getResultSetId
      val rsmOpt = rsmByRsId.remove(rsId.longValue)
      require(rsmOpt.isDefined, s"no validated result summary found for result set #$rsId (dataset #${udsDataset.getId})")

      return rsmOpt
    }

    // Node: process the children first, then merge and validate their result summaries
    // (childRsms is local to this call: the child result summaries are released when this call returns)
    val udsChildren = Option(udsDataset.getChildren).map(_.toList).getOrElse(Nil)
    val childRsms = udsChildren.flatMap( this._validateDatasetNode(udsEM, _, leafDsIds, rsmByRsId) )

    if (childRsms.isEmpty) {
      this.logger.warn(s"Dataset #${udsDataset.getId} has no identification dataset below it and will be ignored")
      return None
    }

    this.logger.info(s"Merging and validating ${childRsms.length} child dataset(s) into dataset #${udsDataset.getId}")
    val mergedRsm = this._mergeAndValidateDatasets(childRsms)

    // Replace existing result set/summary ids
    // Note: to preserve existing mappings it is needed to clone the tree in a previous step
    udsDataset.setResultSetId(mergedRsm.getResultSetId)
    udsDataset.setResultSummaryId(mergedRsm.id)

    // TODO: store properties (do this in the merge RS/RSM service)
    //val mergedDataType = if( mergeResultSets ) "result_set" else "result_summary"
    //udsDataset.setSerializedProperties() // merged_data_type = mergedDataType

    udsEM.persist(udsDataset)

    Some(mergedRsm)
  }

  private def _mergeAndValidateDatasets(rsms: Seq[ResultSummary]): ResultSummary = {

    val mergedRs = if (this.mergeResultSets) {

      val rsList = rsms.map(_.resultSet.get)

      // Merge result sets
      // (the ResultSetMerger merges both target and decoy result sets, stores them and links the merged decoy to the merged target)
      val rsMerger = new ResultSetMerger(execContext, None, Some(rsList), Some(AdditionMode.AGGREGATION))
      rsMerger.runService()

      rsMerger.mergedResultSet

    } else {

      // Merge target result summaries
      val rsmMerger = new ResultSummaryMerger(
        execCtx = execContext,
        None,
        resultSummaries = Some(rsms),
        aggregationMode = Some(AdditionMode.AGGREGATION),
        storeRsm = false,
        useJPA = false
      )
      rsmMerger.runService()

      val mergedRs = rsmMerger.mergedResultSummary.resultSet.get

      mergedRs.decoyResultSet = rsmMerger.mergedResultSummary.decoyResultSummary.flatMap(_.resultSet)

      mergedRs
    }

    // Build Target/Decoy analyzer
    val tdAnalyzer = if (pepMatchValidator.isDefined && pepMatchValidator.get.validationFilter.isInstanceOf[IPeptideMatchSorter]) {
      BuildTDAnalyzer(useTdCompetition, mergedRs, Some(pepMatchValidator.get.validationFilter.asInstanceOf[IPeptideMatchSorter]))
    } else {
      BuildTDAnalyzer(useTdCompetition, mergedRs, None)
    }

    val linkTargetRsToFilter = (filter: IPeptideMatchFilter) => _linkTargetRsToFilter(filter, mergedRs)

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
      targetRs = mergedRs,
      tdAnalyzer = tdAnalyzer,
      pepMatchPreFilters = pepMatchFilters,
      pepMatchValidator = pepMatchValidator,
      protSetFilters = protSetFilters,
      protSetValidator = protSetValidator,
      peptideSetScoring = peptideSetScoring,
      storeResultSummary = false
    )
    rsValidator.run()

    val validatedTargetRsm = rsValidator.validatedTargetRsm
    val validatedDecoyRsmOpt = Option(validatedTargetRsm.decoyResultSummary).flatten

    // Store the validated RSMs (decoy first, so that the target RSM can reference it)
    if (this.mergeResultSets) {
      // The merged result sets have already been stored by the ResultSetMerger
      val rsmStorer = RsmStorer(execContext.getMSIDbConnectionContext)
      validatedDecoyRsmOpt.foreach(rsmStorer.storeResultSummary(_, execContext))
      rsmStorer.storeResultSummary(validatedTargetRsm, execContext)
    } else {
      // The merged result sets are still in memory: store them with the RSMs
      val storerContext = StorerContext(execContext)

      if (validatedDecoyRsmOpt.isDefined) {
        val childDecoyRsms = rsms.flatMap(rsm => Option(rsm.decoyResultSummary).flatten.toSeq)

        if (childDecoyRsms.isEmpty) {
          logger.warn("Merged decoy ResultSummary will not be stored: no child decoy ResultSummary found")
        } else {
          ResultSummaryMerger.storeResultSetAndSummary(
            storerContext,
            validatedDecoyRsmOpt.get,
            childDecoyRsms.map(_.id),
            childDecoyRsms.map(_.getResultSetId)
          )(execContext)
        }
      }

      ResultSummaryMerger.storeResultSetAndSummary(
        storerContext,
        validatedTargetRsm,
        rsms.map(_.id),
        rsms.map(_.getResultSetId)
      )(execContext)
    }

    validatedTargetRsm
  }

}