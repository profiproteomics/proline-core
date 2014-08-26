package fr.proline.core.service.uds

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.context._
import fr.proline.core.dbunit.DbUnitInitDataset
import fr.proline.core.orm.uds.UserAccount
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase

class AuthenticationTest extends DatabaseTestCase with Logging {

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
    //loadDataSet("/dbunit_samples/" + fileName + "/uds-db.xml")
    loadDataSet(DbUnitInitDataset.UDS_DB.getResourcePath)
    
    logger.info("UDS db succesfully initialized !")
    buildUDSConnectionContext()

    // Persist the user account
    udsDbCtx.tryInTransaction {
      udsDbCtx.getEntityManager.persist(udsUser)
    }
    
  }

  @After
  override def tearDown() {
    super.tearDown()
  }

  def buildUDSConnectionContext() {
    udsDbCtx = ContextFactory.buildDbConnectionContext(getConnector, true)
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
      logger.error( service.getErrorMessage )
    }

    assertTrue(isLoginOK)
  }

}

