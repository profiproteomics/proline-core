package fr.proline.core.om.provider

import com.weiglewilczek.slf4s.Logging
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.impl.{ ORMSeqDatabaseProvider, ORMProteinProvider, ORMPeptideProvider, ORMPTMProvider }
import fr.proline.core.om.provider.msi.{ IProteinProvider, IPeptideProvider, IPTMProvider }
import fr.proline.core.om.provider.msi.{ ISeqDatabaseProvider, IPeptideMatchProvider }
import fr.proline.util.{ StringUtils, PropertiesUtils }
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider

trait IProviderFactory {

  def getProviderInstance[T <: AnyRef](providerClassifier: Class[T], executionContext: IExecutionContext): T

}

// TODO Use dependency injection (Giuce, CDI ..) instead of m_providersProperties

/**
 * Default Factory of Provider implementing instances.
 *
 * This factory tries to instanciate Provider implementation by its class names given in the "providers.properties" file.
 * If there is no class name for a given Provider Trait in "providers.properties" file, this factory tries to give an ORM Provider implementating instance.
 *
 * For now, supported Providers are : IPeptideProvider, IPeptideMatchProvider, IProteinProvider, ISeqDatabaseProvider, IPTMProvider
 */
object ProviderFactory extends IProviderFactory with Logging {

  private val PROVIDERS_PROPERTIES_FILE_NAME = "providers.properties"

  private val m_providersProperties = PropertiesUtils.loadProperties(PROVIDERS_PROPERTIES_FILE_NAME)

  def getProviderInstance[T <: AnyRef](providerClassifier: Class[T], executionContext: IExecutionContext): T = {

    if (providerClassifier == null) {
      throw new IllegalArgumentException("ProviderClassifier is null")
    }

    if (executionContext == null) {
      throw new IllegalArgumentException("ExecutionContext is null")
    }

    if (providerClassifier == classOf[IPeptideProvider]) {
      getPeptideProviderInstance(executionContext).asInstanceOf[T]
    } else if (providerClassifier == classOf[IPeptideMatchProvider]) {
      getPeptideMatchProviderInstance(executionContext).asInstanceOf[T]
    } else if (providerClassifier == classOf[IProteinProvider]) {
      getProteinProviderInstance(executionContext).asInstanceOf[T]
    } else if (providerClassifier == classOf[ISeqDatabaseProvider]) {
      getSeqDatabaseProviderInstance(executionContext).asInstanceOf[T]
    } else if (providerClassifier == classOf[IPTMProvider]) {
      getPTMProviderInstance(executionContext).asInstanceOf[T]
    } else if (providerClassifier == classOf[IResultSetProvider]) {
      getResultSetProviderInstance(executionContext).asInstanceOf[T]
    } else {
      throw new IllegalArgumentException("ProviderFactory does not support " + providerClassifier)
    }

  }

  def getPeptideProviderInstance(executionContext: IExecutionContext): IPeptideProvider = {
    var result: IPeptideProvider = getDefaultProviderInstance(classOf[IPeptideProvider])

    if (result == null) {
      val psDb = executionContext.getPSDbConnectionContext

      if ((psDb != null) && psDb.isJPA) {
        logger.debug("Creating a default ORMPeptideProvider in current executionContext")

        result = new ORMPeptideProvider(psDb)
      }

    }

    if (result == null) {
      logger.warn("No IPeptideProvider implementing instance found !!")
    } else {
      logger.debug("PeptideProvider implementation : " + result.getClass.getName)
    }

    result
  }

  def getPeptideMatchProviderInstance(executionContext: IExecutionContext): IPeptideMatchProvider = {
    var result: IPeptideMatchProvider = getDefaultProviderInstance(classOf[IPeptideMatchProvider])

    if (result == null) {
      logger.warn("No IPeptideMatchProvider implementing instance found !!")
    } else {
      logger.debug("PeptideMatchProvider implementation : " + result.getClass.getName)
    }

    result
  }

  def getProteinProviderInstance(executionContext: IExecutionContext): IProteinProvider = {
    var result: IProteinProvider = getDefaultProviderInstance(classOf[IProteinProvider])

    if (result == null) {
      val pdiDb = executionContext.getPDIDbConnectionContext

      if ((pdiDb != null) && pdiDb.isJPA) {
        logger.debug("Creating a default ORMProteinProvider in current executionContext")

        result = new ORMProteinProvider(pdiDb)
      }

    }

    if (result == null) {
      logger.warn("No IProteinProvider implementing instance found !!")
    } else {
      logger.debug("ProteinProvider implementation : " + result.getClass.getName)
    }

    result
  }

  def getSeqDatabaseProviderInstance(executionContext: IExecutionContext): ISeqDatabaseProvider = {
    var result: ISeqDatabaseProvider = getDefaultProviderInstance(classOf[ISeqDatabaseProvider])

    if (result == null) {
      val pdiDb = executionContext.getPDIDbConnectionContext

      if ((pdiDb != null) && pdiDb.isJPA) {
        logger.debug("Creating a default ORMSeqDatabaseProvider in current executionContext")

        result = new ORMSeqDatabaseProvider(pdiDb)
      }

    }

    if (result == null) {
      logger.warn("No ISeqDatabaseProvider implementing instance found !!")
    } else {
      logger.debug("SeqDatabaseProvider implementation : " + result.getClass.getName)
    }

    result
  }

  def getResultSetProviderInstance(executionContext: IExecutionContext): IResultSetProvider = {
    var result: IResultSetProvider = getDefaultProviderInstance(classOf[IResultSetProvider])

    if (result == null) {
      val msiDb = executionContext.getMSIDbConnectionContext
      val psDb = executionContext.getPSDbConnectionContext

      if (msiDb != null) {

        if (msiDb.isJPA) {
          logger.debug("Creating a default ORMResultSetProvider in current executionContext")

          /* ORMResultSetProvider(msiDbCtx, psDbCtx, pdiDbCtx) */
          result = new ORMResultSetProvider(msiDb, psDb, executionContext.getPDIDbConnectionContext)
        } else {
          logger.debug("Creating a default SQLResultSetProvider in current executionContext")

          /* SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx) */
          result = new SQLResultSetProvider(msiDb, psDb, executionContext.getUDSDbConnectionContext)
        }

      } else {
        logger.warn("MSIDbConnectionContex is null : No IResultSetProvider implementing instance can be created")
      }

    }

    if (result != null) {
      logger.debug("ResultSetProvider implementation : " + result.getClass.getName)
    }

    result
  }

  def getPTMProviderInstance(executionContext: IExecutionContext): IPTMProvider = {
    var result: IPTMProvider = getDefaultProviderInstance(classOf[IPTMProvider])

    if (result == null) {
      val psDb = executionContext.getPSDbConnectionContext

      if ((psDb != null) && psDb.isJPA) {
        logger.debug("Creating a default ORMPTMProvider in current executionContext")

        result = new ORMPTMProvider(psDb)
      }

    }

    if (result == null) {
      logger.warn("No IPTMProvider implementing instance found !!")
    } else {
      logger.debug("PTMProvider implementation : " + result.getClass.getName)
    }

    result
  }

  private def getDefaultProviderInstance[T <: AnyRef](providerClassifier: Class[T]): T = {
    var result: T = null.asInstanceOf[T]

    if (m_providersProperties != null) {
      val className = m_providersProperties.getProperty(providerClassifier.getName)

      if (!StringUtils.isEmpty(className)) {

        try {
          val targetClass = Class.forName(className)

          /* For now : only handle default (no arg) constructor */
          result = targetClass.newInstance().asInstanceOf[T]
        } catch {
          case ex: Exception => logger.error("Cannot instantiate [" + className + ']', ex)
        }

      } // End if (className is not empty)

    } // End if (m_providersProperties is not null)

    result
  }

}
