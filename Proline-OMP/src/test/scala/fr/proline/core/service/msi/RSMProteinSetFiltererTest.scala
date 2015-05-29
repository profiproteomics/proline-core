package fr.proline.core.service.msi

import scala.Array.canBuildFrom

import org.junit.Assert
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.util.primitives.toInt
import fr.proline.core.algo.msi.DbUnitResultFileLoading
import fr.proline.core.algo.msi.filtering.FilterPropertyKeys
import fr.proline.core.algo.msi.filtering.ProtSetFilterParams
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.filtering.proteinset.ScoreProtSetFilter
import fr.proline.core.algo.msi.filtering.proteinset.SpecificPeptidesPSFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.algo.msi.validation.proteinset.ProtSetRulesValidatorWithFDROptimization
import fr.proline.core.dal.AbstractEmptyDatastoreTestCase
import fr.proline.core.dbunit.STR_F136482_CTD
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.repository.DriverType





object RSMProtSetFiltererF136482Test extends AbstractEmptyDatastoreTestCase with DbUnitResultFileLoading with Logging {

  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F136482_CTD
  val targetRSId = 2L
  val decoyRSId = Some(1L)
  val useJPA = false
  
  override def getRS(): ResultSet = {
    this.resetRSValidation(readRS)
    if (readRS.decoyResultSet.isDefined) this.resetRSValidation(readRS.decoyResultSet.get)
    this.readRS
  }
  
  protected def resetRSValidation(rs: ResultSet) = {
    rs.peptideMatches.foreach(_.isValidated = true)
  }
  
}

class RSMProtSetFiltererF136482Test extends Logging {
  
  protected val DEBUG_TESTS = false
  val targetRS = RSMProtSetFiltererF136482Test.getRS
  val executionContext = RSMProtSetFiltererF136482Test.executionContext
  
  require( targetRS != null, "targetRS is null")
  require( executionContext != null, "executionContext is null")
  

  @Test
  def testProtSpecificValidation() {

    val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))
    val scoreTh = 22.0f
    val nbrPepProteo = 1
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    val protProteoTypiqueFilters = Seq(new SpecificPeptidesPSFilter(nbrPepProteo))

    logger.info(" RSMProtSetFilterer : step1. validate RS using score")
    val rsValidation = new ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = None, //Some(protProteoTypiqueFilters),
      storeResultSummary = false
    )

 
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug(" End Run RSMProtSetFilterer step1 ")
    
    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertTrue(dRSM.isDefined)
    Assert.assertTrue(tRSM.properties.isDefined)

    var protFilterPropsOpt = tRSM.properties.get.getValidationProperties.get.getParams.getProteinFilters
    Assert.assertFalse(protFilterPropsOpt.isDefined)
    Assert.assertTrue("all targetRSM ProteinSet should validated ", tRSM.proteinSets.count(!_.isValidated) == 0)
    Assert.assertTrue("all decoyRSM  ProteinSet should validated ", dRSM.get.proteinSets.count(!_.isValidated) == 0)

    logger.info(" RSMProtSetFilterer : step2. filter protein set ")
    val rsmProtSetFilerer = new RSMProteinSetFilterer(
		execCtx = executionContext,
		targetRsm = tRSM, 
		protSetFilters = protProteoTypiqueFilters
	)	
    val result2 = rsmProtSetFilerer.runService
    Assert.assertTrue(result2)
    logger.debug(" End Run RSMProtSetFilterer step2 ")
    
    protFilterPropsOpt = tRSM.properties.get.getValidationProperties.get.getParams.getProteinFilters
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
    val removedTarProtSet2Count = tRSM.proteinSets.count(!_.isValidated)
    val removedDecProtSet2Count = dRSM.get.proteinSets.count(!_.isValidated)

    val allTarProtSet = tRSM.proteinSets
    val allDecProtSet = dRSM.get.proteinSets
    logger.debug(" All Target ProtSet (even not validated) " + allTarProtSet.length + " <>  removed Target ProtSet " + removedTarProtSet2Count)
    logger.debug(" All Decoy ProtSet (even not validated)  " + allDecProtSet.length + " <>  removed Decoy ProtSet " + removedDecProtSet2Count)
    Assert.assertEquals("allTarProtSet length", 225, allTarProtSet.filter(_.isValidated).length)  
    Assert.assertEquals("allDecProtSet length", 207, allDecProtSet.filter(_.isValidated).length)
    
  }
  

  @Test
  def testProtSetFDRValidation() {
    
    val testTDAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED))
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = 20))

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
      execContext = executionContext,
      targetRs = targetRS,
      tdAnalyzer = testTDAnalyzer,
      pepMatchPreFilters = Some(pepFilters),
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
    Assert.assertEquals("AllTarProtSets validated count", 12, allTarProtSets.count(_.isValidated))
    Assert.assertEquals("AllDecProtSets validated count", 0, allDecProtSets.count(_.isValidated))
  }
 

}

