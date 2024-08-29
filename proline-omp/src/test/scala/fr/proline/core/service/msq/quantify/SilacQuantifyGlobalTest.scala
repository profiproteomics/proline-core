package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.collection.tuplesArray2longMapBuilder
import fr.profi.util.serialization.ProfiJson.deserialize
import fr.proline.core.algo.msq.config.ResidueLabelingQuantConfig
import fr.proline.core.algo.msq.summarizing.ResidueLabelingEntitiesSummarizer
import fr.proline.core.dal._
import fr.proline.core.dal.context.execCtxToTxExecCtx
import fr.proline.core.dbunit._
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.orm.msi
import fr.proline.core.orm.msi.{ObjectTree, ResultSet, MasterQuantPeptideIon => OrmMasterQuantPeptideIon}
import fr.proline.core.orm.uds.{Dataset, MasterQuantitationChannel, QuantitationChannel}
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.core.service.uds.CreateQuantitation
import fr.proline.repository.DriverType
import org.junit.{Assert, BeforeClass, Ignore, Test}

import javax.persistence.TypedQuery
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object SilacQuantifyGlobalTest extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = SILAC_XIC

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
  override def initDBsDBManagement(driverType: DriverType): Unit = {

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

@Ignore
class SilacQuantifyGlobalTest extends StrictLogging {

  // Ident RSM Ids
  val rsm1Id = 1L
  //  val rsm2Id = 4L
  val mergedRSM1Id = 1L
  val mergedDSId = 2L

  val silacOneRunRsmId = 3L
  val silacOneRunDSId = 3L
  // Quant RSM/DS Ids
  //  val singleQRsmId = 7L
  //  val singleQDSId = 5L
  //  val twoRunsQRsmId = 8L
  //  val twoRunsQDSId = 6L

  //Global ref
  val prjId = 1L
  val silac2PlexMethodId = 1L

  val testExecutionContext = SilacQuantifyGlobalTest.executionContext
  val udsEm = testExecutionContext.getUDSDbConnectionContext.getEntityManager// SilacQuantifyGlobalTest.udsDBTestCase.getConnector.createEntityManager()
  val msiEm = testExecutionContext.getMSIDbConnectionContext.getEntityManager//  SilacQuantifyGlobalTest.msiDBTestCase.getConnector.createEntityManager()


  private def createQuantExpDesign(expDesign: ExperimentalDesign): Long = {

    // Store quantitation in the UDSdb
    val quantiCreator = new CreateQuantitation(
      executionContext = testExecutionContext,
      name = "Test Silac Quant",
      description = "Test desc",
      projectId = prjId,
      methodId = silac2PlexMethodId,
      experimentalDesign = expDesign
    )
    quantiCreator.runService()

    quantiCreator.getUdsQuantitation.getId

  }

  private def computePepAb(lcmsMapSet: MapSet, cache: MasterQuantChannelEntityCache, masterQc: MasterQuantChannel, qRsm: ResultSummary, quantMethod: ResidueLabelingQuantMethod, quantConfig: ResidueLabelingQuantConfig): Array[MasterQuantPeptide] = {

    val rawMapIds = lcmsMapSet.getRawMapIds()
    val lcMsScans = cache.getLcMsScans(rawMapIds)
    val spectrumIdByRsIdAndScanNumber = cache.spectrumIdByRsIdAndScanNumber
    val ms2ScanNumbersByFtId = cache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)
    val qcByRSMIdAndTagId = {
      val qcsByRsmId = masterQc.quantChannels.groupBy(_.identResultSummaryId)
      for ((rsmId, qcs) <- qcsByRsmId;
           qc <- qcs) yield (rsmId, qc.quantLabelId.get) -> qc
    }

    val tagById = quantMethod.tagById
    val tagByPtmId = {
      val tmptagByPtmId = {
        for (tag <- quantConfig.tags;
             ptmId <- tag.ptmSpecificityIds) yield ptmId -> tag.tagId
      }
      val mappedTagIds = tmptagByPtmId.map(_._2).toList
      val unmappedTagIds = tagById.keys.filter(!mappedTagIds.contains(_)).toList
      require(unmappedTagIds.length <= 1, "There cannot be more than one tag corresponding to unlabeled peptides")
      (tmptagByPtmId ++ unmappedTagIds.map((-1L, _))).toLongMap()
    }
    val entitiesSummarizer = new ResidueLabelingEntitiesSummarizer(
      qcByRSMIdAndTagId,
      tagByPtmId,
      lcmsMapSet,
      spectrumIdByRsIdAndScanNumber,
      ms2ScanNumbersByFtId)

    logger.info(" :: Test Silac :: summarizing quant entities...")
    val qttPep = entitiesSummarizer.computeMasterQuantPeptides(masterQc, qRsm, cache.quantChannelResultSummaries)
    logger.info(" == Compute Pep quant Result :  nb quant pep :  " + qttPep.length + " nb quant ions " + qttPep.flatMap(_.masterQuantPeptideIons).toList.length)

    // -- Print MqPIons Seq & info
    //    val qttIons = qttPep.flatMap(_.masterQuantPeptideIons).toList
//    val pepIById = qRsm.getPeptideInstanceById
//    val qChIds = masterQc.quantChannels.map(_.id)
//    System.out.println("Seq\tptm\tcharge\tquant\t")
//        qttIons.foreach(qttIon => {
//          val pep = pepIById(qttIon.peptideInstanceId.get).peptide
//          System.out.println(pep.sequence+"\t"+pep.ptmString+"\t"+qttIon.charge+"\t"+qttIon.getAbundancesForQuantChannels(qChIds).mkString("\t"))
//        })
    qttPep
  }

  private def getQRSMPeptideIonsByPepIdAndCharge(rsmId: Long): Map[(java.lang.Long, Int), OrmMasterQuantPeptideIon] = {
    //Read Q Peptides Ions  to compare with generated ones
    val pepIonsQuery: TypedQuery[OrmMasterQuantPeptideIon] = msiEm.createQuery("select pions from MasterQuantPeptideIon pions WHERE resultSummary.id = " + rsmId, classOf[OrmMasterQuantPeptideIon])
    val rsList = pepIonsQuery.getResultList
    rsList.asScala.toList.map(mqPI => (mqPI.getPeptideId, mqPI.getCharge) -> mqPI).toMap
  }


  def runResidueLabelingQuant( expDesignStr: String, qConfigStr: String): MasterQuantitationChannel = {

      // create Exp Design & config data
    val expDesign = deserialize[ExperimentalDesign](expDesignStr)
    val quantConfig = deserialize[ResidueLabelingQuantConfig](qConfigStr)
    val newQDSId = createQuantExpDesign(expDesign) // run CreateQuantitation service

    val udsQuantitation = udsEm.find(classOf[Dataset], newQDSId)
    val udsMqc = udsQuantitation.getMasterQuantitationChannels.asScala.toList.head
    udsMqc.setIdentResultSummaryId(mergedRSM1Id)
    val udsIdentDs = udsEm.find(classOf[fr.proline.core.orm.uds.Dataset], mergedDSId)
    udsMqc.setIdentDataset(udsIdentDs)
    logger.info(" Created new DS Quant " + newQDSId + " with mqChannel " + udsMqc.getId + " ref RSM id " + udsMqc.getIdentResultSummaryId)

    val udsQuantMethod = udsMqc.getDataset.getMethod
    val tags = udsQuantMethod.getLabels.asScala.toList.map { udsQuantLabel =>
      ResidueTag(
        id = udsQuantLabel.getId,
        name = udsQuantLabel.getName,
        number = udsQuantLabel.getNumber,
        properties = if (udsQuantLabel.getSerializedProperties == null) null else deserialize[ResidueTagProperties](udsQuantLabel.getSerializedProperties)
      )
    }
    val quantMethod = ResidueLabelingQuantMethod(tags.sortBy(_.number))

    // Isolate future actions in an SQL transaction
    testExecutionContext.tryInTransactions(udsTx = true, msiTx = true, txWork = {

      //Run ResidueLabelingQuantifier
      val resLabelQuanntifier = new ResidueLabelingQuantifier(
        executionContext = SilacQuantifyGlobalTest.executionContext,
        udsMasterQuantChannel = udsMqc,
        experimentalDesign = expDesign,
        quantMethod = quantMethod,
        quantConfig = quantConfig)

      resLabelQuanntifier.quantify()

    })

    udsMqc
  }

  def testRunTwiceResidueLabelingQuant(refRsmId: Long, refDSId: Long, expDesignStr: String, qConfigStr: String): Unit = {

    //get existing qPepIons
    val mqPIonsPerPepAndCharge = getQRSMPeptideIonsByPepIdAndCharge(refRsmId)
    logger.info(" READ mqPepIon from ref RSM " + refRsmId + " => " + mqPIonsPerPepAndCharge.size)

    // Run ResidueLabelingQuant - 1
    val udsMqc = runResidueLabelingQuant(expDesignStr, qConfigStr)
    val qRsmId = udsMqc.getQuantResultSummaryId
    logger.info("New Quant DS " + udsMqc.getDataset.getId + " RSM ID " + qRsmId)
    val newMqPIonsPerPepAndCharge = getQRSMPeptideIonsByPepIdAndCharge(qRsmId)
    logger.info(" READ mqPepIon from new RSM " + qRsmId + " => " + newMqPIonsPerPepAndCharge.size)

    // Run a second time
    val udsMqc2 = runResidueLabelingQuant(expDesignStr, qConfigStr)
    val qRsmId2 = udsMqc2.getQuantResultSummaryId
    logger.info("TWICE New Quant DS " + udsMqc.getDataset.getId + " RSM ID " + qRsmId2)
    val newMqPIonsPerPepAndCharge2 = getQRSMPeptideIonsByPepIdAndCharge(qRsmId2)
    logger.info(" TWICE READ mqPepIon from new RSM " + qRsmId2 + " => " + newMqPIonsPerPepAndCharge2.size)

    // Compare results

    // -- Compare Run1  & Run2
    val currentQChIds1 = udsMqc.getQuantitationChannels.asScala.sortBy(_.getNumber).map(qch => qch.getId).toList.toArray
    val currentQChIds2 = udsMqc2.getQuantitationChannels.asScala.sortBy(_.getNumber).map(qch => qch.getId).toList.toArray
    Assert.assertEquals(currentQChIds1.length, currentQChIds2.length)
    Assert.assertEquals(newMqPIonsPerPepAndCharge.size, newMqPIonsPerPepAndCharge2.size)
    System.out.println("Cmp Run1 vs Run2  - pepIons defined & Ab ")
    var nbOk = 0
    newMqPIonsPerPepAndCharge.foreach(ionsPerXX => {
      val pepIon = ionsPerXX._2
      val pepKey = ionsPerXX._1
      val otherMQPepIon = newMqPIonsPerPepAndCharge2.get(pepKey)
      Assert.assertTrue("Missing Run2 peptide ion for peptideId " + pepIon.getPeptideId + " charge " + pepIon.getCharge, otherMQPepIon.isDefined)

      //get Abundances
      val obTree = msiEm.find(classOf[ObjectTree], otherMQPepIon.get.getMasterQuantComponent.getObjectTreeId)
      otherMQPepIon.get.parseQuantPeptideIonFromProperties(obTree.getClobData)
      val obTree2 = msiEm.find(classOf[ObjectTree], pepIon.getMasterQuantComponent.getObjectTreeId)
      pepIon.parseQuantPeptideIonFromProperties(obTree2.getClobData)

      val currentAbs = pepIon.getQuantPeptideIonByQchIds.asScala
      val prevAbsByQCh = otherMQPepIon.get.getQuantPeptideIonByQchIds.asScala
      for (i <- currentQChIds1.indices) {
        val qCh = currentQChIds1(i)
        val qCh2 = currentQChIds2(i)
        val cAb: Float = if (currentAbs.contains(qCh)) currentAbs(qCh).getAbundance else Float.NaN
        val pAb: Float = if (prevAbsByQCh.contains(qCh2)) prevAbsByQCh(qCh2).getAbundance else Float.NaN
        Assert.assertEquals("Not same abundances for " + pepIon.getPeptideId + "/" + pepIon.getCharge, cAb, pAb, 0.01)
        nbOk += 1
      }
    })
    logger.debug(" GOT "+ nbOk+ " compared Ab OK !!! ")
  }

  def testRunResidueLabelingQuant(refRsmId: Long, refDSId: Long, expDesignStr: String, qConfigStr: String): Unit = {

    //get existing qPepIons
    val mqPIonsPerPepAndCharge = getQRSMPeptideIonsByPepIdAndCharge(refRsmId)
    logger.info(" READ mqPepIon from ref RSM " + refRsmId + " => " + mqPIonsPerPepAndCharge.size)

    val udsMqc =  runResidueLabelingQuant(expDesignStr, qConfigStr)

    val qRsmId = udsMqc.getQuantResultSummaryId
    logger.info("New Quant DS " + udsMqc.getDataset.getId + " RSM ID " + qRsmId)
    val newMqPIonsPerPepAndCharge = getQRSMPeptideIonsByPepIdAndCharge(qRsmId)
    logger.info(" READ mqPepIon from new RSM " + qRsmId + " => " + newMqPIonsPerPepAndCharge.size)

    // -- Test previous and  current values are identical
    val currentQChIds = udsMqc.getQuantitationChannels.asScala.sortBy(_.getNumber).map(qch => qch.getId).toList.toArray
    val prevQChannels = udsEm.createQuery("select qCh from QuantitationChannel qCh WHERE dataset.id = " + refDSId, classOf[QuantitationChannel]).getResultList
    val prevQChIds = prevQChannels.asScala.sortBy(_.getNumber).map(qCh => qCh.getId).toList

    Assert.assertEquals(currentQChIds.length, prevQChIds.size)
    Assert.assertEquals(newMqPIonsPerPepAndCharge.size, mqPIonsPerPepAndCharge.size)
    System.out.println("Cmp Prev vs New - pepIons defined ")
    mqPIonsPerPepAndCharge.foreach(ionsPerXX => {
      val pepIon = ionsPerXX._2
      val pepKey = ionsPerXX._1
      val otherMQPepIon = newMqPIonsPerPepAndCharge.get(pepKey)
      Assert.assertTrue("Missing new peptide ion for peptideId " + pepIon.getPeptideId + " charge " + pepIon.getCharge, otherMQPepIon.isDefined)
    })

    System.out.println("Cmp New vs Prev - pepIons defined & Ab ")
    var nbOK = 0 //count nb pepIons checked (and ok...)
    newMqPIonsPerPepAndCharge.foreach(ionsPerXX => {
      val pepIon = ionsPerXX._2
      val pepKey = ionsPerXX._1
      val previousMqPepIon = mqPIonsPerPepAndCharge.get( pepKey)
      Assert.assertTrue("Missing previous peptide ion for peptideId " + pepIon.getPeptideId + " charge " + pepIon.getCharge, previousMqPepIon.isDefined)

      //get Abundances
      val obTree = msiEm.find(classOf[ObjectTree], previousMqPepIon.get.getMasterQuantComponent.getObjectTreeId)
      previousMqPepIon.get.parseQuantPeptideIonFromProperties(obTree.getClobData)
      val obTree2 = msiEm.find(classOf[ObjectTree], pepIon.getMasterQuantComponent.getObjectTreeId)
      pepIon.parseQuantPeptideIonFromProperties(obTree2.getClobData)

      val currentAbs = pepIon.getQuantPeptideIonByQchIds.asScala
      val prevAbsByQCh = previousMqPepIon.get.getQuantPeptideIonByQchIds.asScala
      var index = 0
      udsMqc.getQuantitationChannels.forEach(qCh => {
        val cAb : Float = if (currentAbs.contains(qCh.getId)) currentAbs(qCh.getId).getAbundance else Float.NaN
        val prevQChId = prevQChIds(index)
        val pAb : Float = if (prevAbsByQCh.contains(prevQChId)) prevAbsByQCh(prevQChId).getAbundance else Float.NaN
        index += 1
        Assert.assertEquals("Not same abundances for " + pepIon.getPeptideId + "/" + pepIon.getCharge, cAb, pAb, 0.01)
        nbOK += 1
      })

    })
    logger.debug(" GOT "+ nbOK+ " compared Ab OK !!! ")
  }

  def testReadQuantifyPeptides(refRsmId: Long, refDSId: Long, expDesignStr: String, qConfigStr: String): Unit = {
    //get existing qPepIons
    val mqPIonsPerPepAndCharge = getQRSMPeptideIonsByPepIdAndCharge(refRsmId)
    logger.info(" :: Test Silac :: READ mqPepIon from ref RSM "+refRsmId+" => "+mqPIonsPerPepAndCharge.size)

    // create Exp Design & config data
    val expDesign = deserialize[ExperimentalDesign](expDesignStr)
    val quantConfig = deserialize[ResidueLabelingQuantConfig](qConfigStr)
    val expDesignSetup = ExperimentalDesignSetup(expDesign, 1, 1) //default groupSetup 1, MQChannel 1
    val dsId = createQuantExpDesign(expDesign)

    val udsQuantitation = udsEm.find(classOf[Dataset], dsId)
    val udsMqc = udsQuantitation.getMasterQuantitationChannels.asScala.toList.head
    udsMqc.setIdentResultSummaryId(mergedRSM1Id)
    val udsIdentDs = udsEm.find(classOf[fr.proline.core.orm.uds.Dataset], mergedDSId)
    udsMqc.setIdentDataset(udsIdentDs)
    logger.info(" :: Test Silac :: Created new DS Quant " + dsId + " with mqChannel " + udsMqc.getId + " ref RSM id " + udsMqc.getIdentResultSummaryId)

    val udsQuantMethod = udsMqc.getDataset.getMethod
    val tags = udsQuantMethod.getLabels.asScala.toList.map { udsQuantLabel =>
      ResidueTag(
        id = udsQuantLabel.getId,
        name = udsQuantLabel.getName,
        number = udsQuantLabel.getNumber,
        properties = if (udsQuantLabel.getSerializedProperties == null) null else deserialize[ResidueTagProperties](udsQuantLabel.getSerializedProperties)
      )
    }

    // Isolate future actions in an SQL transaction
    testExecutionContext.tryInTransactions(udsTx = true, msiTx = true, txWork = {

      val quantMethod = ResidueLabelingQuantMethod(tags.sortBy(_.number))

      //------  Idem quantifyMQCh method
      val curSQLTime = new java.sql.Timestamp(new java.util.Date().getTime)
      val rsmProvider = SilacQuantifyGlobalTest.getResultSummaryProvider()
      val rsmDuplicator = new RsmDuplicator(rsmProvider)

      val mergedIdentRefRsm = rsmProvider.getResultSummary(mergedRSM1Id, loadResultSet = true).get

      val allQuChannels = udsMqc.getQuantitationChannels.asScala.toList
      val newAllQChannels = new ArrayBuffer[QuantitationChannel]()
      val newRsmIdByOldId = new mutable.HashMap[Long, Long]()
      val quantChannelsByRsmId = allQuChannels.groupBy(_.getIdentResultSummaryId)

      logger.debug(" :: Test Silac :: ResidueLabelingQuantifier Test  : duplicate idenRSM for qChs " + allQuChannels.map(_.getName))
      // Duplicate new RS/RSM from previous ones for all quantChannels to be allowed to modify them
      quantChannelsByRsmId.foreach(entry => {
        logger.debug(" Get RSM " + entry._1 + " for qChs " + entry._2.map(_.getName))
        val qChIdentRsm = if (entry._1.equals(mergedRSM1Id)) mergedIdentRefRsm else rsmProvider.getResultSummary(entry._1, loadResultSet = true).get
        val oldRsmId = qChIdentRsm.id

        //create ans store in DB an orm ResultSet
        val qChannelMsiQuantRs = new ResultSet
        qChannelMsiQuantRs.setName("")
        qChannelMsiQuantRs.setType(ResultSet.Type.QUANTITATION)
        qChannelMsiQuantRs.setCreationTimestamp(curSQLTime)
        qChannelMsiQuantRs.setChildren(Set.empty[ResultSet].asJava)
        msiEm.persist(qChannelMsiQuantRs)

        //create ans store in DB an orm ResultSummary using created RS
        val qChannelMsiQuantRsm = new msi.ResultSummary()
        qChannelMsiQuantRsm.setModificationTimestamp(curSQLTime)
        qChannelMsiQuantRsm.setResultSet(qChannelMsiQuantRs)
        val rsms = Set.empty[msi.ResultSummary]
        qChannelMsiQuantRsm.setChildren(new java.util.HashSet(rsms.asJavaCollection))
        msiEm.persist(qChannelMsiQuantRsm)

        //Duplicate qCh-identRSM into created one
        rsmDuplicator.cloneAndStoreRSM(qChIdentRsm, qChannelMsiQuantRsm, qChannelMsiQuantRs, eraseSourceIds = false, msiEm)
        //Associate new duplicated RSM to qChannels
        entry._2.foreach(qch => {
          qch.setIdentResultSummaryId(qChannelMsiQuantRsm.getId)
          udsEm.persist(qch)
          newAllQChannels += qch
        })
        newRsmIdByOldId += oldRsmId -> qChannelMsiQuantRsm.getId
      })

      logger.debug(":: Test Silac :: ResidueLabelingQuantifier Test  : duplicate mqCh identRSM ref for  ")
      // Duplicate new RS/RSM from previous one for mqChRef to be allowed to modify them
      //create ans store in DB an orm ResultSet
      val qChannelMsiQuantRs = new ResultSet
      qChannelMsiQuantRs.setName("")
      qChannelMsiQuantRs.setType(ResultSet.Type.QUANTITATION)
      qChannelMsiQuantRs.setCreationTimestamp(curSQLTime)
      qChannelMsiQuantRs.setChildren(Set.empty[ResultSet].asJava)
      msiEm.persist(qChannelMsiQuantRs)

      //create ans store in DB an orm ResultSummary using created RS
      val quantMsiRSM = new msi.ResultSummary()
      quantMsiRSM.setModificationTimestamp(curSQLTime)
      quantMsiRSM.setResultSet(qChannelMsiQuantRs)
      val rsms = Set.empty[msi.ResultSummary]
      quantMsiRSM.setChildren(new java.util.HashSet(rsms.asJavaCollection))
      msiEm.persist(quantMsiRSM)

      // Update quant result summary id of the master quant channel and for each quantChannels
      val omMasterQc = expDesign.masterQuantChannels.find(_.id == udsMqc.getId).get
      //      udsMqc.setIdentResultSummaryId(newRefIdentRSM.getId)
      udsMqc.setQuantResultSummaryId(quantMsiRSM.getId)
      udsMqc.setQuantitationChannels(newAllQChannels.asJava)
      for (index <- omMasterQc.quantChannels.indices) {
        val prevQCh = omMasterQc.quantChannels(index)
        omMasterQc.quantChannels(index) = prevQCh.copy(identResultSummaryId = newRsmIdByOldId(prevQCh.identResultSummaryId))
      }
      udsEm.persist(udsMqc)
      val newQuantOMRSM = rsmDuplicator.cloneAndStoreRSM(mergedIdentRefRsm, quantMsiRSM, qChannelMsiQuantRs, eraseSourceIds = false, msiEm)

      val resLabelQuantifier = new ResidueLabelingQuantifier(
        executionContext = SilacQuantifyGlobalTest.executionContext,
        udsMasterQuantChannel = udsMqc,
        experimentalDesign = expDesign,
        quantMethod = quantMethod,
        quantConfig = quantConfig)

      logger.info(" :: Test Silac :: computing label-free quant entities...")
      logger.info(" ***** Find missing peptides for Peptides-tag tuples in merged RSM..." + newQuantOMRSM.id)
      val start = System.currentTimeMillis()
      resLabelQuantifier.createMissingTupleData(newQuantOMRSM) //create Missing Tuple in IdentRSM (no store so not a pb)
      val end = System.currentTimeMillis()
      logger.info(" ***** TOOK = " + (end - start) + " ms")
      logger.info("Nb peptInstance for qRsm " + newQuantOMRSM.peptideInstances.length + " validated  " + newQuantOMRSM.peptideInstances.count(pi => pi.validatedProteinSetsCount > 0))

      logger.info(" ***** Find missing peptides for Peptides-tag tuples in qChannels RSM...")
      val cache = new MasterQuantChannelEntityCache(testExecutionContext, udsMqc)
      for (rsm <- cache.quantChannelResultSummaries) {
        logger.info("  ... In RSM " + rsm.id)
        resLabelQuantifier.createMissingTupleData(rsm)
      }

      val sortedLcMsRuns = cache.getSortedLcMsRuns()
      val (pepByRunAndScanNbr, psmByRunAndScanNbr) = cache.getPepAndPsmByRunIdAndScanNumber(newQuantOMRSM)
      val psmBypep = psmByRunAndScanNbr.head._2.values.flatten.groupBy(_.peptide)
      val nbrPsm = psmBypep.values.flatten.size
      logger.debug(" :: Test Silac :: psmBypep..." + psmBypep.size + " nbr peptides = " + nbrPsm + " pm. Run ExtractMapSet")

      val mapSetExtractor = new ExtractMapSet(
        testExecutionContext.getLCMSDbConnectionContext,
        "Test Silac PeakelDetector",
        sortedLcMsRuns,
        expDesignSetup,
        quantConfig.labelFreeQuantConfig,
        Some(pepByRunAndScanNbr),
        Some(psmByRunAndScanNbr)
      )
      mapSetExtractor.run()
      val lcmsMapSet = mapSetExtractor.extractedMapSet

      logger.debug(" :: Test Silac :: Run computePepAb ")
      val computedPeps = computePepAb(lcmsMapSet, cache, omMasterQc, newQuantOMRSM, quantMethod, quantConfig)
      val computedPepsById = computedPeps.map( mqP => mqP.id -> mqP).toMap
      val computedPepIons= computedPeps.flatMap(_.masterQuantPeptideIons).toList
      logger.info("  ==> RUN computePepIonAb result pep ions " + computedPepIons.length+"(from "+computedPeps.length+" peptides )")

      /** to comment */
//      val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map(lcmsMap => lcmsMap.runId.get -> lcmsMap.id)
//      // Update the LC-MS map id of each master quant channel
//      val udsQuantChannels = udsMqc.getQuantitationChannels.asScala
//      for ((udsQc, qc) <- udsQuantChannels.zip(omMasterQc.quantChannels)) {
//        val lcMsMapIdOpt = lcMsMapIdByRunId.get(qc.runId.get)
//        require(lcMsMapIdOpt.isDefined, "Can't retrieve the LC-MS map id for the run #" + qc.runId.get)
//        qc.lcmsMapId = lcMsMapIdOpt
//
//        udsQc.setLcmsMapId(lcMsMapIdOpt.get)
//        //      udsEm.merge(udsQc)
//      }
//      // Update the map set id of the master quant channel
//      udsMqc.setLcmsMapSetId(lcmsMapSet.id)
//      udsEm.merge(udsMqc)

//      resLabelQuantifier.storeMasterQuantPeptidesAndProteinSets(quantMsiRSM, computedPeps, Array.empty[MasterQuantProteinSet])


      // ----- Test previous and  current values are identical
      logger.debug(" :: Test Silac :: Test previous and current values ")
      val identPepInstById = newQuantOMRSM.getPeptideInstanceById
      val currentQChIds = udsMqc.getQuantitationChannels.asScala.sortBy(_.getNumber).map(qch => qch.getId).toList.toArray
      val prevQChannels = udsEm.createQuery("select qCh from QuantitationChannel qCh WHERE dataset.id = " + refDSId, classOf[QuantitationChannel]).getResultList
      val prevQChIds = prevQChannels.asScala.sortBy(_.getNumber).map(qCh => qCh.getId).toList
      Assert.assertEquals(currentQChIds.length, prevQChIds.size)
      Assert.assertEquals(mqPIonsPerPepAndCharge.size, computedPepIons.size)

      var nbOK = 0 //count nb pepIons checked (and ok...)
      computedPepIons.foreach(mqPepIons => {
        val pepId2 = computedPepsById(mqPepIons.masterQuantPeptideId).getPeptideId.get
        val previousMqPepIon = mqPIonsPerPepAndCharge.get((pepId2, mqPepIons.charge))
        Assert.assertTrue("Missing previous peptide ion for peptideId " + pepId2 + " charge " + mqPepIons.charge+" ("+identPepInstById(mqPepIons.peptideInstanceId.get).peptide.sequence+")", previousMqPepIon.isDefined)

        //get Abundances
        val obTree = msiEm.find(classOf[ObjectTree], previousMqPepIon.get.getMasterQuantComponent.getObjectTreeId)
        previousMqPepIon.get.parseQuantPeptideIonFromProperties(obTree.getClobData)
        val currentAbs = mqPepIons.getAbundancesForQuantChannels(currentQChIds)
        val prevAbsByQCh = previousMqPepIon.get.getQuantPeptideIonByQchIds.asScala
        for (index <- currentAbs.indices) {
          val prevQChId = prevQChIds(index)
          val prevAb = if (prevAbsByQCh.contains(prevQChId)) prevAbsByQCh(prevQChId).getAbundance else Float.NaN
          Assert.assertEquals("Not same abundances for " + pepId2 + "/" + mqPepIons.charge, currentAbs(index), prevAb)
          nbOK += 1
        }
      })
      logger.info(" GOT " + nbOK + " compared Ab OK !!! ")
    })
  }

  @Ignore protected
  def testFullQuantifyPeptidesOneRun(): Unit = {
    // Isolate future actions in an SQL transaction
      val expDesignStr = "{\"group_setups\":[{\"number\":1,\"biological_groups\":[{\"number\":1,\"sample_numbers\":[1],\"name\":\"GroupRicci3b\"}],\"name\":\"Quant2-156-FromMerged\",\"ratio_definitions\":[{\"number\":1,\"numerator_group_number\":1,\"denominator_group_number\":1}]}]," +
        "\"master_quant_channels\":[{\"number\":1,\"quant_channels\":[{\"name\":\"F177156.Light\",\"number\":1,\"run_id\":1,\"quant_label_id\":1,\"ident_result_summary_id\":"+rsm1Id+",\"sample_number\":1},{\"name\":\"F177156.Heavy\",\"number\":2,\"run_id\":1,\"quant_label_id\":2,\"ident_result_summary_id\":"+rsm1Id+",\"sample_number\":1}],\"ident_result_summary_id\":"+mergedRSM1Id+",\"name\":\"Quant2-156-FromMerged\",\"ident_dataset_id\":"+mergedDSId+"}],\"biological_samples\":[{\"number\":1,\"name\":\"GroupRicci3bSampleRicci3b\"}]}"
      val quantConfigStr = "{\"label_free_quant_config\": {\"use_last_peakel_detection\": \"false\",\"cross_assignment_config\": {\"ft_mapping_params\": {\"moz_tol_unit\": \"PPM\",\"use_moz_calibration\": true,\"use_automatic_time_tol\": false,\"time_tol\": \"60.0\",\"moz_tol\": \"5.0\"},\"method_name\": \"BETWEEN_ALL_RUNS\",\"ft_filter\": {\"name\": \"INTENSITY\",\"value\": 0.0,\"operator\": \"GT\"},\"restrain_to_reliable_features\": true},\"clustering_params\": {\"moz_tol_unit\": \"PPM\",\"intensity_computation\": \"MOST_INTENSE\",\"time_computation\": \"MOST_INTENSE\",\"time_tol\": 15.0,\"moz_tol\": \"5.0\"},\"detection_params\": {\"psm_matching_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"isotope_matching_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"}},\"extraction_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"detection_method_name\": \"DETECT_PEAKELS\",\"moz_calibration_smoothing_method\": \"LOESS\",\"alignment_config\": {\"ft_mapping_method_name\": \"PEPTIDE_IDENTITY\",\"ft_mapping_params\": {\"time_tol\": \"600.0\"},\"ignore_errors\": false,\"method_name\": \"EXHAUSTIVE\",\"smoothing_method_name\": \"LOESS\"},\"config_version\": \"3.0\"},\"tags\": [{\"tag_id\": 2,\"ptm_specificity_ids\": [445,424]},{\"tag_id\": 1,\"ptm_specificity_ids\": []}]}"

      testRunResidueLabelingQuant(silacOneRunRsmId, silacOneRunDSId, expDesignStr, quantConfigStr)
  }

  @Ignore
  def testRunTwiceFullQuantifyPeptidesOneRun(): Unit = {
    // Isolate future actions in an SQL transaction
    val expDesignStr = "{\"group_setups\":[{\"number\":1,\"biological_groups\":[{\"number\":1,\"sample_numbers\":[1],\"name\":\"GroupRicci3b\"}],\"name\":\"Quant2-156-FromMerged\",\"ratio_definitions\":[{\"number\":1,\"numerator_group_number\":1,\"denominator_group_number\":1}]}]," +
      "\"master_quant_channels\":[{\"number\":1,\"quant_channels\":[{\"name\":\"F177156.Light\",\"number\":1,\"run_id\":1,\"quant_label_id\":1,\"ident_result_summary_id\":" + rsm1Id + ",\"sample_number\":1},{\"name\":\"F177156.Heavy\",\"number\":2,\"run_id\":1,\"quant_label_id\":2,\"ident_result_summary_id\":" + rsm1Id + ",\"sample_number\":1}],\"ident_result_summary_id\":" + mergedRSM1Id + ",\"name\":\"Quant2-156-FromMerged\",\"ident_dataset_id\":" + mergedDSId + "}],\"biological_samples\":[{\"number\":1,\"name\":\"GroupRicci3bSampleRicci3b\"}]}"
    val quantConfigStr = "{\"label_free_quant_config\": {\"use_last_peakel_detection\": \"false\",\"cross_assignment_config\": {\"ft_mapping_params\": {\"moz_tol_unit\": \"PPM\",\"use_moz_calibration\": true,\"use_automatic_time_tol\": false,\"time_tol\": \"60.0\",\"moz_tol\": \"5.0\"},\"method_name\": \"BETWEEN_ALL_RUNS\",\"ft_filter\": {\"name\": \"INTENSITY\",\"value\": 0.0,\"operator\": \"GT\"},\"restrain_to_reliable_features\": true},\"clustering_params\": {\"moz_tol_unit\": \"PPM\",\"intensity_computation\": \"MOST_INTENSE\",\"time_computation\": \"MOST_INTENSE\",\"time_tol\": 15.0,\"moz_tol\": \"5.0\"},\"detection_params\": {\"psm_matching_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"isotope_matching_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"}},\"extraction_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"detection_method_name\": \"DETECT_PEAKELS\",\"moz_calibration_smoothing_method\": \"LOESS\",\"alignment_config\": {\"ft_mapping_method_name\": \"PEPTIDE_IDENTITY\",\"ft_mapping_params\": {\"time_tol\": \"600.0\"},\"ignore_errors\": false,\"method_name\": \"EXHAUSTIVE\",\"smoothing_method_name\": \"LOESS\"},\"config_version\": \"3.0\"},\"tags\": [{\"tag_id\": 2,\"ptm_specificity_ids\": [445,424]},{\"tag_id\": 1,\"ptm_specificity_ids\": []}]}"

    testRunResidueLabelingQuant(silacOneRunRsmId, silacOneRunDSId, expDesignStr, quantConfigStr)
  }

  @Ignore
  def testQuantifyPeptidesOneRun(): Unit = {

    val expDesignStr = "{\"group_setups\":[{\"number\":1,\"biological_groups\":[{\"number\":1,\"sample_numbers\":[1],\"name\":\"GroupRicci3b\"}],\"name\":\"Quant2-156-FromMerged\",\"ratio_definitions\":[{\"number\":1,\"numerator_group_number\":1,\"denominator_group_number\":1}]}]," +
      "\"master_quant_channels\":[{\"number\":1,\"quant_channels\":[{\"name\":\"F177156.Light\",\"number\":1,\"run_id\":1,\"quant_label_id\":1,\"ident_result_summary_id\":"+rsm1Id+",\"sample_number\":1},{\"name\":\"F177156.Heavy\",\"number\":2,\"run_id\":1,\"quant_label_id\":2,\"ident_result_summary_id\":"+rsm1Id+",\"sample_number\":1}],\"ident_result_summary_id\":"+mergedRSM1Id+",\"name\":\"Quant2-156-FromMerged\",\"ident_dataset_id\":"+mergedDSId+"}],\"biological_samples\":[{\"number\":1,\"name\":\"GroupRicci3bSampleRicci3b\"}]}"
    val quantConfigStr = "{\"label_free_quant_config\": {\"use_last_peakel_detection\": \"false\",\"cross_assignment_config\": {\"ft_mapping_params\": {\"moz_tol_unit\": \"PPM\",\"use_moz_calibration\": true,\"use_automatic_time_tol\": false,\"time_tol\": \"60.0\",\"moz_tol\": \"5.0\"},\"method_name\": \"BETWEEN_ALL_RUNS\",\"ft_filter\": {\"name\": \"INTENSITY\",\"value\": 0.0,\"operator\": \"GT\"},\"restrain_to_reliable_features\": true},\"clustering_params\": {\"moz_tol_unit\": \"PPM\",\"intensity_computation\": \"MOST_INTENSE\",\"time_computation\": \"MOST_INTENSE\",\"time_tol\": 15.0,\"moz_tol\": \"5.0\"},\"detection_params\": {\"psm_matching_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"isotope_matching_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"}},\"extraction_params\": {\"moz_tol_unit\": \"PPM\",\"moz_tol\": \"5.0\"},\"detection_method_name\": \"DETECT_PEAKELS\",\"moz_calibration_smoothing_method\": \"LOESS\",\"alignment_config\": {\"ft_mapping_method_name\": \"PEPTIDE_IDENTITY\",\"ft_mapping_params\": {\"time_tol\": \"600.0\"},\"ignore_errors\": false,\"method_name\": \"EXHAUSTIVE\",\"smoothing_method_name\": \"LOESS\"},\"config_version\": \"3.0\"},\"tags\": [{\"tag_id\": 2,\"ptm_specificity_ids\": [445,424]},{\"tag_id\": 1,\"ptm_specificity_ids\": []}]}"


    testReadQuantifyPeptides(silacOneRunRsmId, silacOneRunDSId, expDesignStr, quantConfigStr)
  }

}

