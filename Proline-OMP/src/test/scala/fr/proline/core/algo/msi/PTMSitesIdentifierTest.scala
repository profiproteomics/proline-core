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
import fr.proline.core.dbunit.DbUnitResultFileUtils

object PTMSitesIdentifierTest extends AbstractEmptyDatastoreTestCase  with StrictLogging {

  // Define some vars
  val driverType = DriverType.H2
  val targetRSId: Long = 2L
  val useJPA = false
  val decoyRSId = Option.empty[Long]
  
}

class PTMSitesIdentifierTest extends StrictLogging {

  val sqlExecutionContext = PTMSitesIdentifierTest.executionContext

  @Test
  def testPTMSitesIdentifier() {

    val scoreTh = 20.0f
    val pepFilters = Seq(new ScorePSMFilter(scoreThreshold = scoreTh))
    
    val targetRS = DbUnitResultFileUtils.importDbUnitResultFile(GRE_F068213_M2_4_TD_EColi, sqlExecutionContext) 
    
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
