package fr.proline.core.algo.msi

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.dal._
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi.{PeptideMatch, PtmDataSet, ResultSummary}
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider
import fr.proline.core.service.msi.RsmPtmSitesIdentifierV2
import fr.proline.repository.DriverType
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.BeforeClass

object PtmSitesIdentifierTest extends AbstractDatastoreTestCase {

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
class PtmSitesIdentifierTest extends StrictLogging {

  val sqlExecutionContext = PtmSitesIdentifierTest.executionContext

  @Test
  def testPTMSitesIdentifier() {
    val ptmSites = new PtmSitesIdentifier(PtmSitesIdentifierTest.rsm,PtmSitesIdentifierTest.getRS(PtmSitesIdentifierTest.targetRSId).proteinMatches).identifyPtmSites()
    assertNotNull(ProfiJson.serialize(ptmSites))
  }


  @Test
  def testPTMSitesClustering(): Unit = {
    var ptmIds = Array(27L)
    var ptmDefinitionById = new SQLPTMProvider(sqlExecutionContext.getMSIDbConnectionContext).ptmDefinitionById

    val ptmSites = new PtmSitesIdentifier(PtmSitesIdentifierTest.rsm,PtmSitesIdentifierTest.getRS(PtmSitesIdentifierTest.targetRSId).proteinMatches).identifyPtmSites()
    val ptmSites2 = RsmPtmSitesIdentifierV2.toPtmSites2(ptmSites)

    val sitesByProteinMatchIds = ptmSites2.filter{ s => ptmIds.contains(ptmDefinitionById(s.ptmDefinitionId).ptmId) }.groupBy(_.proteinMatchId)

    val clusterizer = new PtmSiteExactClusterer(PtmSitesIdentifierTest.rsm,PtmSitesIdentifierTest.getRS(PtmSitesIdentifierTest.targetRSId).proteinMatches)

    val clusters = sitesByProteinMatchIds.flatMap{ case(protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds, IdGenerator) }

    val ptmDataSet = new PtmDataSet(
      ptmIds = ptmIds,
      leafResultSummaryIds = Array(PtmSitesIdentifierTest.rsm.id),
      ptmSites = ptmSites2.toArray,
      ptmClusters = clusters.toArray)
    val str = ProfiJson.serialize(ptmDataSet)
    str.length
  }

  def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
    val peptideMatches = PtmSitesIdentifierTest.rsm.peptideInstances.filter{ pi => peptideIds.contains(pi.peptide.id) }.flatMap(_.peptideMatches)
    peptideMatches.map( pm => (pm.id -> pm)).toMap
  }

}


object IdGenerator extends InMemoryIdGen {
  private val inMemoryIdSequence = new AtomicLong(0)
  override def generateNewId(): Long = { inMemoryIdSequence.incrementAndGet() }
}