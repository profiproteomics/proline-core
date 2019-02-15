package fr.proline.core.dal

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context._
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.{PeptideCacheExecutionContext, ProviderDecoratedExecutionContext}
import fr.proline.core.om.provider.msi.{IPTMProvider, IPeptideProvider, IResultSetProvider, IResultSummaryProvider}
import fr.proline.core.om.provider.msi.impl._
import fr.proline.repository.DriverType
import org.junit.{AfterClass, BeforeClass}

import scala.collection.mutable

abstract class AbstractDatastoreTestCase extends AbstractMultipleDBTestCase with StrictLogging {


  // Define the interface to be implemented
  val driverType: DriverType
  val useJPA: Boolean
  val dbUnitResultFile: DbUnitResultFileLocation = Init_Dataset

  var executionContext: ProviderDecoratedExecutionContext = null
  val resultSetById = mutable.HashMap[Long,ResultSet]()

  @BeforeClass
  @throws(classOf[Exception])
  def setUp() {

    logger.info("Initializing DBs")
    initDBsDBManagement(driverType)

    //Load Data
    msiDBTestCase.loadDataSet(dbUnitResultFile.msiDbDatasetPath)
    udsDBTestCase.loadDataSet(dbUnitResultFile.udsDbDatasetPath)

    logger.info("MSI and UDS dbs successfully initialized !")
    
    executionContext = if( useJPA ) buildJPAContext() else buildSQLContext()
  }
  
  @AfterClass
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def getRS(targetRSId: Long, decoyRSId: Option[Long] = None): ResultSet = {
    if (!resultSetById.contains(targetRSId)) {
      val rsProvider = getResultSetProvider()
      val rs = rsProvider.getResultSet(targetRSId).get
      // SMALL HACK because SQLResultSetProvider implementation did not read decoyRS. It must be done manually
      if (decoyRSId.isDefined && !useJPA) rs.decoyResultSet = rsProvider.getResultSet(decoyRSId.get)
      resultSetById += (targetRSId -> rs)
    }
    resultSetById(targetRSId)
  }

  def resetRSValidation(rs: ResultSet) = {
    rs.peptideMatches.foreach(_.isValidated = true)
    if (rs.decoyResultSet.isDefined) rs.decoyResultSet.get.peptideMatches.foreach(_.isValidated = true)
  }

  def getResultSummaryProvider() : IResultSummaryProvider = {
    executionContext.getProvider(classOf[IResultSummaryProvider])
  }

  def getResultSetProvider() : IResultSetProvider = {
    executionContext.getProvider(classOf[IResultSetProvider])
  }

  def buildJPAContext() = {

    val execCtx = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    val decoratedContext = ProviderDecoratedExecutionContext(execCtx)
    decoratedContext.putProvider(classOf[IResultSummaryProvider],  new SQLResultSummaryProvider(PeptideCacheExecutionContext(execCtx)))
    decoratedContext.putProvider(classOf[IResultSetProvider], new ORMResultSetProvider(execCtx.getMSIDbConnectionContext))

    decoratedContext
  }
  
  def buildSQLContext() = {
    val udsDbCtx = BuildUdsDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val msiDbCtx = BuildMsiDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val executionContext = PeptideCacheExecutionContext( new BasicExecutionContext(1,udsDbCtx, msiDbCtx, null))

    val decoratedContext = ProviderDecoratedExecutionContext(executionContext)

    decoratedContext.putProvider(classOf[IPeptideProvider], new SQLPeptideProvider(executionContext))
    decoratedContext.putProvider(classOf[IPTMProvider], new SQLPTMProvider(msiDbCtx))
    decoratedContext.putProvider(classOf[IResultSummaryProvider],  new SQLResultSummaryProvider(executionContext))
    decoratedContext.putProvider(classOf[IResultSetProvider], new SQLResultSetProvider(executionContext))

    decoratedContext
  }

}
