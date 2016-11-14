package fr.proline.core.dal

import org.junit.AfterClass
import org.junit.BeforeClass

import com.typesafe.scalalogging.StrictLogging

import fr.proline.context._
import fr.proline.core.dbunit._
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.repository.DriverType

abstract class AbstractEmptyDatastoreTestCase extends AbstractMultipleDBTestCase with StrictLogging {

  // Define the interface to be implemented
  val driverType: DriverType
  val useJPA: Boolean
  
  var executionContext: IExecutionContext = null
  
  @BeforeClass
  @throws(classOf[Exception])
  def setUp() {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    psDBTestCase.loadDataSet(DbUnitInitDataset.PDI_DB.getResourcePath())
    psDBTestCase.loadDataSet(DbUnitInitDataset.PS_DB.getResourcePath())
    msiDBTestCase.loadDataSet(DbUnitInitDataset.MSI_DB.getResourcePath())
    udsDBTestCase.loadDataSet(DbUnitInitDataset.UDS_DB.getResourcePath())

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")
    
    executionContext = if( useJPA ) buildJPAContext() else buildSQLContext()
  }
  
  @AfterClass
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def buildJPAContext() = {
    BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
  }
  
  def buildSQLContext() = {
    val udsDbCtx = BuildUdsDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val pdiDbCtx = BuildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = BuildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, true)
    val msiDbCtx = BuildMsiDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val executionContext = new BasicExecutionContext(
      udsDbCtx,
      pdiDbCtx,
      psDbCtx,
      msiDbCtx,
      null
    )
    val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

    parserContext.putProvider(classOf[IPeptideProvider], new SQLPeptideProvider(psDbCtx))
    parserContext.putProvider(classOf[IPTMProvider], new SQLPTMProvider(psDbCtx))

    parserContext
  }
  
   def loadDbUnitResultFiles( datasetLocation: DbUnitResultFileLocation ) = {
    
    val classLoader = classOf[fr.proline.repository.util.DatabaseTestCase]
    
    // Open streams
    val msiStream = classLoader.getResourceAsStream( datasetLocation.msiDbDatasetPath )
    val udsStream = classLoader.getResourceAsStream( datasetLocation.udsDbDatasetPath )
    val psStream = classLoader.getResourceAsStream( datasetLocation.psDbDatasetPath )
    
    psDBTestCase.loadDataSet(psStream)
    udsDBTestCase.loadDataSet(udsStream)
    msiDBTestCase.loadDataSet(msiStream)
    
    msiStream.close()
    udsStream.close()
    psStream.close()
    
  }

}
