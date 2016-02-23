package fr.proline.core.service.msq.quantify

import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.util.primitives._
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.LabelFreeQuantConfig
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.{Peptide, PeptideMatch}
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.model.msq.SpectralCountProperties
import fr.proline.core.om.model.msq.MasterQuantChannelProperties

class Ms2DrivenLabelFreeFeatureQuantifier(
  val executionContext: IExecutionContext,
  val experimentalDesign: ExperimentalDesign,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val quantConfig: LabelFreeQuantConfig
) extends AbstractLabelFreeFeatureQuantifier {
  
  lazy val validMergedRSMPeptides: Seq[Long] = {
    mergedResultSummary.peptideInstances.filter(_.validatedProteinSetsCount >0).map(_.peptideId).toSeq
    
  }
  
  lazy val runIdByRsmId = {
    Map() ++ udsMasterQuantChannel.getQuantitationChannels().map { udsQC =>
      udsQC.getIdentResultSummaryId() -> udsQC.getRun().getId()
    }
  }
  
  lazy val (peptideByRunIdAndScanNumber, psmByRunIdAndScanNumber) = {
    
    val peptideMap = new collection.mutable.HashMap[Long, HashMap[Int, Peptide]]()
    val psmMap = new collection.mutable.HashMap[Long, HashMap[Int, ArrayBuffer[PeptideMatch]]]()
    
    for( rsm <- this.identResultSummaries ) {
      val runId = runIdByRsmId(rsm.id)
      val valPepMatchIds = rsm.peptideInstances.flatMap( _.getPeptideMatchIds )
      val pepMatchById = rsm.resultSet.get.getPeptideMatchById
      
      for( valPepMatchId <- valPepMatchIds ) {
        val valPepMatch = pepMatchById(valPepMatchId)
        if(validMergedRSMPeptides.contains(valPepMatch.peptideId)) {         
         val spectrumId = valPepMatch.getMs2Query.spectrumId
         val scanNumber = this.scanNumberBySpectrumId(spectrumId)
         psmMap.getOrElseUpdate(runId, new HashMap[Int, ArrayBuffer[PeptideMatch]]).getOrElseUpdate(scanNumber, ArrayBuffer[PeptideMatch]()) += valPepMatch
         peptideMap.getOrElseUpdate(runId, new HashMap[Int, Peptide])(scanNumber) = valPepMatch.peptide
        }
      }
    }
    
    (peptideMap.toMap, psmMap.toMap)
  }
  
  // Extract the LC-MS map set
  lazy val lcmsMapSet: MapSet = {
    val mapSetExtractor = new ExtractMapSet(this.lcmsDbCtx,quantConfig, Some(peptideByRunIdAndScanNumber), Some(psmByRunIdAndScanNumber) )
    mapSetExtractor.run()
    mapSetExtractor.extractedMapSet
  }
  
  // Add processings specific to the MS2 driven strategy here
  override protected def quantifyMasterChannel(): Unit = {
      
    // Retrieve LC-MS maps ids mapped by the run id
    val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map( lcmsMap => lcmsMap.runId.get -> lcmsMap.id )
    
    // Update the LC-MS map id of each master quant channel
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    for( udsQuantChannel <- udsQuantChannels) {
      udsQuantChannel.setLcmsMapId( lcMsMapIdByRunId( udsQuantChannel.getRun().getId ) )
      udsEm.merge(udsQuantChannel)
    }
    
    // Update the map set id of the master quant channel
    udsMasterQuantChannel.setLcmsMapSetId(lcmsMapSet.id)
    udsEm.merge(udsMasterQuantChannel)
    
    super.quantifyMasterChannel()
  }

  override protected def getMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    if (quantConfig.parentRsmId.isEmpty) {
      existingMergedRSM = false
      createMergedResultSummary(msiDbCtx)
    } else {
      existingMergedRSM = true
      val pRsmId = quantConfig.parentRsmId.get
      this.logger.debug("Read Merged RSM with ID " + pRsmId)

      // Instantiate a RSM provider
      val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx = psDbCtx, udsDbCtx = null)
      val idfRSM = rsmProvider.getResultSummary(pRsmId, true).get
      val mqchProperties = new MasterQuantChannelProperties(identResultSummaryId = quantConfig.parentRsmId, identDatasetId = quantConfig.parentDsId, spectralCountProperties = None)
      udsMasterQuantChannel.setSerializedProperties(ProfiJson.serialize(mqchProperties))
      idfRSM
    }
  }

}