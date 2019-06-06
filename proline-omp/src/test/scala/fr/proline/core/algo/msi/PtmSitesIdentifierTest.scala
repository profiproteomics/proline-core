package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.dal._
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi.{PeptideMatch, ResultSummary}
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider
import fr.proline.core.service.msi.RsmPtmSitesIdentifierV2
import fr.proline.repository.DriverType
import org.junit.Assert.assertNotNull
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

@Test
class PTMSitesIdentifierTest extends StrictLogging {

  val sqlExecutionContext = PTMSitesIdentifierTest.executionContext

  @Test
  def testPTMSitesIdentifier() {
    val ptmSites = new PtmSitesIdentifier(PTMSitesIdentifierTest.rsm,PTMSitesIdentifierTest.getRS(PTMSitesIdentifierTest.targetRSId).proteinMatches).identifyPtmSites()
    assertNotNull(ProfiJson.serialize(ptmSites))
  }


  @Test
  def testPTMSitesClustering(): Unit = {
    var ptmIds = Array(27L)
    var ptmDefinitionById = new SQLPTMProvider(sqlExecutionContext.getMSIDbConnectionContext).ptmDefinitionById

    val ptmSites = new PtmSitesIdentifier(PTMSitesIdentifierTest.rsm,PTMSitesIdentifierTest.getRS(PTMSitesIdentifierTest.targetRSId).proteinMatches).identifyPtmSites()
    val ptmSites2 = RsmPtmSitesIdentifierV2.toPtmSites2(ptmSites)

    val sitesByProteinMatchIds = ptmSites2.filter{ s => ptmIds.contains(ptmDefinitionById(s.ptmDefinitionId).ptmId) }.groupBy(_.proteinMatchId)

    val clusterizer = new PtmSiteClusterer(PTMSitesIdentifierTest.rsm,PTMSitesIdentifierTest.getRS(PTMSitesIdentifierTest.targetRSId).proteinMatches)

    sitesByProteinMatchIds.foreach{ case(protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds) }

  }

  def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
    val peptideMatches = PTMSitesIdentifierTest.rsm.peptideInstances.filter{ pi => peptideIds.contains(pi.peptide.id) }.flatMap(_.peptideMatches)
    peptideMatches.map( pm => (pm.id -> pm)).toMap
  }
}
