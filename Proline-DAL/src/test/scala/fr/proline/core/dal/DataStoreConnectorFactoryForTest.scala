package fr.proline.core.dal

import com.weiglewilczek.slf4s.Logging

import fr.proline.repository.DatabaseUpgrader
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.repository.IDatabaseConnector

class DataStoreConnectorFactoryForTest(private val udsDb: IDatabaseConnector = null,
  private val pdiDb: IDatabaseConnector = null,
  private val psDb: IDatabaseConnector = null,
  private val msiDb: IDatabaseConnector = null,
  private val lcMsDb: IDatabaseConnector = null,
  private val initialize: Boolean = false) extends IDataStoreConnectorFactory with Logging {

  private val m_closeLock = new AnyRef()

  if (udsDb == null) {
    logger.warn("No UDS Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(udsDb)
    }

  }

  if (pdiDb == null) {
    logger.warn("No PDI Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(pdiDb)
    }

  }

  if (psDb == null) {
    logger.warn("No PS Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(psDb)
    }

  }

  if (msiDb == null) {
    logger.warn("No MSI Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(msiDb)
    }

  }

  if (lcMsDb == null) {
    logger.warn("No LCMS Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(lcMsDb)
    }

  }

  override def isInitialized() = {
    true
  }

  override def getUdsDbConnector() = {
    udsDb
  }

  override def getPdiDbConnector() = {
    pdiDb
  }

  override def getPsDbConnector() = {
    psDb
  }

  /**
   * Return the same MSI Db for all projectId.
   */
  override def getMsiDbConnector(projectId: Int) = {
    msiDb
  }

  /**
   * Return the same LCMS Db for all projectId.
   */
  override def getLcMsDbConnector(projectId: Int) = {
    lcMsDb
  }

  override def closeAll() {
    logger.warn("Closing this DataStoreConnectorFactoryForTest : use DatabaseTestCase.tearDown() preferably")

    m_closeLock.synchronized {

      if (lcMsDb != null) {
        lcMsDb.close()
      }

      if (msiDb != null) {
        msiDb.close()
      }

      if (psDb != null) {
        psDb.close()
      }

      if (pdiDb != null) {
        pdiDb.close()
      }

      if (udsDb != null) {
        udsDb.close()
      }

    } // End of synchronized block on m_closeLock

  }

}