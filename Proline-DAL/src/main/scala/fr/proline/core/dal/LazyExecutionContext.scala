package fr.proline.core.dal

import com.typesafe.scalalogging.LazyLogging
import fr.proline.context._

class LazyExecutionContext(
  val isJPA: Boolean,
  buildUdsDbCtx: () => UdsDbConnectionContext,
  buildPdiDbCtx: () => DatabaseConnectionContext,
  buildPsDbCtx: () => DatabaseConnectionContext,
  buildMsiDbCtx: () => MsiDbConnectionContext,
  buildLcMsDbCtx: () => LcMsDbConnectionContext
) extends IExecutionContext with LazyLogging {

  private var udsDbCtxInitialized = false
  private lazy val udsDbCtx: UdsDbConnectionContext = synchronized {
    val dbCtx = buildUdsDbCtx()
    _isJPACheck(dbCtx)
    udsDbCtxInitialized = true
    dbCtx
  }

  private var pdiDbCtxInitialized = false
  private lazy val pdiDbCtx: DatabaseConnectionContext = synchronized {
    val dbCtx = buildPdiDbCtx()
    _isJPACheck(dbCtx)
    pdiDbCtxInitialized = true
    dbCtx
  }

  private var psDbCtxInitialized = false
  private lazy val psDbCtx: DatabaseConnectionContext = synchronized {
    val dbCtx = buildPsDbCtx()
    _isJPACheck(dbCtx)
    psDbCtxInitialized = true
    dbCtx
  }

  private var msiDbCtxInitialized = false
  private lazy val msiDbCtx: MsiDbConnectionContext = synchronized {
    val dbCtx = buildMsiDbCtx()
    _isJPACheck(dbCtx)
    msiDbCtxInitialized = true
    dbCtx
  }

  private var lcMsDbCtxInitialized = false
  private lazy val lcMsDbCtx: LcMsDbConnectionContext = synchronized {
    val dbCtx = buildLcMsDbCtx()
    _isJPACheck(dbCtx)
    lcMsDbCtxInitialized = true
    dbCtx
  }

  private def _isJPACheck(dbCtx: DatabaseConnectionContext) {
    require(dbCtx.isJPA() == isJPA, s"dbCtx JPA state is ${!isJPA} while ExecutionContext one is $isJPA")
  }

  override def getUDSDbConnectionContext(): UdsDbConnectionContext = udsDbCtx
  override def getPDIDbConnectionContext(): DatabaseConnectionContext = pdiDbCtx
  override def getPSDbConnectionContext(): DatabaseConnectionContext = psDbCtx
  override def getMSIDbConnectionContext(): MsiDbConnectionContext = msiDbCtx
  override def getLCMSDbConnectionContext(): LcMsDbConnectionContext = lcMsDbCtx

  override def closeAll() {
    if (udsDbCtxInitialized && udsDbCtx != null) udsDbCtx.close()
    if (pdiDbCtxInitialized && pdiDbCtx != null) pdiDbCtx.close()
    if (psDbCtxInitialized && psDbCtx != null) psDbCtx.close()
    if (msiDbCtxInitialized && msiDbCtx != null) msiDbCtx.close()
    if (lcMsDbCtxInitialized && lcMsDbCtx != null) lcMsDbCtx.close()
  }

}
