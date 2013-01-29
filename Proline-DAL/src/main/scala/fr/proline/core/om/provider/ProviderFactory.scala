package fr.proline.core.om.provider

import com.weiglewilczek.slf4s.Logging

import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.impl.ORMPTMProvider
import fr.proline.core.om.provider.msi.impl.ORMPeptideProvider
import fr.proline.core.om.provider.msi.impl.ORMProteinProvider
import fr.proline.core.om.provider.msi.impl.ORMSeqDatabaseProvider
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideMatchProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.provider.msi.IProteinProvider
import fr.proline.core.om.provider.msi.ISeqDatabaseProvider
import fr.proline.util.PropertiesUtils
import fr.proline.util.StringUtils

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
    } else {
      throw new IllegalArgumentException("ProviderFactory does not support " + providerClassifier)
    }

  }

  def getPeptideProviderInstance(executionContext: IExecutionContext): IPeptideProvider = {
    var result: IPeptideProvider = getDefaultProviderInstance(classOf[IPeptideProvider])

    if (result == null) {
      val psDb = executionContext.getPSDbConnectionContext

      if ((psDb != null) && psDb.isJPA) {
        result = new ORMPeptideProvider(psDb)
      }

      if (result == null) {
        logger.warn("No IPeptideProvider implementing instance found !!")
      }

    }

    result
  }

  def getPeptideMatchProviderInstance(executionContext: IExecutionContext): IPeptideMatchProvider = {
    var result: IPeptideMatchProvider = getDefaultProviderInstance(classOf[IPeptideMatchProvider])

    if (result == null) {
      logger.warn("No IPeptideMatchProvider implementing instance found !!")
    }

    result
  }

  def getProteinProviderInstance(executionContext: IExecutionContext): IProteinProvider = {
    var result: IProteinProvider = getDefaultProviderInstance(classOf[IProteinProvider])

    if (result == null) {
      val pdiDb = executionContext.getPDIDbConnectionContext

      if ((pdiDb != null) && pdiDb.isJPA) {
        result = new ORMProteinProvider(pdiDb)
      }

      if (result == null) {
        logger.warn("No IProteinProvider implementing instance found !!")
      }

    }

    result
  }

  def getSeqDatabaseProviderInstance(executionContext: IExecutionContext): ISeqDatabaseProvider = {
    var result: ISeqDatabaseProvider = getDefaultProviderInstance(classOf[ISeqDatabaseProvider])

    if (result == null) {
      val pdiDb = executionContext.getPDIDbConnectionContext

      if ((pdiDb != null) && pdiDb.isJPA) {
        result = new ORMSeqDatabaseProvider(pdiDb)
      }

      if (result == null) {
        logger.warn("No ISeqDatabaseProvider implementing instance found !!")
      }

    }

    result
  }

  def getPTMProviderInstance(executionContext: IExecutionContext): IPTMProvider = {
    var result: IPTMProvider = getDefaultProviderInstance(classOf[IPTMProvider])

    if (result == null) {
      val psDb = executionContext.getPSDbConnectionContext

      if ((psDb != null) && psDb.isJPA) {
        result = new ORMPTMProvider(psDb)
      }

      if (result == null) {
        logger.warn("No IPTMProvider implementing instance found !!")
      }

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
