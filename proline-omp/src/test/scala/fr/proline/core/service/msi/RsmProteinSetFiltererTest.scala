package fr.proline.core.service.msi

import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.primitives.toInt
import fr.proline.core.algo.msi.InferenceMethod
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.filtering.proteinset.SpecificPeptidesPSFilter
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, ProtSetFilterParams}
import fr.proline.core.algo.msi.validation._
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.STR_F136482_CTD
import fr.proline.core.om.model.msi.FilterDescriptor
import fr.proline.repository.DriverType
import org.junit.{Assert, Test}

object RsmProtSetFiltererTest extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = STR_F136482_CTD
  override val useJPA: Boolean = true

  val targetRSId = 2L

}

class RsmProtSetFiltererTest extends StrictLogging {
  
  protected val DEBUG_TESTS = false
  val targetRS = RsmProtSetFiltererTest.getRS(RsmProtSetFiltererTest.targetRSId)

  // reset validation then artificially remove de associated decoyRS
  RsmProtSetFiltererTest.resetRSValidation(targetRS)
  targetRS.decoyResultSet = None

  val executionContext = RsmProtSetFiltererTest.executionContext

  @Test
  def testProtSpecificValidation() {
     val msiDBCn = executionContext.getMSIDbConnectionContext()
    try {
	
	 msiDBCn.beginTransaction()
	 val msiEM  = msiDBCn.getEntityManager()
    val scoreTh = 22.0f
    val nbrPepProteo = 1
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    val protProteoTypiqueFilters = Seq(new SpecificPeptidesPSFilter(nbrPepProteo))

    logger.info(" RSMProtSetFilterer : step1. validate RS using score")
    val rsValidation = ResultSetValidator(
      execContext = executionContext,
      targetRs = targetRS,
      validationConfig = ValidationConfig(tdAnalyzerBuilder = None, pepMatchPreFilters = Some(pepFilters)),
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS),
      storeResultSummary = true,
      propagatePepMatchValidation = false,
      propagateProtSetValidation = false
    )
	 
    val result = rsValidation.runService
    Assert.assertTrue(result)
    logger.debug(" End Run RSMProtSetFilterer step1 ")

    val tRSM = rsValidation.validatedTargetRsm
    val dRSM = rsValidation.validatedDecoyRsm
    
    msiEM.flush()
    //For test go through pepIns !
    tRSM.peptideInstances.foreach( pepI =>{
    	msiEM.find(classOf[fr.proline.core.orm.msi.PeptideInstance],pepI.id)  
    })
    
    Assert.assertNotNull(tRSM)
    Assert.assertTrue(dRSM.isEmpty)
    Assert.assertTrue(tRSM.properties.isDefined)

    var protFilterPropsOpt = tRSM.properties.get.getValidationProperties.get.getParams.getProteinFilters
    Assert.assertTrue(protFilterPropsOpt.isDefined)
    Assert.assertTrue("all targetRSM ProteinSet should validated ", tRSM.proteinSets.count(!_.isValidated) == 0)
//    Assert.assertTrue("all decoyRSM  ProteinSet should validated ", dRSM.get.proteinSets.count(!_.isValidated) == 0)


    logger.info(" RSMProtSetFilterer : step2. filter protein set ")
    val rsmProtSetFilerer = new RsmProteinSetFilterer(
  		execCtx = executionContext,
  		targetRsm = tRSM, 
  		protSetFilters = protProteoTypiqueFilters
  	)	
    val result2 = rsmProtSetFilerer.runService
    Assert.assertTrue(result2)
    logger.debug(" End Run RSMProtSetFilterer step2 ")
    	      
    //Commit transaction
    msiDBCn.commitTransaction()
 
    
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
    val removedDecProtSet2Count = 0//dRSM.get.proteinSets.count(!_.isValidated)

    val allTarProtSet = tRSM.proteinSets
    val allDecProtSet = Array.empty // dRSM.get.proteinSets
    logger.debug(" All Target ProtSet (even not validated) " + allTarProtSet.length + " <>  removed Target ProtSet " + removedTarProtSet2Count)
    logger.debug(" All Decoy ProtSet (even not validated)  " + allDecProtSet.length + " <>  removed Decoy ProtSet " + removedDecProtSet2Count)
    Assert.assertEquals("allTarProtSet length", 225, allTarProtSet.filter(_.isValidated).length)  
//    Assert.assertEquals("allDecProtSet length", 207, allDecProtSet.filter(_.isValidated).length)
    
    } finally {
      if (msiDBCn != null ){
        msiDBCn.close
        
      }       
   }
    
    
  }
  

 

}

