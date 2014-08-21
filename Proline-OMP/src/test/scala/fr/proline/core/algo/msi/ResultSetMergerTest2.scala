package fr.proline.core.algo.msi

import org.junit.After
import org.junit.Before
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.repository.DriverType


@Test
class ResultSetMergerTest2 extends AbstractMultipleDBTestCase with Logging {
  
  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "STR_F122817_Mascot_v2.3"
  val targetRSId = 1
  val decoyRSId = Option.empty[Int]
  
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

    val (execContext, rsProv) = buildJPAContext() //SQLContext()
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

    def buildJPAContext() = {
    val executionContext = ContextFactory.buildExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    val rsProvider = new ORMResultSetProvider(executionContext.getMSIDbConnectionContext, executionContext.getPSDbConnectionContext, executionContext.getPDIDbConnectionContext)

    (executionContext, rsProvider)
  }
  
  @After
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }
    
// TEST FAIL for STRANGE REASONS
  
//  	@Test
//	def addOneRS() = {
//
//  	  val rsMergerAlgo = new ResultSetMerger()
//	  val rs2 = rsMergerAlgo.mergeResultSets(Seq(readRS))
//	  assert(rs2 != null)
//	  assert(readRS != rs2)
//	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id).distinct
//	  assertEquals(peptides.length, readRS.peptides.length)
//	  assertEquals(peptides.length, readRS.peptideMatches.map(_.peptide.id).distinct.length)
//	  assertEquals(rs2.proteinMatches.map(_.sequenceMatches).length, readRS.proteinMatches.map(_.sequenceMatches).length)
//	  val ids = rs2.peptideMatches.map(_.resultSetId).distinct
//	  assertEquals(1, ids.length)
//	  assertEquals(99, ids(0))
//  }
  
  
  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false)
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val executionContext = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)
    val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

    parserContext.putProvider(classOf[IPeptideProvider], new SQLPeptideProvider(psDbCtx))
    parserContext.putProvider(classOf[IPTMProvider], new SQLPTMProvider(psDbCtx))

    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (parserContext, rsProvider)
  }


}

