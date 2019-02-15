package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable

import com.typesafe.scalalogging.LazyLogging

import fr.proline.context.{ DecoratedExecutionContext, IExecutionContext }
import fr.proline.core.om.util.PeptideIdent

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe (must be confined in thread context).
 */
class StorerContext(wrappedExecutionContext: IExecutionContext)
  extends DecoratedExecutionContext(wrappedExecutionContext) with LazyLogging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  /* Db Context params check */
  require(getUDSDbConnectionContext != null, "UDS Db Context is null")
  require(getMSIDbConnectionContext != null, "MSI Db Context is null")

  /**
   * Already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
   * The map can be {{{null}}}.
   */
  var spectrumIdByTitle: Map[String, Long] = null

  var seqDbIdByTmpId: mutable.LongMap[Long] = null // TODO: To be integrated to idCaches

  private val _entityCaches = mutable.Map.empty[Class[_], mutable.LongMap[_]]

  /**
   * Retrieved and created Msi Peptide entities.
   */
  val msiPeptides = mutable.Map.empty[PeptideIdent, MsiPeptide]

  /**
   * Retrieves the current cache for a given Msi entity.
   * These are global caches (for entities shared by ResultSets: Decoy, children...)
   * within the current persistence context (Msi [[EntityManager]] session and transaction).
   * Caches associate OM Id (can be "In memory" < 0) -> ORM loaded or persisted entity from context [[EntityManager]].
   * MsiSeqDatabase cache can contain {{{null}}} values if SeqDatabase not found in Pdi Db.
   *
   * @param entityClass Class of relevant Msi entity obtained with Scala {{{classOf[]}}} operator.
   * @return current cache for given Msi entity.
   */
  def getEntityCache[T](entityClass: Class[T]): mutable.Map[Long, T] = {
    require(entityClass != null, "entityClass is null")

    val knownCache = _entityCaches.get(entityClass)

    if (knownCache.isDefined) {
      knownCache.get.asInstanceOf[mutable.LongMap[T]]
    } else {
      logger.debug(s"Creating context cache for class '$entityClass'" )

      val newCache = mutable.LongMap.empty[T]

      _entityCaches += entityClass -> newCache

      newCache
    }

  }

  /**
   * Clears all caches of this [[StorerContext]].
   */
  override def clearContext() {

    try {
      /* Specific StorerContext clean-up : clears collection and caches */
      msiPeptides.clear()

      for (map <- _entityCaches.values) {
        map.clear()
      }

      _entityCaches.clear()

      seqDbIdByTmpId = null
      spectrumIdByTitle = null
      
    } finally {
      super.clearContext()
    }

  }

}

object StorerContext {

  /**
   * Creates a [[StorerContext]] instance from given [[IExecutionContext]], possibly reusing current [[StorerContext]].
   */
  def apply(wrappedEC: IExecutionContext): StorerContext = {
    require(wrappedEC != null, "WrappedEC is null")

    val result: StorerContext = wrappedEC match {
      case context: StorerContext             => context
      case context: DecoratedExecutionContext => context.find(classOf[StorerContext])
      case _                                  => null
    }

    if (result == null)
      return new StorerContext(wrappedEC)

    // Clear Context caches
    result.clearContext() 

    result
  }

}
