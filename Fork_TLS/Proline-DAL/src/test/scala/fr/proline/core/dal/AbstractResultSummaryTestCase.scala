package fr.proline.core.dal

import com.typesafe.scalalogging.StrictLogging

import org.junit.AfterClass
import org.junit.BeforeClass

import fr.proline.context._
import fr.proline.core.dal._
import fr.proline.core.dbunit._
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.repository.DriverType

// TODO: move file from OMP
abstract class AbstractResultSummaryTestCase extends AbstractMultipleDBTestCase with StrictLogging {

  // Define the interface to be implemented
  val driverType: DriverType
  val dbUnitResultFile: DbUnitResultFileLocation
  val targetRSMId: Long
  val useJPA: Boolean
  
  var executionContext: IExecutionContext = null
  var rsmProvider: IResultSummaryProvider = null

  @BeforeClass
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet(DbUnitSampleDataset.PROTEINS.getResourcePath)
    psDBTestCase.loadDataSet(dbUnitResultFile.psDbDatasetPath)
    msiDBTestCase.loadDataSet(dbUnitResultFile.msiDbDatasetPath)
    udsDBTestCase.loadDataSet(dbUnitResultFile.udsDbDatasetPath)    
    
    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")
    
    val ctxAndProvider = if( useJPA ) buildJPAContext()
    else buildSQLContext()
     
    executionContext = ctxAndProvider._1
    rsmProvider = ctxAndProvider._2
  }

  @AfterClass
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def buildJPAContext() = {
    val execCtx = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    
    val rsmProvider = new SQLResultSummaryProvider(
      execCtx.getMSIDbConnectionContext(),
      execCtx.getPSDbConnectionContext(),
      execCtx.getUDSDbConnectionContext()
    )

    (execCtx, rsmProvider)
  }
  
  def buildSQLContext() = {
    val udsDbCtx = BuildUdsDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val pdiDbCtx = BuildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = BuildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false)
    val msiDbCtx = BuildMsiDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val execCtx = new BasicExecutionContext(udsDbCtx,pdiDbCtx,psDbCtx,msiDbCtx,null)

    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (execCtx, rsmProvider)
  }
  
}
