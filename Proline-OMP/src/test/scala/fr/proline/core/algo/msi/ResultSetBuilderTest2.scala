package fr.proline.core.algo.msi

import org.junit.Assert._
import fr.proline.core.om.provider.msi.IResultSetProvider
import org.junit.BeforeClass
import org.junit.AfterClass
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.repository.DriverType
import fr.proline.context.BasicExecutionContext
import fr.proline.core.om.model.msi.ResultSet
import com.weiglewilczek.slf4s.Logging
import org.junit.Test
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import org.junit.After
import org.junit.Before
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext


@Test
class ResultSetBuilderTest2 extends AbstractMultipleDBTestCase with Logging {
  
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

    val (execContext, rsProv) = buildSQLContext() //SQLContext()
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
    

  	@Test
	def addOneRS() = {
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = -99)
	  rsAddAlgo.addResultSet(readRS)
	  val rs2 = rsAddAlgo.toResultSet()
	  assert(rs2 != null)
	  assert(readRS != rs2)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.getPeptideId).distinct
	  assertEquals(peptides.length, readRS.peptides.length)
	  assertEquals(peptides.length, readRS.peptideMatches.map(_.peptide.id).distinct.length)
	  assertEquals(rs2.proteinMatches.map(_.sequenceMatches).length, readRS.proteinMatches.map(_.sequenceMatches).length)
	  val ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(-99, ids(0))
	  
	  val storerContext = StorerContext(executionContext) // Use Object factory
	  val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
     val rsId = rsStorer.storeResultSet(rs2, storerContext)
  }
  
  	@Test
	def addOneRSTwice() = {
	  val rsAddAlgo = new ResultSetBuilder(resultSetId = -99)
	  rsAddAlgo.addResultSet(readRS)
	  rsAddAlgo.addResultSet(readRS)
	  val rs2 = rsAddAlgo.toResultSet()
	  assert(rs2 != null)
	  assert(readRS != rs2)
	  val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.getPeptideId).distinct
	  assertEquals(peptides.length, readRS.peptides.length)
	  assertEquals(peptides.length, readRS.peptideMatches.map(_.peptide.id).distinct.length)
	  assertEquals(rs2.proteinMatches.map(_.sequenceMatches).length, readRS.proteinMatches.map(_.sequenceMatches).length)
	  val ids = rs2.peptideMatches.map(_.resultSetId).distinct
	  assertEquals(1, ids.length)
	  assertEquals(-99, ids(0))
	  
	  val storerContext = StorerContext(executionContext) // Use Object factory
	  val rsStorer = RsStorer(storerContext.getMSIDbConnectionContext)
     val rsId = rsStorer.storeResultSet(rs2, storerContext)
  }
  
  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false).asInstanceOf[SQLConnectionContext]
    val executionContext = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)
    val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

    parserContext.putProvider(classOf[IPeptideProvider], new SQLPeptideProvider(psDbCtx))
    parserContext.putProvider(classOf[IPTMProvider], new SQLPTMProvider(psDbCtx))

    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (parserContext, rsProvider)
  }


}

