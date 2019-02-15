package fr.proline.core.dal

import scala.beans.BeanProperty
import com.typesafe.scalalogging.LazyLogging
import fr.proline.context._

class LazyExecutionContext(
  @BeanProperty val projectId: Long,
  val isJPA: Boolean,
  buildUdsDbCtx: () => UdsDbConnectionContext,
  buildMsiDbCtx: () => MsiDbConnectionContext,
  buildLcMsDbCtx: () => LcMsDbConnectionContext
) extends IExecutionContext with LazyLogging {
  
  private var isClosed = false

  private var udsDbCtxInitialized = false
  private lazy val udsDbCtx: UdsDbConnectionContext = synchronized {
    if(isClosed) null
    else {
      val dbCtx = buildUdsDbCtx()
      _isJPACheck(dbCtx)
      udsDbCtxInitialized = true
      dbCtx
    }
  }

  private var msiDbCtxInitialized = false
  private lazy val msiDbCtx: MsiDbConnectionContext = synchronized {
    if(isClosed) null
    else {
      val dbCtx = buildMsiDbCtx()
      _isJPACheck(dbCtx)
      msiDbCtxInitialized = true
      dbCtx
    }
  }

  private var lcMsDbCtxInitialized = false
  private lazy val lcMsDbCtx: LcMsDbConnectionContext = synchronized {
    if(isClosed) null
    else {
      val dbCtx = buildLcMsDbCtx()
      _isJPACheck(dbCtx)
      lcMsDbCtxInitialized = true
      dbCtx
    }
  }

  private def _isJPACheck(dbCtx: DatabaseConnectionContext) {
    require(dbCtx.isJPA() == isJPA, s"dbCtx JPA state is ${!isJPA} while ExecutionContext one is $isJPA")
  }

  override def getUDSDbConnectionContext(): UdsDbConnectionContext = udsDbCtx
  override def getMSIDbConnectionContext(): MsiDbConnectionContext = msiDbCtx
  override def getLCMSDbConnectionContext(): LcMsDbConnectionContext = lcMsDbCtx

  override def closeAll() {
    if (udsDbCtxInitialized && udsDbCtx != null) udsDbCtx.close()
    if (msiDbCtxInitialized && msiDbCtx != null) msiDbCtx.close()
    if (lcMsDbCtxInitialized && lcMsDbCtx != null) lcMsDbCtx.close()
    
    this.synchronized {
      isClosed = true
    }
  }

}
