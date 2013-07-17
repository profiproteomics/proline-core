package fr.proline.core.service.msi

import java.io.File
import scala.collection.mutable.{ HashMap, ArrayBuffer }
import org.junit.{ After, AfterClass, Assert, Test, Before, BeforeClass, Ignore }
import com.weiglewilczek.slf4s.Logging
import fr.proline.util.primitives._

import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.pepmatch.{ ScorePSMFilter, RankPSMFilter, _ }
import fr.proline.core.algo.msi.filtering.proteinset.{ ScoreProtSetFilter, SpecificPeptidesPSFilter }
import fr.proline.core.algo.msi.filtering.{ IPeptideMatchFilter, FilterPropertyKeys, _ }
import fr.proline.core.algo.msi.validation.pepmatch.TDPepMatchValidatorWithFDROptimization
import fr.proline.core.algo.msi.validation.proteinset.ProtSetRulesValidatorWithFDROptimization
import fr.proline.core.algo.msi.validation.{ BasicTDAnalyzer, _ }
import fr.proline.core.algo.msi.InferenceMethods
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.dal.{ ContextFactory, SQLConnectionContext }
import fr.proline.core.om.model.msi.{ ResultSet, PeptideMatch, FilterDescriptor }
import fr.proline.core.om.provider.msi.impl._
import fr.proline.core.om.provider.msi._
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.repository.DriverType
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.context.BasicExecutionContext
import fr.proline.core.om.utils.AbstractMultipleDBTestCase

abstract class AbstractResultSetValidator extends AbstractMultipleDBTestCase with Logging {
  
  var executionContext: IExecutionContext = null  
  var rsProvider: IResultSetProvider = null
  protected var readRS: ResultSet = null
  
  // Define the interface to be implemented
  val driverType: DriverType
  val fileName: String
  val targetRSId: Long
  val decoyRSId: Option[Long]
  
  def getRS(): ResultSet = {
    this.resetRSValidation(readRS)
    if (readRS.decoyResultSet.isDefined) this.resetRSValidation(readRS.decoyResultSet.get)
    this.readRS
  }
  
  private def _loadRS(): ResultSet = {
    val rs = rsProvider.getResultSet(targetRSId).get    
    // SMALL HACK because of DBUNIT BUG (see bioproj defect #7548)
    if (decoyRSId.isDefined) rs.decoyResultSet = rsProvider.getResultSet(decoyRSId.get)
    rs
  }
  
  @BeforeClass
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit_samples/"+fileName+"/ps-db.xml")
    msiDBTestCase.loadDataSet("/dbunit_samples/"+fileName+"/msi-db.xml")
    udsDBTestCase.loadDataSet("/dbunit_samples/"+fileName+"/uds-db.xml")

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    val (execContext, rsProv) = buildJPAContext() //SQLContext()
    executionContext = execContext
    rsProvider = rsProv
    readRS = this._loadRS()
  }

  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false).asInstanceOf[SQLConnectionContext]

    val executionContext = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)

    val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

    parserContext.putProvider(classOf[IPeptideProvider], new SQLPeptideProvider(psDbCtx))
    parserContext.putProvider(classOf[IPTMProvider], new SQLPTMProvider(psDbCtx))

    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (parserContext, rsProvider)
  }

  def buildJPAContext() = {
    val executionContext = ContextFactory.buildExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    val rsProvider = new ORMResultSetProvider(executionContext.getMSIDbConnectionContext, executionContext.getPSDbConnectionContext, executionContext.getPDIDbConnectionContext)

    (executionContext, rsProvider)
  }
  
  protected def resetRSValidation(rs: ResultSet) = {
    rs.peptideMatches.foreach(_.isValidated = true)
  }
  
  @AfterClass
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }
  
}


object ResultSetValidatorF122817Test extends AbstractResultSetValidator with Logging {

  val driverType = DriverType.H2
  val fileName = "STR_F122817_Mascot_v2.3"
  val targetRSId: Long = 1L
  val decoyRSId = Option.empty[Long]
  
}

class ResultSetValidatorF122817Test extends Logging {

  @Test
  def testScoreValidationOnNoneDecoy() = {

    val nbrPepProteo = 1
    val scoreTh = 22.0f
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    val protProteoTypiqueFilters = Seq()
    
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF122817Test.executionContext,
      targetRs = ResultSetValidatorF122817Test.getRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = Some(protProteoTypiqueFilters),
      storeResultSummary = false)

    val result = rsValidation.runService
    Assert.assertTrue("ResultSet validation result", result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm
    logger.info(" rsValidation.validatedTargetRsm "+tRSM.id+" rsValidation.validatedDecoyRsm "+dRSM.isDefined)
    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    
//    Assert.assertFalse(dRSM.isDefined)

  }
  
}

object ResultSetValidatorF136482Test extends AbstractResultSetValidator with Logging {

  val driverType = DriverType.H2
  val fileName = "STR_F136482_CTD"
  val targetRSId = 2L
  val decoyRSId = Some(1L)
  
}

class ResultSetValidatorF136482Test extends Logging {
  
  protected val DEBUG_TESTS = true
  
  @Test
  def testScoreValidation() = {

    val scoreTh = 22.0f
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    
    val rs = ResultSetValidatorF136482Test.getRS()

    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = rs,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = None,
      storeResultSummary = false
    )

    val result = rsValidation.runService
    Assert.assertTrue("ResultSet validation result", result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    Assert.assertTrue(dRSM.isDefined)

    //--- TEST Properties values
    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new ScorePSMFilter().filterDescription, fPrp.getDescription.get)
    Assert.assertEquals("ScoreTh float from properties Map", props(FilterPropertyKeys.THRESHOLD_VALUE), scoreTh)

    val pepValResultsOpt = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(438, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(251, pepValResults.getDecoyMatchesCount.get)
    Assert.assertEquals(72.86, pepValResults.getFdr.get, 0.01)

    //--- TEST PSM Count
    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals("AllTarPepMatc length", 438, allTarPepMatc.length)
    Assert.assertEquals("AllTarPepMatc length", 251, allDecPepMatc.length)

        //--- TEST Peptide and PSM properties 
    rsValidation.validatedTargetRsm.peptideInstances.foreach(pepInst => {
      
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue("PeptideMatch is validated", peptideM.isValidated)
        Assert.assertTrue("PeptideMatch.score > scoreTh", peptideM.score > scoreTh)
      })
    })
  }
  
  
  @Test
  def testRankValidation() = {

    val readRS = ResultSetValidatorF136482Test.getRS()
    val seqBuilder = Seq.newBuilder[IPeptideMatchFilter]
    val rank = 1
    seqBuilder += new RankPSMFilter(pepMatchMaxRank = 1)
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = readRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(seqBuilder.result()),
      pepMatchValidator = None,
      protSetFilters = None,
      protSetValidator = None,
      storeResultSummary = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Rank filter, in Test ")

    Assert.assertNotNull(rsValidation.validatedTargetRsm)
    Assert.assertTrue(rsValidation.validatedDecoyRsm.isDefined)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals("AllTarPepMatc length", 774, allTarPepMatc.length)
    Assert.assertEquals("AllDecPepMatc length", 638, allDecPepMatc.length)

    val pepMatchByQuId = new HashMap[Long, ArrayBuffer[PeptideMatch]]()
    allTarPepMatc.foreach(peptideM => {
      val pepMatches = pepMatchByQuId.get(peptideM.msQueryId).getOrElse(new ArrayBuffer[PeptideMatch]())
      //             println(peptideM.msQueryId+"\t"+peptideM.peptide.sequence+"\t"+peptideM.peptide.ptmString+"\t"+peptideM.score)
      pepMatches + peptideM
      Assert.assertTrue(peptideM.isValidated)
      pepMatchByQuId.put(peptideM.msQueryId, pepMatches)
    })

    allDecPepMatc.foreach(peptideM => {
      val pepMatches = pepMatchByQuId.get(peptideM.msQueryId).getOrElse(new ArrayBuffer[PeptideMatch]())
      pepMatches + peptideM
      //             println(peptideM.msQueryId+"\t"+peptideM.peptide.sequence+"\t"+peptideM.peptide.ptmString)
      Assert.assertTrue(peptideM.isValidated)
      pepMatchByQuId.put(peptideM.msQueryId, pepMatches)
    })

    pepMatchByQuId.foreach(entry => {
      var validatedEntry = false
      if (entry._2.length.equals(1)) {
        validatedEntry = true
      } else {
        var index = 0
        while (index < entry._2.length - 1) {
          validatedEntry = (entry._2(index).score - entry._2(index + 1).score).abs < 0.1
          index += 1
        }
      }

      Assert.assertTrue(validatedEntry)
    })

    logger.debug(" ResultSetValidator testRankValidation test properties")
    val rsmPropTargetCount = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getTargetMatchesCount
    val rsmPropDecoyCount = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getDecoyMatchesCount

    Assert.assertEquals(" RSM validation properties target count ", allTarPepMatc.length, rsmPropTargetCount)
    Assert.assertEquals(" RSM validation properties target count ", allDecPepMatc.length, rsmPropDecoyCount.get)

    val rsPepMatchByQuId = new HashMap[Long, ArrayBuffer[PeptideMatch]]()
    val rsPsm = readRS.peptideMatches ++ readRS.decoyResultSet.get.peptideMatches
    rsPsm.foreach(peptideM => {
      val pepMatches = rsPepMatchByQuId.get(peptideM.msQueryId).getOrElse(new ArrayBuffer[PeptideMatch]())
      pepMatches += (peptideM)
      rsPepMatchByQuId.put(peptideM.msQueryId, pepMatches)
    })

    rsPepMatchByQuId.foreach(entry => {
      val psmEntry = entry._2.sortWith((a, b) => a.score > b.score)
      var firstPSMScore = psmEntry(0).score
      entry._2.foreach(psm => {
        //logger.debug(" -- QID "+entry._1+" PSM "+psm.peptide.sequence+" firstPSMScore "+firstPSMScore+" <> "+psm.score+"  " +psm.isValidated+" ( "+(firstPSMScore - psm.score).abs+" )")
        if ((firstPSMScore - psm.score).abs >= 0.1)
          Assert.assertFalse(psm.isValidated)
        else
          Assert.assertTrue(psm.isValidated)
      })
    })

  }

 @Test
  def testScoreAfterValidation() = {

    val readRS = ResultSetValidatorF136482Test.getRS()
    val seqBuilder = Seq.newBuilder[IPeptideMatchFilter]
    val rank = 1
    seqBuilder += new RankPSMFilter(pepMatchMaxRank = 1)
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = readRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(seqBuilder.result()),
      pepMatchValidator = None,
      protSetFilters = None, 
      protSetValidator = None,
      storeResultSummary = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Rank filter, in Test ")

    Assert.assertNotNull(rsValidation.validatedTargetRsm)
    Assert.assertTrue(rsValidation.validatedDecoyRsm.isDefined)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals("AllTarPepMatc length", 774, allTarPepMatc.length)
    Assert.assertEquals("AllDecPepMatc length",638, allDecPepMatc.length)
    
  }


  @Test
  def testScoreFDRValidation() = {

    val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))
    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = new ScorePSMFilter(),
      expectedFdr = Some(7.0f),
      tdAnalyzer = testTDAnalyzer
    )
    //    ComputedFDRPeptideMatchFilter( 1.0F, new ScorePSMFilter() )
    logger.info(" ResultSetValidator testScoreFDRValidation Create service")
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = testTDAnalyzer,
      pepMatchPreFilters = None,
      pepMatchValidator = Some(fdrValidator),
      protSetFilters = None,
      storeResultSummary = true
    )

    logger.debug(" ResultSetValidator testScoreFDRValidation RUN  service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    val rsmID = rsValidation.validatedTargetRsm.id
    Assert.assertTrue(" ResultSummary was saved (positive id) ", rsmID > 1)
    logger.debug(" End Run ResultSetValidator Service with FDR filter using Score, in Test ")

    Assert.assertNotNull(rsValidation.validatedTargetRsm)
    Assert.assertTrue(rsValidation.validatedDecoyRsm.isDefined)
    Assert.assertTrue(rsValidation.validatedTargetRsm.properties.isDefined)

    val pepFilterPropsOpt = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getParams.getPeptideFilters
    Assert.assertTrue(pepFilterPropsOpt.isDefined)
    val pepFilterProps = pepFilterPropsOpt.get
    Assert.assertEquals(1, pepFilterProps.size)
    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new ScorePSMFilter().filterDescription, fPrp.getDescription.get)

    val scoreThresh = props(FilterPropertyKeys.THRESHOLD_VALUE).asInstanceOf[Float]
    Assert.assertEquals("ScoreThresh float compare", 52.90, scoreThresh, 0.01) // 50.61 with decoy sorting V2

    Assert.assertEquals(7.01f, rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getFdr.get, 0.01)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(55, allTarPepMatc.length)
    Assert.assertEquals(2, allDecPepMatc.length)
    Assert.assertEquals(55,rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getTargetMatchesCount)
    Assert.assertEquals(2,rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getDecoyMatchesCount.get)

    allTarPepMatc.foreach(peptideM => {
      //             println(peptideM.msQueryId+"\t"+peptideM.peptide.sequence+"\t"+peptideM.peptide.ptmString+"\t"+peptideM.score)
      Assert.assertTrue(peptideM.isValidated)
      Assert.assertTrue(peptideM.score >= scoreThresh)
    })

    allDecPepMatc.foreach(peptideM => {
      //             println(peptideM.msQueryId+"\t"+peptideM.peptide.sequence+"\t"+peptideM.peptide.ptmString+"\t"+peptideM.score)
      Assert.assertTrue(peptideM.isValidated)
      Assert.assertTrue(peptideM.score >= scoreThresh)
    })

  }

  @Test
  def testProtSpecificPSMValidation() = {

    val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))
    val scoreTh = 22.0f
    val nbrPepProteo = 1
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    val protProteoTypiqueFilters = Seq(new SpecificPeptidesPSFilter(nbrPepProteo))

    logger.info(" ResultSetValidator testProtSpecificPSMValidation Create service")
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = Some(protProteoTypiqueFilters),
      storeResultSummary = false
    )

    logger.debug(" ResultSetValidator testProtSpecificPSMValidation RUN service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug(" End Run ResultSetValidator Service with FDR filter using Score, in Test ")

    Assert.assertNotNull(rsValidation.validatedTargetRsm)
    Assert.assertTrue(rsValidation.validatedDecoyRsm.isDefined)
    Assert.assertTrue(rsValidation.validatedTargetRsm.properties.isDefined)

    val protFilterPropsOpt = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getParams.getProteinFilters
    Assert.assertTrue(protFilterPropsOpt.isDefined)
    val protFilterProps = protFilterPropsOpt.get
    Assert.assertEquals(1, protFilterProps.size)
    val fPrp: FilterDescriptor = protFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(ProtSetFilterParams.SPECIFIC_PEP.toString, fPrp.getParameter)

    val nbrPep = toInt(props(FilterPropertyKeys.THRESHOLD_VALUE))
    Assert.assertEquals("Specific peptide # compare", nbrPepProteo, nbrPep)

    logger.debug(" Verify Result IN RSM ")
    val removedTarProtSet2 = rsValidation.validatedTargetRsm.proteinSets.filter(!_.isValidated)
    val removedDecProtSet2 = rsValidation.validatedDecoyRsm.get.proteinSets.filter(!_.isValidated)

    val validatedTarProtSet = rsValidation.validatedTargetRsm.proteinSets.filter(_.isValidated)
    val validatedDecProtSet = rsValidation.validatedDecoyRsm.get.proteinSets.filter(_.isValidated)

    val allTarProtSet = rsValidation.validatedTargetRsm.proteinSets
    val allDecProtSet = rsValidation.validatedDecoyRsm.get.proteinSets
    logger.debug(" All Target ProtSet (even not validated) " + allTarProtSet.length + " <>  removed Target ProtSet " + removedTarProtSet2.length)
    logger.debug(" All Decoy ProtSet (even not validated)  " + allDecProtSet.length + " <>  removed Decoy ProtSet " + removedDecProtSet2.length)
    //    Assert.assertEquals("allTarProtSet length", 4, allTarProtSet.length) //VDS DEBUG Pour test final, threshold 2... 
    //    Assert.assertEquals("allDecProtSet length", 1, allDecProtSet.length)
    
    if( DEBUG_TESTS ) {
      removedTarProtSet2.foreach(protSet => {
    
        //DEBUG ONLY 
        logger.debug("---- Removed Protein Set ------ ")
        val firstPrtMatch = rsValidation.validatedTargetRsm.resultSet.get.proteinMatches.filter(_.id == protSet.proteinMatchIds(0))(0)
        println(firstPrtMatch.accession + "\t" + protSet.peptideSet.peptideMatchesCount + "\t" + protSet.isValidated)
        protSet.peptideSet.getPeptideInstances.foreach(pepIns => {
          println("\t" + "\t" + pepIns.peptide.sequence + "\t" + pepIns.peptide.ptmString + "\t" + pepIns.proteinSetsCount)
        })
        logger.debug(" Removed Protein Set - unique pep # " + protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length)
        //        Assert.assertTrue("Protein Set more than 1 unique pep", protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length >= 2 )
      })
    }

    if( DEBUG_TESTS ) {
      
      validatedTarProtSet.foreach(protSet => {
        //DEBUG ONLY 
        logger.debug("---- validatedTarProtSet  ------ ")
        val firstPrtMatch = rsValidation.validatedTargetRsm.resultSet.get.proteinMatches.filter(_.id == protSet.proteinMatchIds(0))(0)
        println(firstPrtMatch.accession + "\t" + protSet.peptideSet.peptideMatchesCount + "\t" + protSet.isValidated)
        protSet.peptideSet.getPeptideInstances.foreach(pepIns => {
          println("\t" + "\t" + pepIns.peptide.sequence + "\t" + pepIns.peptide.ptmString + "\t" + pepIns.proteinSetsCount)
        })
        logger.debug(" Protein Set - unique pep # " + protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length)
        //        Assert.assertTrue("Protein Set more than 1 unique pep", protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length >= 2 )
      })
  
      validatedDecProtSet.foreach(protSet => {
        //DEBUG ONLY 
        logger.debug("---- validatedDecProtSet  ------ ")
        val firstPrtMatch = rsValidation.validatedDecoyRsm.get.resultSet.get.proteinMatches.filter(_.id == protSet.proteinMatchIds(0))(0)
        println(firstPrtMatch.accession + "\t" + protSet.peptideSet.peptideMatchesCount + "\t" + protSet.isValidated)
        protSet.peptideSet.getPeptideInstances.foreach(pepIns => {
          println("\t" + "\t" + pepIns.peptide.sequence + "\t" + pepIns.peptide.ptmString + "\t" + pepIns.proteinSetsCount)
        })
        logger.debug(" Protein Set - unique pep # " + protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length)
        //        Assert.assertTrue("Protein Set more than 1 unique pep", protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length >= 2 )
      })
    }
  }

  @Test
  def testRankAndScoreFDRValidation() = {

    val firstRankFilter = new RankPSMFilter(1)
    val valFilter = new ScorePSMFilter()
    // val testTDAnalyzer = Some(new CompetitionBasedTDAnalyzer(valFilter))
    val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))
    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = valFilter,
      expectedFdr = Some(7.0f),
      tdAnalyzer = testTDAnalyzer
    )

    logger.info("ResultSetValidator testRankAndScoreFDRValidation Create service")
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = testTDAnalyzer,
      pepMatchPreFilters = Some(Seq(firstRankFilter)),
      pepMatchValidator = Some(fdrValidator),
      protSetFilters = None,
      protSetValidator = None,
      storeResultSummary = false
    )

    logger.debug("ResultSetValidator testRankAndScoreFDRValidation RUN service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug("End Run ResultSetValidator Service with FDR filter using Rank and Score, in Test ")

    logger.debug("Verify Result IN RS")
    val rsTarPepMatches = rsValidation.validatedTargetRsm.resultSet.get.peptideMatches
    val rsDecPepMatches = rsValidation.validatedDecoyRsm.get.resultSet.get.peptideMatches
    Assert.assertEquals("RsTarPepMatches validated count", 55, rsTarPepMatches.count(_.isValidated)) // 102 with competition
    Assert.assertEquals("RsDecPepMatches validated count", 2, rsDecPepMatches.count(_.isValidated)) // 16 with competition

    logger.debug("Verify Result IN RSM")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(55, allTarPepMatc.length) // 102 with competition
    Assert.assertEquals(2, allDecPepMatc.length)
    Assert.assertEquals(55,rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getTargetMatchesCount)
    Assert.assertEquals(2,rsValidation.validatedDecoyRsm.get.properties.get.getValidationProperties.get.getResults.getPeptideResults.get.getDecoyMatchesCount.get)

  }
  
  
  @Test
  def testProtSetFDRValidation() = {

    val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))

    // Create protein set validator
    val protSetValidator = new ProtSetRulesValidatorWithFDROptimization(
      //protSetScoreUpdater = Some(new MascotProteinSetScoreUpdater(-20f)),
      protSetFilterRule1 = new ScoreProtSetFilter,
      protSetFilterRule2 = new ScoreProtSetFilter,
      expectedFdr = Some(1.0f),
      targetDecoyMode = Some(TargetDecoyModes.CONCATENATED)
    )

    logger.info("ResultSetValidator testProtSetFDRValidation Create service")
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = testTDAnalyzer,
      pepMatchPreFilters = None,
      pepMatchValidator = None,
      protSetFilters = None,
      protSetValidator = Some(protSetValidator),
      storeResultSummary = false
    )

    logger.debug("ResultSetValidator testProtSetFDRValidation RUN service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug("End Run ResultSetValidator Service for testProtSetFDRValidation")

    logger.debug("Verify Result IN RSM")
    val allTarProtSets = rsValidation.validatedTargetRsm.proteinSets
    val allDecProtSets = rsValidation.validatedDecoyRsm.get.proteinSets
    Assert.assertEquals("AllTarProtSets validated count", 13, allTarProtSets.count(_.isValidated))
    Assert.assertEquals("AllDecProtSets validated count", 0, allDecProtSets.count(_.isValidated))
  }

  
  @Test
  def testPepMatchAndProtSetFDRValidation() = {

    val tdMode = TargetDecoyModes.CONCATENATED
    val firstRankFilter = new RankPSMFilter(1)
    val pepMatchValFilter = new ScorePSMFilter()
    //val testTDAnalyzer = Some(new CompetitionBasedTDAnalyzer(pepMatchValFilter))
    val testTDAnalyzer = Some(new BasicTDAnalyzer(tdMode))

    // Create peptide match validator
    val pepMatchValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = pepMatchValFilter,
      expectedFdr = Some(7.0f),
      tdAnalyzer = testTDAnalyzer
    )

    // Create protein set validator
    val protSetValidator = new ProtSetRulesValidatorWithFDROptimization(
      //protSetScoreUpdater = Some(new MascotProteinSetScoreUpdater(-20f)),
      protSetFilterRule1 = new ScoreProtSetFilter,
      protSetFilterRule2 = new ScoreProtSetFilter,
      expectedFdr = Some(1.0f),
      targetDecoyMode = Some(tdMode)
      )

    logger.info("ResultSetValidator testPepMatchAndProtSetFDRValidation Create service")
    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = testTDAnalyzer,
      pepMatchPreFilters = Some(Seq(firstRankFilter)),
      pepMatchValidator = Some(pepMatchValidator),
      protSetFilters = None,
      protSetValidator = Some(protSetValidator),
      inferenceMethod = Some(InferenceMethods.parsimonious),
      peptideSetScoring = Some(PepSetScoring.MASCOT_PEPTIDE_SET_SCORE),
      storeResultSummary = false
    )

    logger.debug("ResultSetValidator testPepMatchAndProtSetFDRValidation RUN service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug("End Run ResultSetValidator Service for testPepMatchAndProtSetFDRValidation")

    logger.debug("Verify Result IN RSM")
    val allTarProtSets = rsValidation.validatedTargetRsm.proteinSets
    val allDecProtSets = rsValidation.validatedDecoyRsm.get.proteinSets
    Assert.assertEquals("AllTarProtSets validated count", 6, allTarProtSets.count(_.isValidated))
    Assert.assertEquals("AllDecProtSets validated count", 0, allDecProtSets.count(_.isValidated))
    
    
    /*logger.debug("Check that validatedProteinSetsCount is updated")
    val protSetCountSum = rsValidation.validatedTargetRsm.peptideInstances.foldLeft(0)( (s,p) => s+p.proteinSetsCount )
    val valProtSetCountSum = rsValidation.validatedTargetRsm.peptideInstances.foldLeft(0)( (s,p) => s+p.validatedProteinSetsCount )
    Assert.assertNotEquals("Validated protein sets count should be updated",protSetCountSum, valProtSetCountSum)*/
    
    /*
    // FIXME the peptide QDILDR seems to be not validated anymore
    val myPepInst = rsValidation.validatedTargetRsm.peptideInstances.find(_.peptide.uniqueKey == "QDILDR%")
    Assert.assertEquals("Protein sets count",1,myPepInst.get.proteinSetsCount)
    Assert.assertEquals("Validated protein sets count",0,myPepInst.get.validatedProteinSetsCount)
    */
    

    
    ()
  }

  @Test
  def testMascotPValueValidation() = {
    val pValTh = 0.01f
    val pepFilters = Seq(new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = false))

    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = None,
      storeResultSummary = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    Assert.assertTrue(dRSM.isDefined)
    Assert.assertTrue(tRSM.proteinSets.length>0)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    //    val allPepMatches = allTarPepMatc++allDecPepMatc
    //    allPepMatches.foreach( pepM  => {
    //	logger.debug(pepM.msQueryId+"\t"+pepM.peptide.sequence+"\t"+pepM.peptide.ptmString)
    //    } )

    Assert.assertEquals(57, allTarPepMatc.length + allDecPepMatc.length)

    rsValidation.validatedTargetRsm.peptideInstances.foreach(pepInst => {
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue(peptideM.isValidated)
      })
    })

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter().filterDescription, fPrp.getDescription.get)
    Assert.assertEquals(props(FilterPropertyKeys.THRESHOLD_VALUE), pValTh)

    val pepValResultsOpt = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(55, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(2, pepValResults.getDecoyMatchesCount.get)
 
    
  }

  @Test
  def testMascotHomologyPValueValidation() = {
    val pValTh = 0.01f
    val pepFilters = Seq(new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = true))

    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = ResultSetValidatorF136482Test.getRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = None,
      storeResultSummary = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    Assert.assertTrue(dRSM.isDefined)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    //    val allPepMatches = allTarPepMatc++allDecPepMatc
    //    allPepMatches.foreach( pepM  => {
    //	logger.debug(pepM.msQueryId+"\t"+pepM.peptide.sequence+"\t"+pepM.peptide.ptmString)
    //    } )
    logger.debug(" allTarPepMatc " + allTarPepMatc.length) //IRMa 81 + 0 duplicated between target and decoy 

    logger.debug(" allDecPepMatc  " + allDecPepMatc.length) //IRMa 3 + 0 duplicated between target and decoy 
    Assert.assertEquals(84, allTarPepMatc.length + allDecPepMatc.length)

    rsValidation.validatedTargetRsm.peptideInstances.foreach(pepInst => {
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue(peptideM.isValidated)
      })
    })

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter(useHomologyThreshold = true).filterDescription, fPrp.getDescription.get)
    Assert.assertEquals(props(FilterPropertyKeys.THRESHOLD_VALUE), pValTh)

    val pepValResultsOpt = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(81, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(3, pepValResults.getDecoyMatchesCount.get)
  }

}

object ResultSetValidatorF068213Test extends AbstractResultSetValidator with Logging {

  val driverType = DriverType.H2
  val fileName = "GRE_F068213_M2.4_TD_EColi"
  val targetRSId = 2L
  val decoyRSId = Some(1L)
  
}

class ResultSetValidatorF068213Test extends Logging {
  
	@Test
	def testMascotFDRPValueValidation() = {
	  val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))
	  val pValTh = 0.05f	 
	  val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
		  validationFilter = new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = false),
		  expectedFdr = Some(0.60240966f),
		  tdAnalyzer = testTDAnalyzer
	  )
    
     
     logger.info(" ResultSetValidator test FDR on IT Validation . Create service")
     val rsValidation = new ResultSetValidator(
		 execContext = ResultSetValidatorF068213Test.executionContext,
		 targetRs = ResultSetValidatorF068213Test.getRS,
		 tdAnalyzer = testTDAnalyzer,
		 pepMatchPreFilters = None,
		 pepMatchValidator = Some(fdrValidator),
		 protSetFilters = None,
		 storeResultSummary = true
	 )
     
	  
	  val result = rsValidation.runService
	  Assert.assertTrue(result)
	  logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

	  val tRSM = rsValidation.validatedTargetRsm
	  val dRSM = rsValidation.validatedDecoyRsm

	  Assert.assertNotNull(tRSM)
	  Assert.assertNotNull(dRSM)
	  Assert.assertTrue(dRSM.isDefined)
	  Assert.assertTrue(tRSM.proteinSets.length>0) //Test to see bug #7831 

	  logger.debug(" Verify Result IN RSM ")
	  val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
	  val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)

	  //VD : Fails du to none sortable MascotPValueFilter
	  Assert.assertEquals(996, allTarPepMatc.length + allDecPepMatc.length) 
	  logger.debug(" allTarPepMatc "+allTarPepMatc.length +" allDecPepMatc "+ allDecPepMatc.length)
    
	  Assert.assertTrue(tRSM.properties.isDefined)
	  Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.isDefined)

	  val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.get
	  Assert.assertEquals(1, pepFilterProps.size)

	  val fPrp: FilterDescriptor = pepFilterProps(0)
	  val props = fPrp.getProperties.get
	  Assert.assertEquals(1, props.size)
	  Assert.assertEquals(new MascotPValuePSMFilter().filterDescription, fPrp.getDescription.get)

	  val pepValResults = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults.get
   //VD : Fails du to none sortable MascotPValueFilter
	  Assert.assertEquals(allTarPepMatc.length, pepValResults.getTargetMatchesCount)
	  Assert.assertEquals(allDecPepMatc.length, pepValResults.getDecoyMatchesCount.get)
      logger.debug(" -------------------- FDR = "+pepValResults.getFdr)
  }
    
  @Test
  def testOtherMascotPValueValidation() = {
    
    val pValTh = 0.1f
    val pepFilters = Seq(new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = false))

    val rsValidation = new ResultSetValidator(
      execContext = ResultSetValidatorF068213Test.executionContext,
      targetRs = ResultSetValidatorF068213Test.getRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = None,
      storeResultSummary = false
    )
 
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Identity Threshold Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    Assert.assertTrue(dRSM.isDefined)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = rsValidation.validatedTargetRsm.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = rsValidation.validatedDecoyRsm.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    logger.debug("  - allTarPepMatc " + allTarPepMatc.length + " allDecPepMatc " + allDecPepMatc.length)
    Assert.assertEquals(996, allTarPepMatc.length + allDecPepMatc.length) //IRMa -> 1000! 

    rsValidation.validatedTargetRsm.peptideInstances.foreach(pepInst => {
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue(peptideM.isValidated)
      })
    })

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPeptideFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter().filterDescription, fPrp.getDescription.get)
    Assert.assertEquals(props(FilterPropertyKeys.THRESHOLD_VALUE), pValTh)

    val pepValResultsOpt = rsValidation.validatedTargetRsm.properties.get.getValidationProperties.get.getResults.getPeptideResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(993, pepValResults.getTargetMatchesCount) // IRMa 997
    Assert.assertEquals(3, pepValResults.getDecoyMatchesCount.get) //IRMa 3
    Assert.assertEquals(993, allTarPepMatc.length) 
    Assert.assertEquals(3, allDecPepMatc.length) 
    logger.debug(" -------------------- FDR = "+pepValResults.getFdr)
    
  }

}
