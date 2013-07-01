package fr.proline.core.service.msq

import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.utils.generator.ResultSetFakeBuilder
import scala.collection.mutable.ListBuffer
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.ProteinMatch
import org.junit.Before
import fr.proline.core.algo.msi.inference.CommunistProteinSetInferer
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.context.BasicExecutionContext
import org.junit.After
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider

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
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false).asInstanceOf[SQLConnectionContext]
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
    readRSM.resultSet = Some(readRS)
    assertEquals(rsm.id,rsmOp.get.id)
    var wsCalculator = new WeightedSCCalculator(execContext = executionContext, referenceRSM=rsmOp.get, rsmToCalculate=Seq(rsmOp.get))
    val serviceRes = wsCalculator.runService
    assertTrue(serviceRes)
//    logger.debug("  wsCalculator RESULT  "+wsCalculator.getResultAsJSON)
   
  }

  

}

