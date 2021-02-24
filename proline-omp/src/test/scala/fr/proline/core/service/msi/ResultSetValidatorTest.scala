package fr.proline.core.service.msi

import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.algo.msi.InferenceMethod
import fr.proline.core.algo.msi.filtering.pepmatch._
import fr.proline.core.algo.msi.filtering.proteinset.{ScoreProtSetFilter, SpecificPeptidesPSFilter}
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IPeptideMatchFilter, _}
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.algo.msi.validation.pepmatch.TDPepMatchValidatorWithFDROptimization
import fr.proline.core.algo.msi.validation.proteinset.ProtSetRulesValidatorWithFDROptimization
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.{FilterDescriptor, PeptideMatch}
import fr.proline.repository.DriverType
import org.junit.{Assert, Test}

import scala.collection.mutable.{ArrayBuffer, HashMap}

object ResultSetValidatorF122817Test extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = STR_F122817_Mascot_v2_3
  override val useJPA = false

  val targetRSId: Long = 1L

}

class ResultSetValidatorF122817Test extends StrictLogging {
  
  val targetRS = ResultSetValidatorF122817Test.getRS(ResultSetValidatorF122817Test.targetRSId)
  ResultSetValidatorF122817Test.resetRSValidation(targetRS)
  val executionContext = ResultSetValidatorF122817Test.executionContext
  
  require( targetRS != null, "targetRS is null" )
  require( executionContext != null, "executionContext is null" )

  @Test
  def testScoreValidationOnNoneDecoy() {

    val scoreTh = 22.0f
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))

    val rsValidation =  ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = None,
        pepMatchPreFilters = Some(pepFilters),
        pepMatchValidator = None,
        protSetFilters = None),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS), 
      storeResultSummary = true,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    val result = rsValidation.runService
    Assert.assertTrue("ResultSet validation result", result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm
    logger.info(" rsValidation.validatedTargetRsm "+tRSM.id+" rsValidation.validatedDecoyRsm "+dRSM.isDefined)
    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    
    val nbrSamsetPepSet = tRSM.peptideSets.count(!_.isSubset)
    val nbrProtSet = tRSM.proteinSets.length
    
    val nbrPepIWOTotalLeaveMCount = tRSM.peptideInstances.filter(_.totalLeavesMatchCount <0).length
    Assert.assertEquals(0,  nbrPepIWOTotalLeaveMCount)
    /*val pepSetIds = tRSM.peptideSets.withFilter(!_.isSubset).map(_.id).toSeq
    rsValidation.validatedTargetRsm.proteinSets.foreach(protSet => {
     if(!pepSetIds.contains(protSet.peptideSetId))
        logger.debug("protSet "+protSet.id+" linked to subset peptideSet. Typical ID = "+protSet.getTypicalProteinMatchId)               
    })
    */
    
    Assert.assertEquals(nbrProtSet,nbrSamsetPepSet)
  }
  
}

object ResultSetValidatorF136482Test extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = STR_F136482_CTD
  override val useJPA: Boolean = false

  val targetRSId = 2L
  val decoyRSId = Some(1L)
  
}

class ResultSetValidatorF136482Test extends StrictLogging {
  
  protected val DEBUG_TESTS = false
  val targetRS = ResultSetValidatorF136482Test.getRS(ResultSetValidatorF136482Test.targetRSId, ResultSetValidatorF136482Test.decoyRSId)
  ResultSetValidatorF136482Test.resetRSValidation(targetRS)
  val executionContext = ResultSetValidatorF136482Test.executionContext
  
  require( targetRS != null, "targetRS is null")
  require( executionContext != null, "executionContext is null")
  
  @Test
  def testScoreValidation() {

    val scoreTh = 22.0f
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(pepFilters),
        pepMatchValidator = None,
        protSetFilters = None),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
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
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new ScorePSMFilter().filterDescription, fPrp.getDescription.get)
    Assert.assertEquals("ScoreTh float from properties Map", props(FilterPropertyKeys.THRESHOLD_VALUE), scoreTh)

    val pepValResultsOpt = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(438, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(251, pepValResults.getDecoyMatchesCount.get)
    Assert.assertEquals(72.86, pepValResults.getFdr.get, 0.01)

    //--- TEST PSM Count
    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals("AllTarPepMatc length", 438, allTarPepMatc.length)
    Assert.assertEquals("AllTarPepMatc length", 251, allDecPepMatc.length)

    //--- TEST Peptide and PSM properties 
    tRSM.peptideInstances.foreach(pepInst => {
      
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue("PeptideMatch is validated", peptideM.isValidated)
        Assert.assertTrue("PeptideMatch.score > scoreTh", peptideM.score > scoreTh)
      })
    })
  }
  
  
  @Test
  def testRankValidation() {

    val seqBuilder = Seq.newBuilder[IPeptideMatchFilter]
    val maxRank = 1
    seqBuilder += new PrettyRankPSMFilter(maxPrettyRank = maxRank)
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchPreFilters = Some(seqBuilder.result())),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Rank filter, in Test ")
    
    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertTrue(dRSM.isDefined)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals("AllTarPepMatc length", 774, allTarPepMatc.length)
    Assert.assertEquals("AllDecPepMatc length", 638, allDecPepMatc.length)

    val pepMatchByQuId = new HashMap[Long, ArrayBuffer[PeptideMatch]]()
    allTarPepMatc.foreach(peptideM => {
      val pepMatches = pepMatchByQuId.get(peptideM.msQueryId).getOrElse(new ArrayBuffer[PeptideMatch]())
      //             println(peptideM.msQueryId+"\t"+peptideM.peptide.sequence+"\t"+peptideM.peptide.ptmString+"\t"+peptideM.score)
      pepMatches += peptideM
      Assert.assertTrue(peptideM.isValidated)
      pepMatchByQuId.put(peptideM.msQueryId, pepMatches)
    })

    allDecPepMatc.foreach(peptideM => {
      val pepMatches = pepMatchByQuId.get(peptideM.msQueryId).getOrElse(new ArrayBuffer[PeptideMatch]())
      pepMatches += peptideM
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
    val pepValidationResults = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get
    val rsmPropTargetCount = pepValidationResults.getTargetMatchesCount
    val rsmPropDecoyCount = pepValidationResults.getDecoyMatchesCount

    Assert.assertEquals(" RSM validation properties target count ", allTarPepMatc.length, rsmPropTargetCount)
    Assert.assertEquals(" RSM validation properties target count ", allDecPepMatc.length, rsmPropDecoyCount.get)

    val rsPepMatchByQuId = new HashMap[Long, ArrayBuffer[PeptideMatch]]()
    val rsPsm = targetRS.peptideMatches ++ targetRS.decoyResultSet.get.peptideMatches
    rsPsm.foreach(peptideM => {
      val pepMatches = rsPepMatchByQuId.get(peptideM.msQueryId).getOrElse(new ArrayBuffer[PeptideMatch]())
      pepMatches += (peptideM)
      rsPepMatchByQuId.put(peptideM.msQueryId, pepMatches)
    })

    rsPepMatchByQuId.foreach(entry => {
      val psmEntry = entry._2.sortWith((a, b) => a.score > b.score)
      val firstPSMScore = psmEntry(0).score
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
  def testScoreAfterValidation() {

    val seqBuilder = Seq.newBuilder[IPeptideMatchFilter]
    val maxRank = 1
    seqBuilder += new PrettyRankPSMFilter(maxPrettyRank = maxRank)
   val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(seqBuilder.result())),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Rank filter, in Test ")
    
    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertTrue(dRSM.isDefined)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals("AllTarPepMatc length", 774, allTarPepMatc.length)
    Assert.assertEquals("AllDecPepMatc length",638, allDecPepMatc.length)
    
  }


  @Test
  def testScoreFDRValidation() {

    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = new ScorePSMFilter(),
      expectedFdr = Some(7.0f)
    )

    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    //    ComputedFDRPeptideMatchFilter( 1.0F, new ScorePSMFilter() )
    logger.info(" ResultSetValidator testScoreFDRValidation Create service")
    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchValidator = Some(fdrValidator)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    logger.debug(" ResultSetValidator testScoreFDRValidation RUN  service")
    val result = rsValidation.runService
    
    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm
    
    Assert.assertTrue(result)
    //val rsmID = tRSM.id
   // Assert.assertTrue(" ResultSummary was saved (positive id) ", rsmID > 1)
    logger.debug(" End Run ResultSetValidator Service with FDR filter using Score, in Test ")

    Assert.assertNotNull(tRSM)
    Assert.assertTrue(dRSM.isDefined)
    Assert.assertTrue(tRSM.properties.isDefined)

    val pepFilterPropsOpt = tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters
    Assert.assertTrue(pepFilterPropsOpt.isDefined)
    val pepFilterProps = pepFilterPropsOpt.get
    Assert.assertEquals(1, pepFilterProps.size)
    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new ScorePSMFilter().filterDescription, fPrp.getDescription.get)

    val scoreThresh = props(FilterPropertyKeys.THRESHOLD_VALUE).asInstanceOf[Float]
    Assert.assertEquals("ScoreThresh float compare", 50.69, scoreThresh, 0.01) 

    Assert.assertEquals(6.66f, tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getFdr.get, 0.01)

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(58, allTarPepMatc.length)
    Assert.assertEquals(2, allDecPepMatc.length)
    
    val pepValidationResults = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get
    Assert.assertEquals(58,pepValidationResults.getTargetMatchesCount)
    Assert.assertEquals(2,pepValidationResults.getDecoyMatchesCount.get)

    val rocPoints = tRSM.peptideValidationRocCurve
    Assert.assertNotNull(rocPoints.get)
    
    val serializedProperties = rocPoints.map(ProfiJson.serialize(_))
    logger.info("rocPoints props = "+serializedProperties.get)

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
  def testProtSpecificPSMValidation() {

    val scoreTh = 22.0f
    val nbrPepProteo = 1
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    val protProteoTypiqueFilters = Seq(new SpecificPeptidesPSFilter(nbrPepProteo))
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    logger.info(" ResultSetValidator testProtSpecificPSMValidation Create service")
    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchPreFilters = Some(pepFilters), protSetFilters = Some(protProteoTypiqueFilters)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    logger.debug(" ResultSetValidator testProtSpecificPSMValidation RUN service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug(" End Run ResultSetValidator Service with FDR filter using Score, in Test ")
    
    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertTrue(dRSM.isDefined)
    Assert.assertTrue(tRSM.properties.isDefined)

    val protFilterPropsOpt = tRSM.properties.get.getValidationProperties.get.getParams.getProteinFilters
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
    //    Assert.assertEquals("allTarProtSet length", 4, allTarProtSet.length) //VDS DEBUG Pour test final, threshold 2... 
    //    Assert.assertEquals("allDecProtSet length", 1, allDecProtSet.length)
    
    if( DEBUG_TESTS ) {
      
      val removedTarProtSet2 = tRSM.proteinSets.filter(!_.isValidated)
      val removedDecProtSet2 = dRSM.get.proteinSets.filter(!_.isValidated)
      val validatedTarProtSet = tRSM.proteinSets.filter(_.isValidated)
      val validatedDecProtSet = dRSM.get.proteinSets.filter(_.isValidated)
      
      removedTarProtSet2.foreach(protSet => {
    
        //DEBUG ONLY 
        logger.debug("---- Removed Protein Set ------ ")
        val firstPrtMatch = tRSM.resultSet.get.proteinMatches.filter(_.id == protSet.getSameSetProteinMatchIds(0))(0)
        println(firstPrtMatch.accession + "\t" + protSet.peptideSet.peptideMatchesCount + "\t" + protSet.isValidated)
        protSet.peptideSet.getPeptideInstances.foreach(pepIns => {
          println("\t" + "\t" + pepIns.peptide.sequence + "\t" + pepIns.peptide.ptmString + "\t" + pepIns.proteinSetsCount)
        })
        logger.debug(" Removed Protein Set - unique pep # " + protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length)
        //        Assert.assertTrue("Protein Set more than 1 unique pep", protSet.peptideSet.getPeptideInstances.filter(_.proteinSetsCount == 1).length >= 2 )
      })
      
      validatedTarProtSet.foreach(protSet => {
        //DEBUG ONLY 
        logger.debug("---- validatedTarProtSet  ------ ")
        val firstPrtMatch = tRSM.resultSet.get.proteinMatches.filter(_.id == protSet.samesetProteinMatchIds(0))(0)
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
        val firstPrtMatch = dRSM.get.resultSet.get.proteinMatches.filter(_.id == protSet.samesetProteinMatchIds(0))(0)
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
  def testRankAndScoreFDRValidation() {

    val firstRankFilter = new PrettyRankPSMFilter(1)
    val valFilter = new ScorePSMFilter()
    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = valFilter,
      expectedFdr = Some(7.0f)
    )

    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    logger.info("ResultSetValidator testRankAndScoreFDRValidation Create service")
    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(Seq(firstRankFilter)),
        pepMatchValidator = Some(fdrValidator)
      ),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    logger.debug("ResultSetValidator testRankAndScoreFDRValidation RUN service")
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug("End Run ResultSetValidator Service with FDR filter using Rank and Score, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm
    
    logger.debug("Verify Result IN RS")
    val rsTarPepMatches = tRSM.resultSet.get.peptideMatches
    val rsDecPepMatches = dRSM.get.resultSet.get.peptideMatches
    Assert.assertEquals("RsTarPepMatches validated count", 58, rsTarPepMatches.count(_.isValidated)) // 102 with competition
    Assert.assertEquals("RsDecPepMatches validated count", 2, rsDecPepMatches.count(_.isValidated)) // 16 with competition

    logger.debug("Verify Result IN RSM")
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(58, allTarPepMatc.length) // 102 with competition
    Assert.assertEquals(2, allDecPepMatc.length)
    Assert.assertEquals(58,tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getTargetMatchesCount)
    Assert.assertEquals(2,dRSM.get.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getDecoyMatchesCount.get)

  }
  
  @Test
  def testProtSetFDRValidation() {
    
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = 20))

    // Create protein set validator
    val protSetValidator = new ProtSetRulesValidatorWithFDROptimization(
      //protSetScoreUpdater = Some(new MascotProteinSetScoreUpdater(-20f)),
      protSetFilterRule1 = new ScoreProtSetFilter,
      protSetFilterRule2 = new ScoreProtSetFilter,
      expectedFdr = Some(1.0f)
    )

    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    logger.info("ResultSetValidator testProtSetFDRValidation Create service")
    val rsValidation =  ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(pepFilters),
        protSetValidator = Some(protSetValidator)
      ),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
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

  
  @Test
  def testPepMatchAndProtSetFDRValidation() {
    
    val tdMode = TargetDecoyModes.CONCATENATED
    val firstRankFilter = new PrettyRankPSMFilter(1)
    val pepMatchValFilter = new ScorePSMFilter()

    // Create peptide match validator
    val pepMatchValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = pepMatchValFilter,
      expectedFdr = Some(7.0f)
    )

    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    // Create protein set validator
    val protSetValidator = new ProtSetRulesValidatorWithFDROptimization(
      //protSetScoreUpdater = Some(new MascotProteinSetScoreUpdater(-20f)),
      protSetFilterRule1 = new ScoreProtSetFilter,
      protSetFilterRule2 = new ScoreProtSetFilter,
      expectedFdr = Some(1.0f)
    )


    logger.info("ResultSetValidator testPepMatchAndProtSetFDRValidation Create service")
    val rsValidation = ResultSetValidator(
      execContext = ResultSetValidatorF136482Test.executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(Seq(firstRankFilter)),
        pepMatchValidator = Some(pepMatchValidator),
        protSetValidator = Some(protSetValidator),
        pepSetScoring = Some(PepSetScoring.MASCOT_MODIFIED_MUDPIT_SCORE)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
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
  def testMascotPValueValidation() {
    val pValTh = 0.01f
    val pepFilters = Seq(new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = false))

    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    val rsValidation =  ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchPreFilters = Some(pepFilters)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
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
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    //    val allPepMatches = allTarPepMatc++allDecPepMatc
    //    allPepMatches.foreach( pepM  => {
    //	logger.debug(pepM.msQueryId+"\t"+pepM.peptide.sequence+"\t"+pepM.peptide.ptmString)
    //    } )

    Assert.assertEquals(57, allTarPepMatc.length + allDecPepMatc.length)

    tRSM.peptideInstances.foreach(pepInst => {
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue(peptideM.isValidated)
      })
    })

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter().filterDescription, fPrp.getDescription.get)
    Assert.assertEquals(props(FilterPropertyKeys.THRESHOLD_VALUE), pValTh)

    val pepValResultsOpt = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(55, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(2, pepValResults.getDecoyMatchesCount.get)
 
    
  }

  @Test
  def testMascotHomologyPValueValidation() {
    val pValTh = 0.01f
    val pepFilters = Seq(new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = true))
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchPreFilters = Some(pepFilters)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
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
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    //    val allPepMatches = allTarPepMatc++allDecPepMatc
    //    allPepMatches.foreach( pepM  => {
    //	logger.debug(pepM.msQueryId+"\t"+pepM.peptide.sequence+"\t"+pepM.peptide.ptmString)
    //    } )
    logger.debug(" allTarPepMatc " + allTarPepMatc.length) //IRMa 81 + 0 duplicated between target and decoy 

    logger.debug(" allDecPepMatc  " + allDecPepMatc.length) //IRMa 3 + 0 duplicated between target and decoy 
    Assert.assertEquals(84, allTarPepMatc.length + allDecPepMatc.length)

    tRSM.peptideInstances.foreach(pepInst => {
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue(peptideM.isValidated)
      })
    })

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter(useHomologyThreshold = true).filterDescription, fPrp.getDescription.get)
    Assert.assertEquals(props(FilterPropertyKeys.THRESHOLD_VALUE), pValTh)

    val pepValResultsOpt = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(81, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(3, pepValResults.getDecoyMatchesCount.get)
  }

}

object ResultSetValidatorF068213Test extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = GRE_F068213_M2_4_TD_EColi
  override val useJPA: Boolean = false

  val targetRSId = 2L
  val decoyRSId = Some(1L)

}

class ResultSetValidatorF068213Test extends StrictLogging {
  
  val targetRS = ResultSetValidatorF068213Test.getRS(ResultSetValidatorF068213Test.targetRSId, ResultSetValidatorF068213Test.decoyRSId)
  ResultSetValidatorF068213Test.resetRSValidation(targetRS)
  val executionContext = ResultSetValidatorF068213Test.executionContext
  
  require( targetRS != null, "targetRS is null")
  require( executionContext != null, "executionContext is null")

  @Test
  def testMascotFDRPValueValidation() {

    val pValTh = 0.05f
    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = false),
      expectedFdr = Some(0.60240966f)
    )
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    logger.info(" ResultSetValidator test FDR on IT Validation . Create service")
    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchValidator = Some(fdrValidator)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm

    Assert.assertNotNull(tRSM)
    Assert.assertNotNull(dRSM)
    Assert.assertTrue(dRSM.isDefined)
    Assert.assertTrue(tRSM.proteinSets.length > 0) //Test to see bug #7831 

    logger.debug(" Verify Result IN RSM ")
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    Assert.assertEquals(1047, allTarPepMatc.length + allDecPepMatc.length)
    logger.debug(" allTarPepMatc " + allTarPepMatc.length + " allDecPepMatc " + allDecPepMatc.length)

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter().filterDescription, fPrp.getDescription.get)

    val pepValResults = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get

    Assert.assertEquals(allTarPepMatc.length, pepValResults.getTargetMatchesCount)
    Assert.assertEquals(allDecPepMatc.length, pepValResults.getDecoyMatchesCount.get)
    logger.debug(" -------------------- FDR = " + pepValResults.getFdr)

    val nbrProtSet = tRSM.proteinSets.length
    val nbrSamsetPepSet = tRSM.peptideSets.count(!_.isSubset)
    Assert.assertEquals(nbrProtSet, nbrSamsetPepSet)
  }

  @Test
  def testOtherMascotPValueValidation() {

    val pValTh = 0.1f
    val pepFilters = Seq(new MascotPValuePSMFilter(pValue = pValTh, useHomologyThreshold = false))
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))

    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = Some(tdAnalyzerBuilder), pepMatchPreFilters = Some(pepFilters)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
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
    val allTarPepMatc = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatc = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)

    logger.debug("  - allTarPepMatc " + allTarPepMatc.length + " allDecPepMatc " + allDecPepMatc.length)
    Assert.assertEquals(996, allTarPepMatc.length + allDecPepMatc.length) //IRMa -> 1000! 

    tRSM.peptideInstances.foreach(pepInst => {
      pepInst.peptideMatches.foreach(peptideM => {
        Assert.assertTrue(peptideM.isValidated)
      })
    })

    Assert.assertTrue(tRSM.properties.isDefined)
    Assert.assertTrue(tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.isDefined)

    val pepFilterProps = tRSM.properties.get.getValidationProperties.get.getParams.getPsmFilters.get
    Assert.assertEquals(1, pepFilterProps.size)

    val fPrp: FilterDescriptor = pepFilterProps(0)
    val props = fPrp.getProperties.get
    Assert.assertEquals(1, props.size)
    Assert.assertEquals(new MascotPValuePSMFilter().filterDescription, fPrp.getDescription.get)
    Assert.assertEquals(props(FilterPropertyKeys.THRESHOLD_VALUE), pValTh)

    val pepValResultsOpt = tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults
    Assert.assertTrue(pepValResultsOpt.isDefined)
    val pepValResults = pepValResultsOpt.get
    Assert.assertEquals(993, pepValResults.getTargetMatchesCount) // IRMa 997
    Assert.assertEquals(3, pepValResults.getDecoyMatchesCount.get) //IRMa 3
    Assert.assertEquals(993, allTarPepMatc.length)
    Assert.assertEquals(3, allDecPepMatc.length)
    logger.debug(" -------------------- FDR = " + pepValResults.getFdr)
    val nbrProtSet = tRSM.proteinSets.length
    val nbrSamsetPepSet = tRSM.peptideSets.count(!_.isSubset)
    Assert.assertEquals(nbrProtSet, nbrSamsetPepSet)

  }

}

object ResultSetValidatorF027737Test extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = TLS_F027737_MTD_no_varmod
  override val useJPA: Boolean = false

  val targetRSId = 2L
  val decoyRSId = Some(1L)

}

class ResultSetValidatorF027737Test extends StrictLogging {
  
  val targetRS = ResultSetValidatorF027737Test.getRS(ResultSetValidatorF027737Test.targetRSId, ResultSetValidatorF027737Test.decoyRSId)
  ResultSetValidatorF027737Test.resetRSValidation(targetRS)
  val executionContext = ResultSetValidatorF027737Test.executionContext
  
  require( targetRS != null, "targetRS is null")
  require( executionContext != null, "executionContext is null")

  @Test
  def testSeparatedSearchValidationWithCompetition() {
    val rsValidator = _testSeparatedSearchValidation(expectedFdr = 5.0f, useTdCompetition = true)
    
    val tRSM = rsValidator.validatedTargetRsm
    val dRSM = rsValidator.validatedDecoyRsm
    
    logger.debug("Verify Result IN RS")
    val rsTarPepMatches = tRSM.resultSet.get.peptideMatches
    val rsDecPepMatches = dRSM.get.resultSet.get.peptideMatches
    Assert.assertEquals("RsTarPepMatches validated count", 18, rsTarPepMatches.count(_.isValidated))
    Assert.assertEquals("RsDecPepMatches validated count", 0, rsDecPepMatches.count(_.isValidated))

    logger.debug("Verify Result IN RSM")
    val allTarPepMatch = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatch = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(18, allTarPepMatch.length)
    Assert.assertEquals(0, allDecPepMatch.length)
    Assert.assertEquals(18,tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getTargetMatchesCount)
    Assert.assertEquals(0,dRSM.get.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getDecoyMatchesCount.get)
    
    //println(tRSM.peptideValidationRocCurve.get.rocPoints.toList)
 
  }
  
  @Test
  def testSeparatedSearchValidationWithoutCompetition() {
    val rsValidator = _testSeparatedSearchValidation(expectedFdr = 5.0f, useTdCompetition = false)
    
    val tRSM = rsValidator.validatedTargetRsm
    val dRSM = rsValidator.validatedDecoyRsm
    
    logger.debug("Verify Result IN RS")
    val rsTarPepMatches = tRSM.resultSet.get.peptideMatches
    val rsDecPepMatches = dRSM.get.resultSet.get.peptideMatches
    Assert.assertEquals("RsTarPepMatches validated count", 18, rsTarPepMatches.count(_.isValidated))
    Assert.assertEquals("RsDecPepMatches validated count", 0, rsDecPepMatches.count(_.isValidated))

    logger.debug("Verify Result IN RSM")
    val allTarPepMatch = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatch = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(18, allTarPepMatch.length)
    Assert.assertEquals(0, allDecPepMatch.length)
    Assert.assertEquals(18,tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getTargetMatchesCount)
    Assert.assertEquals(0,dRSM.get.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getDecoyMatchesCount.get)
  }
  
  def _testSeparatedSearchValidation(expectedFdr: Float, useTdCompetition: Boolean): ResultSetValidator = {
    
    val tdAnalyzerBuilder = new TDAnalyzerBuilder(
      analyzer = TargetDecoyAnalyzers.BASIC,
      estimator = if (useTdCompetition) { Some(TargetDecoyEstimators.GIGY_COMPUTER) } else { Some(TargetDecoyEstimators.KALL_STOREY_COMPUTER) }
    )
    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = new MascotAdjustedEValuePSMFilter(),
      expectedFdr = Some(expectedFdr)
    )

    logger.info("testSeparatedSearchValidation: Create ResultSetValidator service")
    val rsValidator =  ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(Seq(new PrettyRankPSMFilter(1))),
        pepMatchValidator = Some(fdrValidator)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )

    logger.debug("testSeparatedSearchValidation: RUN ResultSetValidator service")
    val result = rsValidator.runService
    Assert.assertTrue(result)
    logger.debug("testSeparatedSearchValidation: End Run ResultSetValidator Service with FDR filter using Rank and Mascot Adjusted E-Value, in Test ")

    rsValidator
  }
  
  @Test
  def testValidationWithoutPsms() {

    val tdAnalyzerBuilder = new TDAnalyzerBuilder(analyzer = TargetDecoyAnalyzers.BASIC, estimator = Some(TargetDecoyEstimators.GIGY_COMPUTER))
    val fdrValidator = new TDPepMatchValidatorWithFDROptimization(
      validationFilter = new MascotAdjustedEValuePSMFilter(),
      expectedFdr = Some(5.0f)
    )

    logger.info("testValidationWithoutPsms: Create ResultSetValidator service")
    val rsValidator = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(
        tdAnalyzerBuilder = Some(tdAnalyzerBuilder),
        pepMatchPreFilters = Some(Seq(new PrettyRankPSMFilter(0))),
        pepMatchValidator = Some(fdrValidator)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = false,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )
    
    val result = rsValidator.runService
    Assert.assertTrue(result)
    
    val tRSM = rsValidator.validatedTargetRsm
    val dRSM = rsValidator.validatedDecoyRsm
    
    logger.debug("Verify Result IN RS")
    val rsTarPepMatches = tRSM.resultSet.get.peptideMatches
    val rsDecPepMatches = dRSM.get.resultSet.get.peptideMatches
    Assert.assertEquals("RsTarPepMatches validated count", 0, rsTarPepMatches.count(_.isValidated))
    Assert.assertEquals("RsDecPepMatches validated count", 0, rsDecPepMatches.count(_.isValidated))

    logger.debug("Verify Result IN RSM")
    val allTarPepMatch = tRSM.peptideInstances.flatMap(pi => pi.peptideMatches)
    val allDecPepMatch = dRSM.get.peptideInstances.flatMap(pi => pi.peptideMatches)
    Assert.assertEquals(0, allTarPepMatch.length)
    Assert.assertEquals(0, allDecPepMatch.length)
    Assert.assertEquals(0,tRSM.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getTargetMatchesCount)
    Assert.assertEquals(0,dRSM.get.properties.get.getValidationProperties.get.getResults.getPsmResults.get.getDecoyMatchesCount.get)
  }
  

}
