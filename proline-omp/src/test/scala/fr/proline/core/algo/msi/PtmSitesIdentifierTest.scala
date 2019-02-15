package fr.proline.core.algo.msi

import org.junit.Assert.assertNotNull
import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.dal._
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.repository.DriverType
import org.junit.{BeforeClass, Test}

object PTMSitesIdentifierTest extends AbstractDatastoreTestCase {

  override val driverType: DriverType = DriverType.H2
  override val useJPA: Boolean = true
  override val dbUnitResultFile: DbUnitResultFileLocation = STR_F122817_Mascot_v2_3

  val targetRSId = 1L


  lazy val proteinSetInferer = new ParsimoniousProteinSetInferer()
  var rsm: ResultSummary = null

  @BeforeClass
  override def setUp() {
    super.setUp()
    rsm = proteinSetInferer.computeResultSummary( resultSet = getRS(targetRSId) )
  }
}

class PTMSitesIdentifierTest extends StrictLogging {

  val sqlExecutionContext = PTMSitesIdentifierTest.executionContext

  @Test
  def testPTMSitesIdentifier() {
    val ptmSites = new PtmSitesIdentifier().identifyPtmSites(PTMSitesIdentifierTest.rsm,PTMSitesIdentifierTest.getRS(PTMSitesIdentifierTest.targetRSId) .proteinMatches)
    assertNotNull(ProfiJson.serialize(ptmSites))
  }

  

}
