package fr.proline.core.service.uds

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.Settings
import fr.proline.core.dal._
import fr.proline.core.dal.context._
import fr.proline.core.dbunit.Init_Dataset
import fr.proline.core.orm.uds.UserAccount
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase

class AuthenticationTest extends DatabaseTestCase with StrictLogging {

  val udsUser = new UserAccount()
  udsUser.setLogin("proline_user")
  udsUser.setPasswordHash("993bd037c1ae6b87ea5adb936e71fca01a3b5c0505855444afdfa2f86405db1d")
  udsUser.setCreationMode("MANUAL")
  
  override def getProlineDatabaseType() = ProlineDatabaseType.UDS

  var udsDbCtx: DatabaseConnectionContext = null

  @Before
  @throws(classOf[Exception])
  def setUp() {

    logger.info("Initializing UDS db")
    initDatabase()
    loadDataSet(Init_Dataset.udsDbDatasetPath)
    
    logger.info("UDS db successfully initialized !")
    buildUDSConnectionContext()

    // Persist the user account
    udsDbCtx.tryInTransaction {
      udsDbCtx.getEntityManager.persist(udsUser)
    }
    
  }

  override def getPropertiesFileName(): String = {
	return "db_settings/h2/db_uds.properties";
  }
  
  @After
  override def tearDown() {
    super.tearDown()
  }

  def buildUDSConnectionContext() {
    udsDbCtx = BuildUdsDbConnectionContext(getConnector, true)
  }

  @Test
  def authenticateUser() {
    val service = new UserAuthenticator(
      udsConnectionCtxt = udsDbCtx,
      name = udsUser.getLogin(),
      hashPassword = udsUser.getPasswordHash()
    )
    val isLoginOK = service.runService()
    
    if( !isLoginOK) {
      logger.error( service.getAuthenticateResultMessage() )
    }

    assertTrue(isLoginOK)
  }


}

