package fr.proline.core.service.uds

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.context.IExecutionContext
import fr.proline.repository.DriverType
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.dal.ContextFactory
import fr.proline.context.BasicExecutionContext

@Test
class AuthenticationTest extends DatabaseTestCase with Logging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "STR_F136482_CTD"
   
  override def getProlineDatabaseType() = ProlineDatabaseType.UDS


  var udsDbCtx: DatabaseConnectionContext = null

  @Before
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing UDS db")
    initDatabase()
    loadDataSet("/dbunit_samples/" + fileName + "/uds-db.xml")
   
    logger.info("UDS db succesfully initialized !")
    buildUDSConnectionContext()
  }

  @After
  override def tearDown() = {
    super.tearDown();
  }
  
  def buildUDSConnectionContext() = {
    udsDbCtx = ContextFactory.buildDbConnectionContext(getConnector, false)
    
  }

  @Test
  def authenticateUser() = {
	  val service = new UserAuthenticator(udsConnectionCtxt = udsDbCtx,name = "proline_user", hashPassword = "993bd037c1ae6b87ea5adb936e71fca01a3b5c0505855444afdfa2f86405db1d" )
      val result = service.runService()
      
      assertTrue(result)
  }

}

