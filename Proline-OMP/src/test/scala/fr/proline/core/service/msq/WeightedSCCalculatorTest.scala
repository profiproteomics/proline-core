package fr.proline.core.service.msq

import org.junit.After
import org.junit.Assert._
import org.junit.Before
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.repository.DriverType

@Test
class WeightedSCCalculatorTest extends AbstractMultipleDBTestCase with Logging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "GRE_F068213_M2.4_TD_EColi"
  val targetRSId : Long = 2
  val decoyRSId = Option.empty[Long]

  var executionContext: IExecutionContext = null  
  var rsProvider: IResultSetProvider = null
  protected var readRS: ResultSet = null
  
  @Before
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit_samples/"+fileName+"/ps-db.xml")
    msiDBTestCase.loadDataSet("/dbunit_samples/"+fileName+"/msi-db.xml")
    udsDBTestCase.loadDataSet("/dbunit_samples/"+fileName+"/uds-db.xml")

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    val (execContext, rsProv) = buildSQLContext()
    executionContext = execContext
    rsProvider = rsProv
    readRS = this._loadRS()
  }
  
  private def _loadRS(): ResultSet = {
    val rs = rsProvider.getResultSet(targetRSId).get    
    // SMALL HACK because of DBUNIT BUG (see bioproj defect #7548)
    if (decoyRSId.isDefined) rs.decoyResultSet = rsProvider.getResultSet(decoyRSId.get)
    rs
  }
  
  @After
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }
  
  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false)
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val executionContext = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)

    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (executionContext, rsProvider)
  }

     
  /**
   * P1 = (pep1, pep2, pep3, pep4, pep5, pep6,)
   * P2 = (pep5, pep7, pep8, pep9, pep10)
   */
  @Test
  def oneRSMSimpleSC() = {
	  //  Validate RS to generate RSM
    
    var validator = new ResultSetValidator(execContext = executionContext, targetRs= readRS ) 
    validator.runService
    val rsm = validator.validatedTargetRsm
    
    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = executionContext.getMSIDbConnectionContext(), psDbCtx = executionContext.getPSDbConnectionContext(), udsDbCtx = executionContext.getUDSDbConnectionContext())
    
    val rsmOp = rsmProvider.getResultSummary(rsm.id,true)
    assertNotNull(rsmOp)
    assertTrue(rsmOp.isDefined)
    val readRSM = rsmOp.get
//    readRSM.resultSet = Some(readRS)
    assertEquals(rsm.id,rsmOp.get.id)
    var wsCalculator = new WeightedSCCalculator(execContext = executionContext, referenceRSM=rsmOp.get, rsmToCalculate=Seq(rsmOp.get))
    val serviceRes = wsCalculator.runService
    assertTrue(serviceRes)
//    logger.debug("  wsCalculator RESULT  "+wsCalculator.getResultAsJSON)
   
  }

  
     
  /**
   * P1 = (pep1, pep2, pep3, pep4, pep5, pep6,)
   * P2 = (pep5, pep7, pep8, pep9, pep10)
   */
  @Test
  def oneRSMIdSimpleSC() = {
	  //  Validate RS to generate RSM
    
    var validator = new ResultSetValidator(execContext = executionContext, targetRs= readRS ) 
    validator.runService
    val rsm = validator.validatedTargetRsm
    
    var wsCalculator = new WeightedSCCalculatorWId(execContext = executionContext, referenceRSMId=rsm.id, rsmIdsToCalculate=Seq(rsm.id))
    val serviceRes = wsCalculator.runService
    assertTrue(serviceRes)
//    logger.debug("  wsCalculator RESULT  "+wsCalculator.getResultAsJSON)
   
  }

}

