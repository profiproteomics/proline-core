package fr.proline.core.service.msq.quantify

import java.io.File
import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.HashMap
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.algo.lcms.FeatureMappingParams
import fr.proline.core.algo.lcms.LabelFreeQuantConfig
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.{Instrument,Peptide,PeptideMatch}
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.repository.IDataStoreConnectorFactory

class Ms2DrivenLabelFreeFeatureQuantifier(
  val executionContext: IExecutionContext,
  val experimentalDesign: ExperimentalDesign,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val quantConfig: LabelFreeQuantConfig
) extends AbstractLabelFreeFeatureQuantifier {
  
  lazy val runIdByRsmId = {
    Map() ++ udsMasterQuantChannel.getQuantitationChannels().map { udsQC =>
      udsQC.getIdentResultSummaryId() -> udsQC.getRun().getId()
    }
  }
  
  // TODO: try to handle PSMs with rank > 1
  lazy val (peptideByRunIdAndScanNumber, psmByRunIdAndScanNumber) = {
    
    val peptideMap = new collection.mutable.HashMap[Long, HashMap[Int, Peptide]]()
    val psmMap = new collection.mutable.HashMap[Long, HashMap[Int, PeptideMatch]]()
    
    for( rsm <- this.identResultSummaries ) {
      val runId = runIdByRsmId(rsm.id)
      val valPepMatchIds = rsm.peptideInstances.flatMap( _.getPeptideMatchIds )
      val pepMatchById = rsm.resultSet.get.getPeptideMatchById
      
      for( valPepMatchId <- valPepMatchIds ) {
        val valPepMatch = pepMatchById(valPepMatchId)
        // FIXME: how to deal with other ranked PSMs ?
        if( valPepMatch.rank == 1 ) {
          val spectrumId = valPepMatch.getMs2Query.spectrumId

          val scanNumber = this.scanNumberBySpectrumId(spectrumId)
          psmMap.getOrElseUpdate(runId, new HashMap[Int, PeptideMatch])(scanNumber) = valPepMatch
          peptideMap.getOrElseUpdate(runId, new HashMap[Int, Peptide])(scanNumber) = valPepMatch.peptide
        } else {
          this.logger.trace(s"Peptide ${valPepMatch.peptide.sequence} id=${valPepMatch.peptideId} will be ignored (rank > 1)")
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

}