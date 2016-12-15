package fr.proline.core.dal

import com.typesafe.scalalogging.StrictLogging

import org.junit.AfterClass
import org.junit.BeforeClass

import fr.proline.context._
import fr.proline.core.dal._
import fr.proline.core.dbunit._
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

// TODO: move file from OMP
abstract class AbstractResultSetTestCase extends AbstractMultipleDBTestCase with StrictLogging {

  // Define the interface to be implemented
  val driverType: DriverType
  val dbUnitResultFile: DbUnitResultFileLocation
  val targetRSId: Long
  val decoyRSId: Option[Long]
  
  var executionContext: IExecutionContext = null
  var rsProvider: IResultSetProvider = null
  protected var readRS: ResultSet = null
  
  private def _loadRS(): ResultSet = {
    val rs = rsProvider.getResultSet(targetRSId).get
    // SMALL HACK because of DBUNIT BUG (see bioproj defect #7548)
    if (decoyRSId.isDefined) rs.decoyResultSet = rsProvider.getResultSet(decoyRSId.get)
    rs
  }
  
  def getRS(): ResultSet = {
    this.readRS
  }

  @BeforeClass
  @throws(classOf[Exception])
  def setUp() {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet(DbUnitSampleDataset.PROTEINS.getResourcePath)
    psDBTestCase.loadDataSet(dbUnitResultFile.psDbDatasetPath)
    msiDBTestCase.loadDataSet(dbUnitResultFile.msiDbDatasetPath)
    udsDBTestCase.loadDataSet(dbUnitResultFile.udsDbDatasetPath)

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    val (execContext, rsProv) = buildSQLContext() //SQLContext()
    executionContext = execContext
    rsProvider = rsProv
    readRS = this._loadRS()
  }
  
  @AfterClass
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def buildJPAContext() = {
    val executionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    val rsProvider = new ORMResultSetProvider(executionContext.getMSIDbConnectionContext, executionContext.getPSDbConnectionContext, executionContext.getPDIDbConnectionContext)

    (executionContext, rsProvider)
  }
  
  def buildSQLContext() = {
    val udsDbCtx = BuildUdsDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val pdiDbCtx = BuildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = BuildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false)
    val msiDbCtx = BuildMsiDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val executionContext = new BasicExecutionContext(udsDbCtx,pdiDbCtx,psDbCtx,msiDbCtx,null)
    val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

    parserContext.putProvider(classOf[IPeptideProvider], new SQLPeptideProvider(psDbCtx))
    parserContext.putProvider(classOf[IPTMProvider], new SQLPTMProvider(psDbCtx))

    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (parserContext, rsProvider)
  }
  
}