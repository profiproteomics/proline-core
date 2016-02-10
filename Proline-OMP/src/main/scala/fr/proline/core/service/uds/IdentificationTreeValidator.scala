package fr.proline.core.service.uds

import javax.persistence.EntityManager

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.LazyLogging

import fr.proline.api.service.IService
import fr.proline.context.{IExecutionContext, DatabaseConnectionContext}
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.filtering._
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal.context._
import fr.proline.core.dal.helper.{MsiDbHelper, UdsDbHelper}
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.orm.uds.{Dataset => UdsDataset}
import fr.proline.core.service.msi.{ResultSetValidator, ResultSetMerger, ResultSummaryMerger, ValidationConfig}

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
  // TODO: uncomment this require when LCMS ORM is implemented
  //require( execContext.isJPA, "a JPA execution context is needed" )  

  private val udsDbCtx = execContext.getUDSDbConnectionContext()
  private val psDbCtx = execContext.getPSDbConnectionContext()
  private val msiDbCtx = execContext.getMSIDbConnectionContext()
  private val msiDbHelper = new MsiDbHelper(msiDbCtx)

  // TODO: remove this require when LCMS ORM is implemented
  require(udsDbCtx.isJPA, "a JPA execution context is needed")

  /*override def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    execJpaContext.closeAll()
  }*/

  def runService(): Boolean = {

    // Retrieve UDSdb entity manager
    val udsEM = udsDbCtx.getEntityManager()

    // Retrieve identification datasets ids
    val udsIdentTreeDS = udsEM.find(classOf[UdsDataset], identTreeId)
    val projectId = udsIdentTreeDS.getProject.getId
    val udsIdentDatasets = udsIdentTreeDS.getIdentificationDatasets()

    // Retrieve target result sets ids
    val targetRsIds: Array[Long] = udsIdentDatasets.map(_.getResultSetId.longValue).toArray
    this.logger.debug("rs ids count = " + targetRsIds.length)
    this.logger.debug("first RS id = " + targetRsIds(0))

    // Retrieve decoy RS ids if they exists
    /*val decoyRsIdsAsOpts = targetRsIds.map { msiDbHelper.getDecoyRsId(_) } filter { _.isDefined }

    // Check that if we have decoy data we have the same number of target and decoy result sets
    val (nbTargetRs, nbDecoyRs) = (targetRsIds.length, decoyRsIdsAsOpts.length)
    if (nbDecoyRs > 0 && nbDecoyRs != nbTargetRs) {
      throw new Exception("missing decoy result set for one of the provided result sets")
    }
    this.logger.debug("decoy rs ids count = " + nbDecoyRs)
    
    // Load result sets
    val rsIds = targetRsIds ++ decoyRsIdsAsOpts.map { _.get }
    val (targetRsList, decoyRsList) = this._loadResultSets(projectId, rsIds).partition { _.isDecoy == false }
    //val targetRsById = targetRsList.map( drs => drs.id -> drs ).toMap
    val decoyRsById = decoyRsList.map(drs => drs.id -> drs).toMap*/
    
    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    // Link decoy RS to target RS and map RSM by RS id
    val rsmByRsId = new HashMap[Long, ResultSummary]
    for (targetRsId <- targetRsIds) {
      val decoyRsIdOpt = msiDbHelper.getDecoyRsId(targetRsId)
      require( decoyRsIdOpt.isDefined, "decoyrRsIdOpt is not defined" )
      
      // TODO: use the maxRank filter here
      val tdResultSets = rsProvider.getResultSets( Array(targetRsId,decoyRsIdOpt.get) )
      require( tdResultSets.length == 2, "target/decoy results have not been fetched from MSIdb")
      
      // Link decoy RS to target RS
      val targetRS = tdResultSets.find( _.isDecoy == false ).get
      targetRS.decoyResultSet = tdResultSets.find( _.isDecoy )
      
      this.logger.debug("validating target RS")
      val rsm = this._validateResultSet(targetRS)
      
      if( mergeResultSets == false ) {
        
        this.logger.debug("remove non-validated entities from RS and decoy RS")

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
      
      rsmByRsId += targetRS.id -> rsm
    }

    // Link result summaries to the corresponding dataset id
    //val rsmByDsId = new HashMap[Int,ResultSummary]
    for (udsIdentDataset <- udsIdentDatasets) {
      val rsId = udsIdentDataset.getResultSetId
      udsIdentDataset.setResultSummaryId(rsmByRsId(rsId).id)

      udsEM.persist(udsIdentDataset)
      //rsmByDsId += udsIdentDataset.getId.toInt -> rsmByRsId(rsId)
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

    //Test if there are some IFilterNeedingResultSet in Flilters.
    if(pepMatchFilters.isDefined){
      pepMatchFilters.get.foreach(currentFilter=>{
        if(currentFilter.isInstanceOf[IFilterNeedingResultSet])
          currentFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rs)
      })
    }    
    if(pepMatchValidator.isDefined && pepMatchValidator.get.validationFilter.isInstanceOf[IFilterNeedingResultSet])
      pepMatchValidator.get.validationFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(rs)
      
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

  /**
   * Validates dataset tree recursively from leaves to root
   */
  private def _validateDatasetTree(udsEM: EntityManager, udsDatasets: List[UdsDataset], rsmByRsId: HashMap[Long, ResultSummary]) {
    if (udsDatasets.length == 1 && udsDatasets(0).getParentDataset == null) return

    this.logger.warn("validating tree node with " + udsDatasets.length + " dataset(s)")

    // Group RSMs by parent DS id
    val rsmsByUdsParentDs = new HashMap[UdsDataset, ArrayBuffer[ResultSummary]]
    for (dataset <- udsDatasets) {
      val udsParentDs = dataset.getParentDataset
      val rsm = rsmByRsId(dataset.getResultSetId)
      rsmsByUdsParentDs.getOrElseUpdate(udsParentDs, new ArrayBuffer[ResultSummary]()) += rsm
    }

    // Define some vars
    val newUdsParentDatasets = new ArrayBuffer[UdsDataset]
    val newRsmByRsId = new HashMap[Long, ResultSummary]

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

      // TODO: store properties
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

      if (decoyRsList.length > 0) {
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

      //Test if there are some IFilterNeedingResultSet in Flilters.
      if (pepMatchFilters.isDefined) {
        pepMatchFilters.get.foreach(currentFilter => {
          if (currentFilter.isInstanceOf[IFilterNeedingResultSet])
            currentFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(mergedTargetRs)
        })
      }
      if (pepMatchValidator.isDefined && pepMatchValidator.get.validationFilter.isInstanceOf[IFilterNeedingResultSet])
        pepMatchValidator.get.validationFilter.asInstanceOf[IFilterNeedingResultSet].setTargetRS(mergedTargetRs)

      
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
      val rsmMerger = new ResultSummaryMerger(execCtx = execContext, None, resultSummaries = Some(rsms), aggregationMode = Some(AdditionMode.AGGREGATION))
      rsmMerger.runService()

      // TODO: merge decoy rsms

      rsmMerger.mergedResultSummary
    }

  }

}