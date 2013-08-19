package fr.proline.core.om.provider

import scala.collection.mutable

import fr.proline.context.DecoratedExecutionContext
import fr.proline.context.IExecutionContext

/**
 * ExecutionContext with a map of entity Providers (can be used in Mascot / OMSSA parsers).
 * Default factory for Providers is [[fr.proline.core.om.provider.ProviderFactory]] .
 * Objects of this class are NOT thread-safe (must be confined in thread context).
 */
class ProviderDecoratedExecutionContext(wrappedExecutionContext: IExecutionContext, providerFactory: IProviderFactory = ProviderFactory)
  extends DecoratedExecutionContext(wrappedExecutionContext) {

  private val m_providers = mutable.Map.empty[Class[_], AnyRef]

  def putProvider[T <: AnyRef](providerClassifier: Class[T], providerInstance: T): Option[T] = {
    require(providerClassifier != null, "ProviderClassifier is null")

    val oldProvider = m_providers.put(providerClassifier, providerInstance)
    oldProvider.map(_.asInstanceOf[T]) // Must be a T
  }

  def getProvider[T <: AnyRef](providerClassifier: Class[T]): T = {
    require(providerClassifier != null, "ProviderClassifier is null")

    val currentProviderOpt = m_providers.get(providerClassifier)

    if (currentProviderOpt.isDefined) {
      currentProviderOpt.get.asInstanceOf[T]
    } else {
      val newProviderInstance = providerFactory.getProviderInstance(providerClassifier, this)

      if (newProviderInstance != null) {
        putProvider(providerClassifier, newProviderInstance)
      }

      newProviderInstance
    }

  }

}

object ProviderDecoratedExecutionContext {

  /**
   * Creates a [[ProviderDecoratedExecutionContext]] from given [[IExecutionContext]], possibly reusing current ProviderContext.
   */
  def apply(wrappedEC: IExecutionContext): ProviderDecoratedExecutionContext = {
    require(wrappedEC != null, "WrappedEC is null")

    var result: ProviderDecoratedExecutionContext = wrappedEC match {
      case context: ProviderDecoratedExecutionContext => context

      case context: DecoratedExecutionContext => context.find(classOf[ProviderDecoratedExecutionContext])
      // This one can return null also if wrappedEC does not contain any ProviderDecoratedExecutionContext

      case _ => null
    }

    if (result == null) {
      result = new ProviderDecoratedExecutionContext(wrappedEC)
    } else {
      result.clearContext() // Clear Context caches
    }

    result
  }

}
