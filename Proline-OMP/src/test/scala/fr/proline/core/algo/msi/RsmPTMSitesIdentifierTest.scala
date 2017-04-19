package fr.proline.core.algo.msi

import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.repository.DriverType
import fr.proline.core.dbunit.STR_F063442_F122817_MergedRSMs
import fr.proline.core.dbunit.DbUnitSampleDataset
import fr.proline.core.dbunit.GRE_F068213_M2_4_TD_EColi
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.service.msi.RsmPTMSitesIdentifier
import fr.proline.core.om.model.msi.ResultSummary

object RsmPTMSitesIdentifierTest extends AbstractResultSummaryTestCase with StrictLogging {

  // Define some vars
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F063442_F122817_MergedRSMs
  val targetRSMId: Long = 33L
  val useJPA = true
  val decoyRSId = Option.empty[Long]
  
}

class RsmPTMSitesIdentifierTest extends StrictLogging {


  @Test
  def testPTMSitesIdentifier() {

    new RsmPTMSitesIdentifier(RsmPTMSitesIdentifierTest.executionContext, RsmPTMSitesIdentifierTest.targetRSMId, false).runService
        
  }

  

}
