package fr.proline.core.dal

import com.typesafe.scalalogging.StrictLogging

import fr.proline.repository.DatabaseUpgrader
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.repository.IDatabaseConnector

class DataStoreConnectorFactoryForTest(private val udsDb: IDatabaseConnector = null,
  private val pdiDb: IDatabaseConnector = null,
  private val psDb: IDatabaseConnector = null,
  private val msiDb: IDatabaseConnector = null,
  private val lcMsDb: IDatabaseConnector = null,
  private val initialize: Boolean = false) extends IDataStoreConnectorFactory with StrictLogging {

  private val m_closeLock = new AnyRef()

  if (udsDb == null) {
    logger.warn("No UDS Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(udsDb, false)
    }

  }

  if (pdiDb == null) {
    logger.warn("No PDI Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(pdiDb, false)
    }

  }

  if (psDb == null) {
    logger.warn("No PS Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(psDb, false)
    }

  }

  if (msiDb == null) {
    logger.warn("No MSI Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(msiDb, false)
    }

  }

  if (lcMsDb == null) {
    logger.warn("No LCMS Db Connector")
  } else {

    if (initialize) {
      DatabaseUpgrader.upgradeDatabase(lcMsDb, false)
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
  override def getMsiDbConnector(projectId: Long) = {
    msiDb
  }

  /**
   * Return the same LCMS Db for all projectId.
   */
  override def getLcMsDbConnector(projectId: Long) = {
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
  
  override def closeLcMsDbConnector(projectId: Long) {

    m_closeLock.synchronized {
      if (lcMsDb != null) {
        lcMsDb.close()
      }
    } // End of synchronized block on m_closeLock

  }
  
  override def closeMsiDbConnector(projectId: Long) {

    m_closeLock.synchronized {
      if (msiDb != null) {
        msiDb.close()
      }
    } // End of synchronized block on m_closeLock

  }
  
  override def closeProjectConnectors(projectId: Long) {
    logger.warn("Closing this DataStoreConnectorFactoryForTest : use DatabaseTestCase.tearDown() preferably")

    this.closeLcMsDbConnector(projectId)
    this.closeMsiDbConnector(projectId)

  }

}