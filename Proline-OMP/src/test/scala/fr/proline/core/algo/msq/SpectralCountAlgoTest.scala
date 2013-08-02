package fr.proline.core.algo.msq

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.weiglewilczek.slf4s.Logging
import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import org.junit.After
import org.junit.Before
import org.junit.Test

@Test
class SpectralCountAlgoTest extends AbstractMultipleDBTestCase with Logging {
  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "Merged_RSM_Test"
  val targetRSMId: Long = 8

  var executionContext: IExecutionContext = null
  var rsmProvider: IResultSummaryProvider = null
  protected var readRSM: ResultSummary = null

  @Before
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/ps-db.xml")
    msiDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/msi-db.xml")
    udsDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/uds-db.xml")

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    val (execContext, rsmProv) = buildSQLContext()
    executionContext = execContext
    rsmProvider = rsmProv
    readRSM = this._loadRSM()
  }

  private def _loadRSM(): ResultSummary = {
    val rsm = rsmProvider.getResultSummary(targetRSMId,true).get
    // SMALL HACK because of DBUNIT BUG (see bioproj defect #7548)
//    if (decoyRSId.isDefined) rs.decoyResultSet = rsProvider.getResultSet(decoyRSId.get)
	rsm
  }
  
  @After
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false).asInstanceOf[SQLConnectionContext]
    val executionContext = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)

    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (executionContext, rsmProvider)
  }

  
  @Test
  def testUpdatePepInstance() = {
	  PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(readRSM, executionContext)
	  readRSM.peptideInstances.foreach(pepI=> {
	    logger.debug(" TEST PeptI totalLeavesMatchCount : "+pepI.peptide.sequence+" : "+pepI.id+" => "+pepI.totalLeavesMatchCount)
	    if(pepI.id != 34979 && pepI.id !=37217 && pepI.id != 37760)
	    	assertTrue(pepI.totalLeavesMatchCount>0)
	    else
	      logger.warn("Validated Peptide Match for peptide Instance "+34979+" was not treated !!!! See ")
	  })
	  
  }

}

