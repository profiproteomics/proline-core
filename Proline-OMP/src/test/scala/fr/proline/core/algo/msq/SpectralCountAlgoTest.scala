package fr.proline.core.algo.msq

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.STR_F063442_F122817_MergedRSMs
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.repository.DriverType

object SpectralCountAlgoTest extends AbstractDatastoreTestCase with StrictLogging {

  // Define some needed values
  override val driverType = DriverType.H2
  override val dbUnitResultFile = STR_F063442_F122817_MergedRSMs
  override val useJPA = true

  val targetRSMId: Long = 33L

  protected var readRSM: ResultSummary = null

  @BeforeClass
  @throws(classOf[Exception])
  override def setUp() = {
    super.setUp()
    readRSM = this._loadRSM()
  }

  private def _loadRSM(): ResultSummary = {
    val rsm = getResultSummaryProvider().getResultSummary(targetRSMId, true).get
    rsm
  }

  def getRSM() = readRSM

}

class SpectralCountAlgoTest extends StrictLogging {

  val executionContext = SpectralCountAlgoTest.executionContext
  val readRSM = SpectralCountAlgoTest.getRSM()

  @Test
  def testUpdatePepInstance() = {
    val pepInstanceFilteringLeafSCUpdater= new PepInstanceFilteringLeafSCUpdater()
    pepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(readRSM, executionContext)
    readRSM.peptideInstances.foreach(pepI => {
      assertTrue(pepI.totalLeavesMatchCount > 0)
    })

  }

}

