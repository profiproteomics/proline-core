package fr.proline.core.service.msq

import java.util.{ArrayList, HashSet}

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msq.config._
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.STR_F063442_F122817_MergedRSMs
import fr.proline.core.om.model.msq
import fr.proline.core.om.model.msq.{BiologicalGroup, MasterQuantChannel, QuantChannel, SimplifiedExperimentalDesign}
import fr.proline.core.orm.uds._
import fr.proline.core.service.msq.quantify.WeightedSpectralCountQuantifier
import fr.proline.repository.DriverType
import org.junit.Assert._
import org.junit.Test

object WeightedSCQuantifierTest extends AbstractDatastoreTestCase with StrictLogging {

  // Define required parameters
  override val driverType = DriverType.H2
  override val dbUnitResultFile = STR_F063442_F122817_MergedRSMs
  override val useJPA = true

  val targetRSMId: Long = 33L

}
  
class WeightedSCQuantifierTest extends StrictLogging {
  
  val executionContext = WeightedSCQuantifierTest.executionContext
  val targetRSMId = WeightedSCQuantifierTest.targetRSMId

  @Test
  def quantifyRSMSC() {

    val weightRefRSMIds = Array(33L)
    val spCountCfg = new SpectralCountConfig(identResultSummaryId = Some(targetRSMId), identDatasetId = None, weightsRefRsmIds=weightRefRSMIds)

    val udsEm = executionContext.getUDSDbConnectionContext.getEntityManager
    udsEm.getTransaction().begin()

    //Create Exp Design
    val pj: Project = udsEm.find(classOf[Project], 1l)
    val qtDS = new Dataset(pj)
    qtDS.setNumber(2)
    qtDS.setName("WSC test DS")
    qtDS.setType(Dataset.DatasetType.QUANTITATION)
    qtDS.setChildrenCount(2)

    //Create Sample Analysis
    val splAnalysis1 = new SampleAnalysis()
    splAnalysis1.setDataset(qtDS)

    val splAnalysis2 = new SampleAnalysis()
    splAnalysis2.setDataset(qtDS)
    
    val splAnalysis3 = new SampleAnalysis()
    splAnalysis3.setDataset(qtDS)
    
    
    //Create BiologicalSample
    val bioSpl1 = new BiologicalSample()
    bioSpl1.setName("WSC Test BioSpl")
    bioSpl1.setNumber(1)
    bioSpl1.setDataset(qtDS)

    //Create link between SampleAnalysis  & BiologicalSample
    val replicate2Sample1 = new BiologicalSplSplAnalysisMap()
    replicate2Sample1.setBiologicalSample(bioSpl1)
    replicate2Sample1.setSampleAnalysis(splAnalysis1)
    replicate2Sample1.setSampleAnalysisNumber(1)
    
    val replicate2Sample2 = new BiologicalSplSplAnalysisMap()
    replicate2Sample2.setBiologicalSample(bioSpl1)
    replicate2Sample2.setSampleAnalysis(splAnalysis2)
    replicate2Sample2.setSampleAnalysisNumber(2)

    val replicate2Sample3 = new BiologicalSplSplAnalysisMap()
    replicate2Sample3.setBiologicalSample(bioSpl1)
    replicate2Sample3.setSampleAnalysis(splAnalysis3)
    replicate2Sample3.setSampleAnalysisNumber(3)
    
    val allSplAnalysis = new ArrayList[BiologicalSplSplAnalysisMap](3)
    allSplAnalysis.add(replicate2Sample1)
    allSplAnalysis.add(replicate2Sample2)
    allSplAnalysis.add(replicate2Sample3)
    val allBioSplReplicateMap = new HashSet[BiologicalSplSplAnalysisMap](allSplAnalysis)
    
    bioSpl1.setBiologicalSplSplAnalysisMap(allSplAnalysis)
    splAnalysis1.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)
    splAnalysis2.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)
    splAnalysis3.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)

    //Create QuantitationChannel
    val qCh1 = new QuantitationChannel()
    qCh1.setIdentResultSummaryId(1)
    qCh1.setContextKey("1.1")
    qCh1.setQuantitationDataset(qtDS)
    qCh1.setName("QChannel 1.1")

    val qCh2 = new QuantitationChannel()
    qCh2.setIdentResultSummaryId(2)
    qCh2.setContextKey("1.2")
    qCh2.setQuantitationDataset(qtDS)
    qCh2.setName("QChannel 1.2")

    val qCh3 = new QuantitationChannel()
    qCh3.setIdentResultSummaryId(targetRSMId)
    qCh3.setContextKey("1.3")
    qCh3.setQuantitationDataset(qtDS)
    qCh3.setName("QChannel 1.3")

    //Create MasterQuantitationChannel
    val mqCh = new MasterQuantitationChannel()
    mqCh.setName("WSC Test")
    mqCh.setNumber(1)

    //Create link between QuantitationChannel  & BiologicalSample&SampleAnalysis
    val qChs = new ArrayList[QuantitationChannel](2)
    qChs.add(qCh1)
    qChs.add(qCh2)
    qChs.add(qCh3)
    qCh1.setNumber(1)
    qCh1.setSampleReplicate(splAnalysis1)
    qCh1.setBiologicalSample(bioSpl1)
    qCh2.setNumber(2)
    qCh2.setSampleReplicate(splAnalysis2)
    qCh2.setBiologicalSample(bioSpl1)
    qCh3.setNumber(3)
    qCh3.setSampleReplicate(splAnalysis3)
    qCh3.setBiologicalSample(bioSpl1)
    bioSpl1.setQuantitationChannels(qChs)
    splAnalysis1.setQuantitationChannels(qChs)// a verifier vds

    //Create link between MasterQuantitationChannel  & QuantitationChannels
    mqCh.setQuantitationChannels(qChs)
    qCh2.setMasterQuantitationChannel(mqCh)
    qCh3.setMasterQuantitationChannel(mqCh)
    qCh1.setMasterQuantitationChannel(mqCh)

    //Create link between MasterQCh  & Dataset
    mqCh.setDataset(qtDS)
    val mqChs = new ArrayList[MasterQuantitationChannel](1)
    mqChs.add(mqCh)
    qtDS.setMasterQuantitationChannels(mqChs)
    qtDS.setQuantitationChannels(qChs)

    udsEm.persist(qtDS)
    udsEm.persist(splAnalysis1)
    udsEm.persist(splAnalysis2)
    udsEm.persist(splAnalysis3)
    udsEm.persist(bioSpl1)

    udsEm.getTransaction().commit()

    // build a fake OM experimental design. A better option should
    val experimentalDesign = new SimplifiedExperimentalDesign(
      biologicalSamples = Array.empty[msq.BiologicalSample],
      biologicalGroups = Array.empty[BiologicalGroup],
      masterQuantChannels = Array {
        new MasterQuantChannel(id = 0, number = 1, identResultSummaryId = Some(targetRSMId), quantChannels = Array.empty[QuantChannel])
      }
    )

    val wsCalculator = new WeightedSpectralCountQuantifier(executionContext = executionContext, experimentalDesign = experimentalDesign.toExperimentalDesign(), udsMasterQuantChannel = mqCh, quantConfig = spCountCfg)

    wsCalculator.quantify
    assertNotNull(mqCh.getQuantResultSummaryId())

  }

}

