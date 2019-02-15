package fr.proline.core.om.provider

import fr.proline.context.DecoratedExecutionContext
import fr.proline.core.om.provider.msi.cache.PeptideCacheRegistry
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.cache.IPeptideCache

class PeptideCacheExecutionContext(wrappedExecutionContext: IExecutionContext) extends DecoratedExecutionContext(wrappedExecutionContext) {

  def getPeptideCache() : IPeptideCache =  {
    PeptideCacheRegistry.getOrCreate(wrappedExecutionContext.getProjectId)
  }

  override def clearContext(): Unit = {
    getPeptideCache().clear()
  }

}

object PeptideCacheExecutionContext {

  /**
    * Creates a [[PeptideCacheExecutionContext]] from given [[IExecutionContext]], possibly reusing current ProviderContext.
    */
  def apply(wrappedEC: IExecutionContext): PeptideCacheExecutionContext = {
    require(wrappedEC != null, "WrappedEC is null")

    var result: PeptideCacheExecutionContext = wrappedEC match {
      case context: PeptideCacheExecutionContext => context

      case context: DecoratedExecutionContext => context.find(classOf[PeptideCacheExecutionContext])
      // This one can return null also if wrappedEC does not contain any ProviderDecoratedExecutionContext

      case _ => null
    }

    if (result == null) {
      result = new PeptideCacheExecutionContext(wrappedEC)
    } else {
      result.clearContext() // Clear Context caches
    }

    result
  }

}