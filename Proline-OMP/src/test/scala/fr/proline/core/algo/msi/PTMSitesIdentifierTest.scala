package fr.proline.core.algo.msi

import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.repository.DriverType
import fr.proline.core.dbunit.DbUnitSampleDataset
import fr.proline.core.dbunit.GRE_F068213_M2_4_TD_EColi
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.profi.util.serialization.ProfiJson

object PTMSitesIdentifierTest extends AbstractEmptyDatastoreTestCase with DbUnitResultFileLoading with StrictLogging {

  // Define some vars
  val driverType = DriverType.H2
  val dbUnitResultFile = GRE_F068213_M2_4_TD_EColi
  val targetRSId: Long = 2L
  val useJPA = true
  val decoyRSId = Option.empty[Long]
  
}

class PTMSitesIdentifierTest extends StrictLogging {

  val targetRS = PTMSitesIdentifierTest.getRS()

  @Test
  def testPTMSitesIdentifier() {

    val scoreTh = 22.0f
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    
    val rsValidation = new ResultSetValidator(
      execContext = PTMSitesIdentifierTest.executionContext,
      targetRs = targetRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED)),
      pepMatchPreFilters = Some(pepFilters),
      pepMatchValidator = None,
      protSetFilters = None,
      inferenceMethod = Some(InferenceMethod.PARSIMONIOUS), 
      storeResultSummary = true
    )

    rsValidation.runService
    val tRSM = rsValidation.validatedTargetRsm
    val ptmSites = new PTMSitesIdentifier().identifyPTMSites(tRSM,targetRS.proteinMatches)
    println("serialized" + ProfiJson.serialize(ptmSites))
    
//    Assert.assertTrue(!ptmSites.isEmpty)
//    Assert.assertEquals(398, ptmSites.size)
    
  }

  

}
