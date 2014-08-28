package fr.proline.core.algo.msq

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.algo.msi.AbstractResultSummaryTestCase
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dbunit.STR_F063442_F122817_MergedRSMs
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.repository.DriverType

object SpectralCountAlgoTest extends AbstractResultSummaryTestCase with Logging {

  // Define some needed values
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F063442_F122817_MergedRSMs
  val targetRSMId: Long = 33L
  val useJPA = true

  protected var readRSM: ResultSummary = null

  @BeforeClass
  @throws(classOf[Exception])
  override def setUp() = {
    super.setUp()

    readRSM = this._loadRSM()
  }

  private def _loadRSM(): ResultSummary = {
    val rsm = rsmProvider.getResultSummary(targetRSMId, true).get
    // SMALL HACK because of DBUNIT BUG (see bioproj defect #7548)
    //    if (decoyRSId.isDefined) rs.decoyResultSet = rsProvider.getResultSet(decoyRSId.get)
    rsm
  }

  def getRSM() = readRSM

}

class SpectralCountAlgoTest extends Logging {

  val executionContext = SpectralCountAlgoTest.executionContext
  val readRSM = SpectralCountAlgoTest.getRSM()

  @Test
  def testUpdatePepInstance() = {
    PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(readRSM, executionContext)
    readRSM.peptideInstances.foreach(pepI => {
      assertTrue(pepI.totalLeavesMatchCount > 0)
    })

  }

}

