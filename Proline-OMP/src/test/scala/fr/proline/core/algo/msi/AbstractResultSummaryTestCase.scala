package fr.proline.core.algo.msi

import org.junit.AfterClass
import org.junit.BeforeClass
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.dal.ContextFactory
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
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msi.IResultSummaryProvider

abstract class AbstractResultSummaryTestCase extends AbstractMultipleDBTestCase with Logging {

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
    val execCtx = ContextFactory.buildExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA
    
    val rsmProvider = new SQLResultSummaryProvider(
      execCtx.getMSIDbConnectionContext(),
      execCtx.getPSDbConnectionContext(),
      execCtx.getUDSDbConnectionContext()
    )

    (execCtx, rsmProvider)
  }
  
  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false)
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false)
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false)
    val execCtx = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)

    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (execCtx, rsmProvider)
  }
  
}