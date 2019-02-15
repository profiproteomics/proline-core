package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.collection._
import fr.profi.api.service.IService
import fr.proline.context._
import fr.proline.core.algo.msi.AdditionMode
import fr.proline.core.algo.msi.ResultSummaryAdder
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.algo.msi.scoring.PeptideSetScoreUpdater
import fr.proline.core.dal._
import fr.proline.core.dal.context._
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.msi.MsiDbResultSetRelationTable
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.RsmStorer

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

object ResultSummaryMerger {

  def loadResultSummary(rsmId: Long, execContext: IExecutionContext): ResultSummary = {

    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(execContext))


    val rsm = rsmProvider.getResultSummary(rsmId, loadResultSet = true)
    require(rsm.isDefined, "Unknown ResultSummary Id: " + rsmId)

    rsm.get
  }

}

class ResultSummaryMerger(
  execCtx: IExecutionContext,
  resultSummaryIds: Option[Seq[Long]],
  resultSummaries: Option[Seq[ResultSummary]],
  aggregationMode: Option[AdditionMode.Value] = None,
  useJPA: Boolean = false
) extends IService with LazyLogging {
  
  private val rsTable = MsiDbResultSetTable
  private val rsCols = rsTable.columns

  var mergedResultSummary: ResultSummary = _

  override protected def beforeInterruption: Unit = {
    logger.info("ResultSummaryMerger thread is going to be interrupted.")
  }

  def runService(): Boolean = {
    
    val storerContext = StorerContext(execCtx)
    var isMsiDbTxOk: Boolean = false

    try {
      isMsiDbTxOk = storerContext.getMSIDbConnectionContext.tryInTransaction {
        mergedResultSummary = if (resultSummaries.isDefined) {
          logger.info("Start merge from existing ResultSummaries")
          _mergeFromResultSummaries(resultSummaries.get, aggregationMode, storerContext)
        } else {
          logger.info("Start merge from ResultSummary Ids")
          _mergeFromResultSummaryIds(resultSummaryIds.get, aggregationMode, storerContext)
        }
      }

    } finally {

      if (storerContext != null) {
        storerContext.clearContext()
      }

    }

    isMsiDbTxOk
  }

  private def _mergeFromResultSummaries(
    resultSummaries: Seq[ResultSummary],
    aggregationMode: Option[AdditionMode.Value],
    storerContext: StorerContext
  ): ResultSummary = {

    val rsmCount = resultSummaries.length
    val targetRsmIds = new ArrayBuffer[Long](rsmCount)
    val decoyRsmIds = new ArrayBuffer[Long](rsmCount)
    val resultSummaryById = new LongMap[ResultSummary](rsmCount * 2)
    
    for (rsm <- resultSummaries) {
      targetRsmIds += rsm.id
      
      resultSummaryById.put(rsm.id, rsm)
      
      val decoyRsmId = rsm.getDecoyResultSummaryId
      if (decoyRsmId > 0L) decoyRsmIds += decoyRsmId
      
      val decoyRsmOpt = rsm.decoyResultSummary

      if (decoyRsmOpt.isDefined) {
        resultSummaryById.put(decoyRsmId, decoyRsmOpt.get)
      } else if (decoyRsmId > 0L) {
        logger.debug("Loading missing DECOY result summary automatically...")
        resultSummaryById.put(decoyRsmId, ResultSummaryMerger.loadResultSummary(decoyRsmId, storerContext))
      }
    }
    
    this._mergeAndStoreResultSummaries(
      targetRsmIds,
      decoyRsmIds,
      (rsmId: Long) => resultSummaryById(rsmId),
      aggregationMode,
      storerContext
    )
  }

  private def _mergeFromResultSummaryIds(
    resultSummaryIds: Seq[Long],
    aggregationMode: Option[AdditionMode.Value],
    storerContext: StorerContext
  ): ResultSummary = {
    
     val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)
     val decoyRsmIds = msiDbHelper.getDecoyRsmIds(resultSummaryIds)
    
     this._mergeAndStoreResultSummaries(
      resultSummaryIds,
      decoyRsmIds,
      (rsmId: Long) => ResultSummaryMerger.loadResultSummary(rsmId, execCtx),
      aggregationMode,
      storerContext
    )
  }
  
  private def _mergeAndStoreResultSummaries(
    targetRsmIds: Seq[Long],
    decoyRsmIds: Seq[Long],
    resultSummaryProvider: Long => ResultSummary,
    aggregationMode: Option[AdditionMode.Value],
    storerContext: StorerContext
  ): ResultSummary = {
    
    // Merge result summaries
    // FIXME: check that all peptide sets have the same score type ?
    val msiDbHelper = new MsiDbHelper(storerContext.getMSIDbConnectionContext)
    val scorings = msiDbHelper.getScoringsByResultSummaryIds(targetRsmIds)
    val peptideSetScoring = PepSetScoring.withName(scorings.head)
    val pepSetScoreUpdater = PeptideSetScoreUpdater(peptideSetScoring)

    logger.debug("TARGET ResultSummary Ids: " + targetRsmIds.mkString("[",", ","]"))
    logger.debug("DECOY ResultSummary Ids: " + decoyRsmIds.mkString("[",", ","]"))

    val targetRsmCount = targetRsmIds.length
    val decoyRsmCount = decoyRsmIds.length
    val additionMode = aggregationMode.getOrElse(AdditionMode.AGGREGATION)

    if (targetRsmCount != decoyRsmCount) {
      logger.warn(s"Inconsistent number of TARGET-DECOY ResultSummaries: $targetRsmCount TARGET RSM VS $decoyRsmCount DECOY RSM")
    }
    
    // --- MERGE TARGET RSMs --- //
    logger.debug("Merging TARGET ResultSummaries ...")
    
    val childTargetRsIds = new ArrayBuffer[Long](targetRsmCount)
    var childRsmPropsOpt = Option.empty[ResultSummaryProperties]

    val rsmAdder = new ResultSummaryAdder(
      resultSetId = ResultSummary.generateNewId(),
      isDecoy = false,
      pepSetScoreUpdater = pepSetScoreUpdater,
      additionMode = additionMode
    )

    for (targetRsmId <- targetRsmIds) {

      val targetRsm = resultSummaryProvider(targetRsmId)
      rsmAdder.addResultSummary(targetRsm)

      if (childRsmPropsOpt.isEmpty) {
        childRsmPropsOpt = targetRsm.properties
      }

      // Retrieve child RS id
      val targetRsOpt = targetRsm.resultSet
      require(targetRsOpt.isDefined, "ResultSummary must contain a valid ResultSet")

      childTargetRsIds += targetRsOpt.get.id
    }

    val mergedTargetRsm = rsmAdder.toResultSummary()

    val rsmProperties = mergedTargetRsm.properties.getOrElse(new ResultSummaryProperties())
    rsmProperties.setMergeMode(Some(additionMode.toString))
    mergedTargetRsm.properties = Some(rsmProperties)
    
    // --- MERGE DECOY RSMs --- //
    val mergedDecoyRsmOpt = if (decoyRsmCount == 0) None
    else {
      logger.debug("Merging DECOY ResultSummaries ...")
      
      val childDecoyRsIds = new ArrayBuffer[Long](decoyRsmCount)

      val decoyRsmAdder = new ResultSummaryAdder(
        resultSetId = ResultSummary.generateNewId(),
        isDecoy = true,
        pepSetScoreUpdater = pepSetScoreUpdater,
        additionMode = additionMode
      )
      
      for (decoyRsmId <- decoyRsmIds) {
        
        val decoyRsm = resultSummaryProvider(decoyRsmId)
        decoyRsmAdder.addResultSummary(decoyRsm)
        
        // Retrieve child RS id
        val decoyRsOpt = decoyRsm.resultSet
        require(decoyRsOpt.isDefined, "ResultSummary must contain a valid ResultSet")
          
        childDecoyRsIds += decoyRsOpt.get.id
      }

      val decoyRsm = decoyRsmAdder.toResultSummary()
      val rsmProperties = decoyRsm.properties.getOrElse(new ResultSummaryProperties())
      rsmProperties.setMergeMode(Some(additionMode.toString))
      decoyRsm.properties = Some(rsmProperties)

      logger.debug("Storing DECOY ResultSummary ...")
      _storeResultSummary(storerContext, decoyRsm, decoyRsmIds, childDecoyRsIds)

      // Update decoy RSM / RS ids
      mergedTargetRsm.setDecoyResultSummaryId(decoyRsm.id)
      mergedTargetRsm.resultSet.get.setDecoyResultSetId(decoyRsm.getResultSetId)
      
      Some(decoyRsm)
    }
    
    // Update merged RSM validation properties
    if (childRsmPropsOpt.isDefined && childRsmPropsOpt.get.validationProperties.isDefined) {
      
      val childRsmValProps = childRsmPropsOpt.get.validationProperties.get
      //val leavesPsmCount = mergedTargetRsm.peptideInstances.foldLeft(0) { case (0,p) => 0 + p.totalLeavesMatchCount }
      val peptidesCount = mergedTargetRsm.resultSet.get.peptideMatches.length
      val protSetsCount = mergedTargetRsm.proteinSets.length
      val decoyPeptidesCountOpt = mergedDecoyRsmOpt.map(_.resultSet.get.peptideMatches.length)
      val decoyProtSetsCountOpt = mergedDecoyRsmOpt.map(_.proteinSets.length)

      // Compute merged RSM properties
      val valProps = RsmValidationProperties(
        params = childRsmValProps.params,
        results = RsmValidationResultsProperties(
          peptideResults = Some(
            RsmValidationResultProperties(
              targetMatchesCount = peptidesCount,
              decoyMatchesCount = decoyPeptidesCountOpt
            )
          ),
          proteinResults = Some(
            RsmValidationResultProperties(
              targetMatchesCount = protSetsCount,
              decoyMatchesCount = decoyProtSetsCountOpt,
              fdr = decoyProtSetsCountOpt.map(_.toFloat/protSetsCount)
            )
          )
        )
      )
      
      // Update merged RSM properties
      val rsmProps = mergedTargetRsm.properties.getOrElse(ResultSummaryProperties())
      rsmProps.validationProperties = Some(valProps)
      mergedTargetRsm.properties = Some(rsmProps)
    }

    logger.debug("Storing TARGET ResultSummary ...")
    _storeResultSummary(storerContext, mergedTargetRsm, targetRsmIds, childTargetRsIds)

    mergedTargetRsm
  }

  private def _storeResultSummary(
    storerContext: StorerContext,
    tmpMergedResultSummary: ResultSummary,
    childRsmIds: Seq[Long],
    childRsIds: Seq[Long]
  ) {
    
    require( childRsmIds.nonEmpty, "childRsmIds is empty")
    require( childRsIds.nonEmpty, "childRsIds is empty")
    
    val distinctRsmIds = childRsmIds.distinct
    val distinctRsIds = childRsIds.distinct

    DoJDBCWork.withEzDBC(storerContext.getMSIDbConnectionContext) { msiEzDBC =>

      val proteinSets = tmpMergedResultSummary.proteinSets
      logger.debug("ProteinSets count:" + tmpMergedResultSummary.proteinSets.length)

      // Validate all protein sets
      proteinSets.foreach { _.isValidated = true }

      // Retrieve the merged result set
      val mergedResultSet = tmpMergedResultSummary.resultSet.get
      val peptideInstances = tmpMergedResultSummary.peptideInstances
//      val pepInstanceByPepId = peptideInstances.toLongMapWith { pepInst => pepInst.peptide.id -> pepInst }

      // Map peptide matches and proptein matches by their tmp id
      val mergedPepMatchByTmpId = mergedResultSet.peptideMatches.mapByLong(_.id)
      val protMatchByTmpId = mergedResultSet.proteinMatches.mapByLong(_.id)

      logger.debug("Storing ResultSet ...")

      val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext, useJPA)
      rsStorer.storeResultSet(mergedResultSet, storerContext)

      executeOnProgress() //execute registered action during progress

      // Update peptide match ids referenced in peptide instances
      for (pepInstance <- peptideInstances) {
        
        val oldPepMatchIds = pepInstance.peptideMatchIds
        val oldPepMatchPropsById = pepInstance.peptideMatchPropertiesById

        // Retrieve new pep match ids and re-map peptide match RSM properties with the new ids
        val newPepMatchIds = new ArrayBuffer[Long](pepInstance.getPeptideMatchIds().length)
        val newPepMatchPropsById = new LongMap[PeptideMatchResultSummaryProperties]

        pepInstance.bestPeptideMatchId = mergedPepMatchByTmpId(pepInstance.bestPeptideMatchId).id

        for (oldPepMatchId <- oldPepMatchIds) {
          val newPepMatchId = mergedPepMatchByTmpId(oldPepMatchId).id
          newPepMatchIds += newPepMatchId

          if (oldPepMatchPropsById != null) {
            newPepMatchPropsById.put( newPepMatchId, oldPepMatchPropsById(oldPepMatchId) )
          }
        }

        pepInstance.peptideMatchIds = newPepMatchIds.toArray

        if (oldPepMatchPropsById != null) {
          pepInstance.peptideMatchPropertiesById = newPepMatchPropsById.toMap
        }

      }

      // Update protein match ids referenced in peptide sets
      val peptideSets = tmpMergedResultSummary.peptideSets
      for (peptideSet <- peptideSets) {
        peptideSet.proteinMatchIds = peptideSet.proteinMatchIds.map { protMatchByTmpId(_).id }
      }

      // Update protein match ids referenced in protein sets
      for (proteinSet <- proteinSets) {
        proteinSet.samesetProteinMatchIds = proteinSet.getSameSetProteinMatchIds.map { protMatchByTmpId(_).id }
        proteinSet.subsetProteinMatchIds = proteinSet.getSubSetProteinMatchIds.map { protMatchByTmpId(_).id }
      }

      // Store result summary
      logger.info("store result summary...")
      RsmStorer(execCtx.getMSIDbConnectionContext).storeResultSummary(tmpMergedResultSummary, execCtx)

      executeOnProgress() //execute registered action during progress
      
      // --- Create links between parent and child result summaries --- //
      val parentRsmId = tmpMergedResultSummary.id
      logger.debug("Linking child Result Summaries to their parent #" + parentRsmId)

      // Update link between RS & RSM ( mergedResultSet.mergedResultSummaryId )
      mergedResultSet.mergedResultSummaryId = tmpMergedResultSummary.id

      val rsUpdateQuery = s"UPDATE ${rsTable.name} SET ${rsCols.MERGED_RSM_ID} = ? WHERE ${rsCols.ID} = ? "
      msiEzDBC.executePrepared(rsUpdateQuery){stmt => stmt.executeWith(tmpMergedResultSummary.id, mergedResultSet.id)}
      
      // Insert result set relation between parent and its children
      val rsmRelationInsertQuery = MsiDbResultSummaryRelationTable.mkInsertQuery()

      msiEzDBC.executeInBatch(rsmRelationInsertQuery) { stmt =>
        for (childRsmId <- distinctRsmIds) stmt.executeWith(parentRsmId, childRsmId)
      }

      // --- Create links between parent and child result sets --- //
      val parentRsId = tmpMergedResultSummary.getResultSetId
      logger.debug("Linking child Result Sets to their parent #" + parentRsId)

      // Insert result set relation between parent and its children
      val rsRelationInsertQuery = MsiDbResultSetRelationTable.mkInsertQuery()

      msiEzDBC.executeInBatch(rsRelationInsertQuery) { stmt =>
        for (childRSId <- distinctRsIds) stmt.executeWith(parentRsId, childRSId)
      }
      
    }
    executeOnProgress() //execute registered action during progress

  }

}
