package fr.proline.core.service.msq

import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.serialization.ProfiJson.deserialize
import fr.proline.core.algo.lcms.MqPepIonAbundanceSummarizingMethod
import fr.proline.core.algo.msq.config.LabelFreeQuantConfig
import fr.proline.core.algo.msq.summarizing.LabelFreeEntitiesSummarizer
import fr.proline.core.dal._
import fr.proline.core.dbunit._
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi
import fr.proline.core.om.model.msq.{ExperimentalDesign, ExperimentalDesignSetup, MasterQuantChannel, MasterQuantPeptideIon}
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.Settings
import fr.proline.core.orm.msi.{MasterQuantPeptideIon => OrmMasterQuantPeptideIon}
import fr.proline.core.orm.msi.ObjectTree
import fr.proline.core.orm.uds.{Dataset, MasterQuantitationChannel, QuantitationChannel}
import fr.proline.core.service.lcms.io.{ExtractMapSet, PeakelsDetector}
import fr.proline.core.service.msq.quantify.MasterQuantChannelEntityCache
import fr.proline.core.service.uds.CreateQuantitation
import fr.proline.repository.DriverType
import org.junit.{Assert, BeforeClass, Ignore, Test}

import java.io.File
import javax.persistence.TypedQuery
import scala.collection.JavaConverters._
import scala.collection.mutable

object QuantifyGlobalTest extends AbstractDatastoreTestCase  with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = SmallRuns_XIC

  override val useJPA: Boolean = true

  var lcmsDBTestCase: LCMSDatabaseTestCase = null

  @BeforeClass
  @throws(classOf[Exception])
  override def setUp(): Unit = {
    super.setUp()
    lcmsDBTestCase.loadDataSet(dbUnitResultFile.lcmsDbDatasetPath)
  }

  /*
   * redefine initDBsDBManagement to init LCMS database for this test
   */
  override def initDBsDBManagement(driverType: DriverType) : Unit=  {

    m_testCaseLock.synchronized {

      if (m_toreDown) {
        throw new IllegalStateException("TestCase ALREADY torn down")
      }

      logger.info("Creating UDS and MSI/LCMS Database TestCases")

      udsDBTestCase = new UDSDatabaseTestCase(driverType)
      udsDBTestCase.initDatabase()

      msiDBTestCase = new MSIDatabaseTestCase(driverType)
      msiDBTestCase.initDatabase()

      lcmsDBTestCase = new LCMSDatabaseTestCase(driverType)
      lcmsDBTestCase.initDatabase()

      dsConnectorFactoryForTest = new DataStoreConnectorFactoryForTest(
        udsDBTestCase.getConnector,
        msiDBTestCase.getConnector,
        lcmsDBTestCase.getConnector,
        initialize = false
      )

    } // End of synchronized block on m_testCaseLock

  }
}

@Test
class QuantifyGlobalTest extends StrictLogging {

  // Ident RSM Ids
  val rsm1Id = 2L
  val rsm2Id = 4L
  val mergedRSM1Id = 6L
  val mergedDSId = 2L

  // Quant RSM/DS Ids
  val singleQRsmId = 7L
  val singleQDSId = 5L
  val twoRunsQRsmId = 8L
  val twoRunsQDSId = 6L

  //Global ref
  val prjId = 1L
  val lfMethodId = 3L

  val testExecutionContext = QuantifyGlobalTest.executionContext
  val udsEm =QuantifyGlobalTest.udsDBTestCase.getConnector.createEntityManager()
  val msiEm = QuantifyGlobalTest.msiDBTestCase.getConnector.createEntityManager()

//  @Test
//  def test52(): Unit = {
////    logger.info("\n\n\n\n ****************** 1 : \n\n\n\n"+Settings.renderConfigAsString())
//
//    logger.info("\n\n\n\n ****************** 2 \n\n\n\n"+Settings.renderCurratedConfigAsString())
//  }

  private def createLFQuantExpDesign(expDesign : ExperimentalDesign) : Long = {

    // Store quantitation in the UDSdb
    val quantiCreator = new CreateQuantitation(
      executionContext = testExecutionContext,
      name = "Test Quant",
      description = "Test desc",
      projectId = prjId,
      methodId = lfMethodId,
      experimentalDesign = expDesign
    )
    quantiCreator.runService()

   quantiCreator.getUdsQuantitation.getId
  }

  private def computePepIonAb(lcmsMapSet: MapSet, cache: MasterQuantChannelEntityCache, masterQc : MasterQuantChannel, qRsm : msi.ResultSummary):  List[MasterQuantPeptideIon] = {
    val ms2ScanNumbersByFtId = {
      val rawMapIds = lcmsMapSet.getRawMapIds()
      val lcMsScans = cache.getLcMsScans(rawMapIds)
      cache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)
    }

   val lfSummarizer = new LabelFreeEntitiesSummarizer(
     lcmsMapSet = lcmsMapSet,
     spectrumIdByRsIdAndScanNumber = cache.spectrumIdByRsIdAndScanNumber,
     ms2ScanNumbersByFtId = ms2ScanNumbersByFtId,
     abundanceSummarizerMethod =  MqPepIonAbundanceSummarizingMethod.SUM)

    val qttPep = lfSummarizer.computeMasterQuantPeptides(masterQc, qRsm, cache.quantChannelResultSummaries)
    val qttIons = qttPep.flatMap(_.masterQuantPeptideIons).toList
    logger.info(" == Compute Pep quant Result :  nb quant pep :  "+qttPep.length+" nb quant ions "+qttIons.length )
//    val pepIById = qRsm.getPeptideInstanceById
//    val qChIds = masterQc.quantChannels.map(_.id)
//    qttIons.foreach(qttIon => {
//      val pep = pepIById(qttIon.peptideInstanceId.get).peptide
//      System.out.println("Seq\t"+pep.sequence+"\tptm\t"+pep.ptmString+"\tquant\t"+qttIon.getAbundancesForQuantChannels(qChIds).mkString("$\t"))
//    })
    qttIons
  }

  private def getQRSMPeptideIonsByPepIdAndCharge(rsmId: Long) :  Map[(java.lang.Long, Int), OrmMasterQuantPeptideIon] = {
    //Read Q Peptides Ions  to compare with generated ones
    val pepIonsQuery: TypedQuery[OrmMasterQuantPeptideIon] = msiEm.createQuery("select pions from MasterQuantPeptideIon pions WHERE resultSummary.id = " + rsmId, classOf[OrmMasterQuantPeptideIon])
    val rsList = pepIonsQuery.getResultList
   rsList.asScala.toList.map(mqPI => (mqPI.getPeptideId, mqPI.getCharge) -> mqPI).toMap
  }


  private def testQuantifyPeptides(refRsmId : Long, refDSId : Long, expDesignStr : String,qConfigStr : String): Unit = {

    //get existing qPepIons
    val mqPIonsPerPepAndCharge = getQRSMPeptideIonsByPepIdAndCharge(refRsmId)

    // create Exp Design & config data
    val expDesign = deserialize[ExperimentalDesign](expDesignStr)
    val quantConfig = deserialize[LabelFreeQuantConfig](qConfigStr)
    val expDesignSeup = ExperimentalDesignSetup(expDesign, 1, 1) //default groupSetup 1, MQChannel 1
    val dsId = createLFQuantExpDesign(expDesign)

    val udsQuantitation = udsEm.find(classOf[Dataset], dsId)
    val udsMqc = udsQuantitation.getMasterQuantitationChannels.asScala.toList.head

    //Set identification RSM as quantification RSM ... should be the same
    val mergedRsm = QuantifyGlobalTest.getResultSummaryProvider().getResultSummary(mergedRSM1Id, loadResultSet = true, Some(true))
    val masterQc1 = expDesign.masterQuantChannels.find(_.id == udsMqc.getId).get
    val masterQc = masterQc1.copy(quantResultSummaryId = Some(mergedRSM1Id))
    udsMqc.setQuantResultSummaryId(mergedRSM1Id)

    val cache = new MasterQuantChannelEntityCache(testExecutionContext, udsMqc)
    val sortedLcMsRuns = cache.getSortedLcMsRuns()

    /*
     *
     * Ms2DrivenLabelFreeFeatureQuantifier  ExtractMapSet
     */
    val (pepByRunAndScanNbr, psmByRunAndScanNbr) = cache.getPepAndPsmByRunIdAndScanNumber(mergedRsm.get)
    val mapSetExtractor = new ExtractMapSet(
      testExecutionContext.getLCMSDbConnectionContext,
      "Test PeakelDetector",
      sortedLcMsRuns,
      expDesignSeup,
      quantConfig,
      Some(pepByRunAndScanNbr),
      Some(psmByRunAndScanNbr)
    )
    mapSetExtractor.run()
    val lcmsMapSet = mapSetExtractor.extractedMapSet

    /*
    *
    * Ms2DrivenLabelFreeFeatureQuantifier link mapSet and quant channels
    *
    */
    val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map(lcmsMap => lcmsMap.runId.get -> lcmsMap.id)

    // Update the LC-MS map id of each master quant channel
    val udsQuantChannels = udsMqc.getQuantitationChannels.asScala
    for ((udsQc, qc) <- udsQuantChannels.zip(masterQc.quantChannels)) {
      val lcMsMapIdOpt = lcMsMapIdByRunId.get(qc.runId.get)
      require(lcMsMapIdOpt.isDefined, "Can't retrieve the LC-MS map id for the run #" + qc.runId.get)
      qc.lcmsMapId = lcMsMapIdOpt

      udsQc.setLcmsMapId(lcMsMapIdOpt.get)
      //      udsEm.merge(udsQc)
    }
    // Update the map set id of the master quant channel
    udsMqc.setLcmsMapSetId(lcmsMapSet.id)
    //    udsEm.merge(udsMasterQuantChannel)

    /*
     * AbstractLabelFreeFeatureQuantifier Create new Quant RSM/RS ==> Skip !
     */


    val computedPepIons = computePepIonAb(lcmsMapSet, cache, masterQc, mergedRsm.get)

    // -- Test previous and  current values are identical
    val identPepInstById = mergedRsm.get.getPeptideInstanceById
    val currentQChIds = udsMqc.getQuantitationChannels.asScala.sortBy(_.getNumber).map(qch => qch.getId).toList.toArray
    val prevQChannels =  udsEm.createQuery("select qCh from QuantitationChannel qCh WHERE dataset.id = "+refDSId , classOf[QuantitationChannel]).getResultList
    val prevQChIds = prevQChannels.asScala.sortBy(_.getNumber).map(qCh=>qCh.getId).toList

    Assert.assertEquals(currentQChIds.length, prevQChIds.size)
    Assert.assertEquals(computedPepIons.size, mqPIonsPerPepAndCharge.size)

    var nbOK = 0 //count nb pepIons checked (and ok...)
    computedPepIons.foreach(mqPepIons => {
      val pepId = identPepInstById(mqPepIons.peptideInstanceId.get).peptideId
      val previousMqPepIon = mqPIonsPerPepAndCharge.get((pepId, mqPepIons.charge))
      Assert.assertTrue("Missing previous peptide ion for peptideId "+pepId, previousMqPepIon.isDefined)

      //get Abundances
//      val obTree = msiEm.createQuery("select ot from fr.proline.core.orm.msi.ObjectTree ot WHERE id = " + previousMqPepIon.get.getMasterQuantComponent.getObjectTreeId, classOf[ObjectTree]).getSingleResult
      val obTree = msiEm.find(classOf[ObjectTree], previousMqPepIon.get.getMasterQuantComponent.getObjectTreeId)
      previousMqPepIon.get.parseQuantPeptideIonFromProperties(obTree.getClobData)
      val currentAbs = mqPepIons.getAbundancesForQuantChannels(currentQChIds)
      val prevAbsByQCh  = previousMqPepIon.get.getQuantPeptideIonByQchIds.asScala
      for( index <- currentAbs.indices) {
        val prevQChId = prevQChIds(index)
        val prevAb = if(prevAbsByQCh.contains(prevQChId))  prevAbsByQCh(prevQChId).getAbundance else Float.NaN
        Assert.assertEquals("Not same abundances for " + pepId + "/" + mqPepIons.charge, currentAbs(index),prevAb)
        nbOK += 1
      }
    })
    logger.info(" GOT " + nbOK + " compared Ab OK !!! ")
  }


  @Test
  def testQuantifyPeptidesOneRun(): Unit = {
    /**
     * "{\"group_setups\":
     * [{\"number\": 1,
     * \"biological_groups\": [{\"number\": 1,\"sample_numbers\": [1],\"name\": \"Group 2\"}],
     * \"name\": \"Quant\",
     * \"ratio_definitions\": [{\"number\": 1,\"numerator_group_number\": 1,\"denominator_group_number\": 1}]
     * }],
     * \"master_quant_channels\":
     * [{\"number\": 1,
     * \"quant_channels\":
     * [{\"number\": 1,\"run_id\": 1,\"ident_result_summary_id\": 2,\"sample_number\": 1,\"name\": \"F071235\"}],
     * \"name\": \"Quant\"}],
     * \"biological_samples\": [{\"number\": 1,\"name\": \"Group 2Sample null\"}]
     * }"
     */
      //Warning Run Id hardcoded
    val expDesignStr ="{\"group_setups\": [{\"number\": 1,\"biological_groups\": [{\"number\": 1,\"sample_numbers\": [1],\"name\": \"Group 2\"}],\"name\": \"Quant\",\"ratio_definitions\": [{\"number\": 1,\"numerator_group_number\": 1,\"denominator_group_number\": 1}]}]," +
      "\"master_quant_channels\": [{\"number\": 1,\"quant_channels\": [{\"number\": 1,\"run_id\": 1,\"ident_result_summary_id\": "+rsm1Id+",\"sample_number\": 1,\"name\": \"F071235\"}],\"name\": \"Quant\", \"ident_result_summary_id\": "+mergedRSM1Id+", \"ident_dataset_id\": "+mergedDSId+"}],\"biological_samples\": [{\"number\": 1,\"name\": \"Group 2Sample null\"}]}"
    val quantConfigStr ="{\"cross_assignment_config\": { \"ft_mapping_params\": {\"moz_tol_unit\": \"PPM\", \"use_moz_calibration\": true,\"use_automatic_time_tol\": false, \"time_tol\": \"60.0\",   \"moz_tol\": \"5.0\"  },  \"method_name\": \"BETWEEN_ALL_RUNS\",  \"ft_filter\": {   \"name\": \"INTENSITY\",   \"value\": 0.0,   \"operator\": \"GT\"  },  \"restrain_to_reliable_features\": true }, \"clustering_params\": {  \"moz_tol_unit\": \"PPM\",  \"intensity_computation\": \"MOST_INTENSE\",  \"time_computation\": \"MOST_INTENSE\",  \"time_tol\": 15.0,  \"moz_tol\": \"5.0\" }, \"detection_params\": {  \"psm_matching_params\": {   \"moz_tol_unit\": \"PPM\",   \"moz_tol\": \"5.0\"  },  \"isotope_matching_params\": {   \"moz_tol_unit\": \"PPM\",   \"moz_tol\": \"5.0\"  } }, \"extraction_params\": {  \"moz_tol_unit\": \"PPM\",  \"moz_tol\": \"5.0\" }, \"detection_method_name\": \"DETECT_PEAKELS\", \"moz_calibration_smoothing_method\": \"LOESS\", \"alignment_config\": {  \"ft_mapping_method_name\": \"PEPTIDE_IDENTITY\",  \"ft_mapping_params\": {   \"time_tol\": \"600.0\"  },  \"ignore_errors\": false,  \"method_name\": \"EXHAUSTIVE\",  \"smoothing_method_name\": \"LOESS\" }, \"config_version\": \"3.0\"}"
    testQuantifyPeptides(singleQRsmId, singleQDSId, expDesignStr, quantConfigStr)
  }

  @Test
  def testQuantifyPeptidesTwoRun(): Unit = {
    /**
     * "{\"group_setups\":
     * [{\"number\": 1,
     * \"biological_groups\": [{\"number\": 1,\"sample_numbers\": [1],\"name\": \"Group 2\"}],
     * \"name\": \"Quant\",
     * \"ratio_definitions\": [{\"number\": 1,\"numerator_group_number\": 1,\"denominator_group_number\": 1}]
     * }],
     * \"master_quant_channels\":
     * [{\"number\": 1,
     * \"quant_channels\":
     * [{\"number\": 1,\"run_id\": 1,\"ident_result_summary_id\": 2,\"sample_number\": 1,\"name\": \"F071235\"},
     * "{\"number\": 2,\"run_id\": 2,\"ident_result_summary_id\": 4,\"sample_number\": 1,\"name\": \"F071239\"}],],
     * \"name\": \"Quant\"}],
     * \"biological_samples\": [{\"number\": 1,\"name\": \"Group 2Sample null\"}]
     * }"
     */
    //Warning Run Id hardcoded
    val expDesignStr ="{\"group_setups\": [{\"number\": 1,\"biological_groups\": [{\"number\": 1,\"sample_numbers\": [1],\"name\": \"Group Small_2\"}]," +
      "\"name\": \"Quant\",\"ratio_definitions\": [{\"number\": 1,\"numerator_group_number\": 1,\"denominator_group_number\": 1}]}]," +
      "\"master_quant_channels\": " +
      "[{\"number\": 1,\"quant_channels\": [{\"number\": 1,\"run_id\": 1,\"ident_result_summary_id\": "+rsm1Id+",\"sample_number\": 1,\"name\": \"F071235\"}," +
      "{\"number\": 2,\"run_id\": 2,\"ident_result_summary_id\": "+rsm2Id+",\"sample_number\": 1,\"name\": \"F071239\"}],\"name\": \"Quant\", \"ident_result_summary_id\": "+mergedRSM1Id+", \"ident_dataset_id\": "+mergedDSId+"}]," +
      "\"biological_samples\": [{\"number\": 1,\"name\": \"Group Small_2Sample Small_2\"}]}"
    val quantConfigStr = "{\"cross_assignment_config\": { \"ft_mapping_params\": {\"moz_tol_unit\": \"PPM\", \"use_moz_calibration\": true,\"use_automatic_time_tol\": false, \"time_tol\": \"60.0\",   \"moz_tol\": \"5.0\"  },  \"method_name\": \"BETWEEN_ALL_RUNS\",  \"ft_filter\": {   \"name\": \"INTENSITY\",   \"value\": 0.0,   \"operator\": \"GT\"  },  \"restrain_to_reliable_features\": true }, \"clustering_params\": {  \"moz_tol_unit\": \"PPM\",  \"intensity_computation\": \"MOST_INTENSE\",  \"time_computation\": \"MOST_INTENSE\",  \"time_tol\": 15.0,  \"moz_tol\": \"5.0\" }, \"detection_params\": {  \"psm_matching_params\": {   \"moz_tol_unit\": \"PPM\",   \"moz_tol\": \"5.0\"  },  \"isotope_matching_params\": {   \"moz_tol_unit\": \"PPM\",   \"moz_tol\": \"5.0\"  } }, \"extraction_params\": {  \"moz_tol_unit\": \"PPM\",  \"moz_tol\": \"5.0\" }, \"detection_method_name\": \"DETECT_PEAKELS\", \"moz_calibration_smoothing_method\": \"LOESS\", \"alignment_config\": {  \"ft_mapping_method_name\": \"PEPTIDE_IDENTITY\",  \"ft_mapping_params\": {   \"time_tol\": \"600.0\"  },  \"ignore_errors\": false,  \"method_name\": \"EXHAUSTIVE\",  \"smoothing_method_name\": \"LOESS\" }, \"config_version\": \"3.0\"}"
    testQuantifyPeptides(twoRunsQRsmId, twoRunsQDSId, expDesignStr, quantConfigStr)
  }

  @Ignore
  def testDetectMapSet2Runs(): Unit = {
    val expDesignStr = "{\"group_setups\": [{\"number\": 1,\"biological_groups\": [{\"number\": 1,\"sample_numbers\": [1],\"name\": \"Group Small_2\"}],\"name\": \"Quant\",\"ratio_definitions\": [{\"number\": 1,\"numerator_group_number\": 1,\"denominator_group_number\": 1}]}],\"master_quant_channels\": [{\"number\": 1,\"quant_channels\": [{\"number\": 1,\"run_id\": 1,\"ident_result_summary_id\": 2,\"sample_number\": 1,\"name\": \"F071235\"},{\"number\": 2,\"run_id\": 2,\"ident_result_summary_id\": 4,\"sample_number\": 1,\"name\": \"F071239\"}],\"name\": \"Quant\"}],\"biological_samples\": [{\"number\": 1,\"name\": \"Group Small_2Sample Small_2\"}]}"
    val quantConfigStr = "{\"use_last_peakel_detection\": \"false\",\"clustering_params\": {\"moz_tol_unit\": \"PPM\", \"intensity_computation\": \"MOST_INTENSE\", \"time_computation\": \"MOST_INTENSE\", \"time_tol\": \"15.0\",\"moz_tol\": \"5.0\"},\"detection_params\": {\"psm_matching_params\": {\"moz_tol_unit\":\"PPM\", \"moz_tol\": \"5.0\"}, \"isotope_matching_params\": { \"moz_tol_unit\": \"PPM\", \"moz_tol\": \"5.0\"}}, \"extraction_params\": { \"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"detection_method_name\": \"DETECT_PEAKELS\",\"config_version\": \"2.0\",\"moz_calibration_smoothing_method\":\"LOESS\"}"
    testDetectMapSet(2L, rsm2Id, expDesignStr, quantConfigStr)
  }

  @Test
  def testDetectMapSetOneRun(): Unit = {
    val expDesignStr = "{\"group_setups\": [{\"number\": 1,\"biological_groups\": [{\"number\": 1,\"sample_numbers\": [1],\"name\": \"Group 1\"}],\"name\": \"HELA_50\",\"ratio_definitions\": [{\"number\": 1,\"numerator_group_number\": 1,\"denominator_group_number\": 1}]}],\"master_quant_channels\": [{\"number\": 1,\"quant_channels\": [{\"number\": 1,\"run_id\": 1,\"sample_number\": 1,\"name\": \"F071234\"}],\"name\": \"XIC\"}],\"biological_samples\": [{\"number\": 1,\"name\": \"Sample 1\"}]}"
    val quantConfigStr = "{\"use_last_peakel_detection\": \"false\",\"clustering_params\": {\"moz_tol_unit\": \"PPM\", \"intensity_computation\": \"MOST_INTENSE\", \"time_computation\": \"MOST_INTENSE\", \"time_tol\": \"15.0\",\"moz_tol\": \"5.0\"},\"detection_params\": {\"psm_matching_params\": {\"moz_tol_unit\":\"PPM\", \"moz_tol\": \"5.0\"}, \"isotope_matching_params\": { \"moz_tol_unit\": \"PPM\", \"moz_tol\": \"5.0\"}}, \"extraction_params\": { \"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"detection_method_name\": \"DETECT_PEAKELS\",\"config_version\": \"2.0\",\"moz_calibration_smoothing_method\":\"LOESS\"}"
    testDetectMapSet(1L, rsm1Id, expDesignStr, quantConfigStr)
  }

  private def testDetectMapSet(mqcId: Long, rsmId:Long, expDesignStr:String, qConfigStr: String ): Unit = {
    val mqc = udsEm.find(classOf[MasterQuantitationChannel], mqcId)
    val rsm = QuantifyGlobalTest.getResultSummaryProvider().getResultSummary(rsmId, loadResultSet = true, Some(true))


    val cache = new MasterQuantChannelEntityCache(testExecutionContext, mqc)
    val sortedLcMsRuns = cache.getSortedLcMsRuns()
    val (_, psmByRunAndScanNbr) = cache.getPepAndPsmByRunIdAndScanNumber(rsm.get)
    val expDesign = deserialize[ExperimentalDesign](expDesignStr)
    val quantConfig = deserialize[LabelFreeQuantConfig](qConfigStr)
    val expDesignSeup = ExperimentalDesignSetup(expDesign, 1, 1)

    logger.info(" #############Start TEST ############ : Create PeakelsDetector")
    val pdetector = new PeakelsDetector(testExecutionContext.getLCMSDbConnectionContext, "Test PeakelDetector", sortedLcMsRuns, expDesignSeup, quantConfig, psmByRunAndScanNbr)

    //call detectMapSetFromPeakels
    val mzDbFileByLcMsRunId = new mutable.LongMap[File]()

    val scanSeqProvider = new SQLScanSequenceProvider(QuantifyGlobalTest.executionContext.getLCMSDbConnectionContext)

    for (lcmsRun <- sortedLcMsRuns) {

      val rawFile = lcmsRun.rawFile
      val mzDbFilePath = rawFile.getMzdbFilePath().get
      val mzDbFile = new File(mzDbFilePath)
      val ssOpt = scanSeqProvider.getScanSequence(lcmsRun.id)
      lcmsRun.scanSequence = ssOpt
      Assert.assertFalse(lcmsRun.scanSequence.isEmpty)
      mzDbFileByLcMsRunId += lcmsRun.id -> mzDbFile
    }

    logger.info(" ############# TEST ############ : PeakelsDetector detectMapSetFromPeakels ")
    val (finalMapSet, alnResult) = pdetector.detectMapSetFromPeakels(mzDbFileByLcMsRunId, -1L)
    Assert.assertNotNull(finalMapSet)
    Assert.assertNotNull(alnResult)
  }


}

