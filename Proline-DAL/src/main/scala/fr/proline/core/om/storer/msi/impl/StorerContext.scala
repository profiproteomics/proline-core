package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable

import com.weiglewilczek.slf4s.Logging

import fr.proline.context.{ DecoratedExecutionContext, IExecutionContext }
import fr.proline.core.om.utils.PeptideIdent

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe (must be confined in thread context).
 */
class StorerContext(wrappedExecutionContext: IExecutionContext)
  extends DecoratedExecutionContext(wrappedExecutionContext) with Logging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  /* Db Context params check */
  require(getUDSDbConnectionContext != null, "UDS Db Context is null")
  require(getPDIDbConnectionContext != null, "PDI Db Context Db Context is null")
  require(getPSDbConnectionContext != null, "PS Db Context Db Context is null")
  require(getMSIDbConnectionContext != null, "MSI Db Context is null")

  /**
   * Already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
   * The map can be {{{null}}}.
   */
  var spectrumIdByTitle: Map[String, Long] = null

  var seqDbIdByTmpId: Map[Long, Long] = null // TODO To be integrated to idCaches

  private val m_entityCaches = mutable.Map.empty[Class[_], mutable.Map[Long, _]]

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
   * @param classifier Class of relevant Msi entity obtained with Scala {{{classOf[]}}} operator.
   * @return current cache for given Msi entity.
   */
  def getEntityCache[T](classifier: Class[T]): mutable.Map[Long, T] = {
    require(classifier != null, "Classifier is null")

    val knownCache = m_entityCaches.get(classifier)

    if (knownCache.isDefined) {
      knownCache.get.asInstanceOf[mutable.Map[Long, T]]
    } else {
      logger.debug("Creating context cache for " + classifier)

      val newCache = mutable.Map.empty[Long, T]

      m_entityCaches += classifier -> newCache

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

      for (map <- m_entityCaches.values) {
        map.clear()
      }

      m_entityCaches.clear()

      seqDbIdByTmpId = null

      spectrumIdByTitle = null
    } finally {
      super.clearContext()
    }

  }

}

object StorerContext {

  def apply(wrappedEC: IExecutionContext): StorerContext = {
    require(wrappedEC != null, "WrappedEC is null")

    var result: StorerContext = wrappedEC match {
      case context: StorerContext             => context

      case context: DecoratedExecutionContext => context.find(classOf[StorerContext])

      case _                                  => null
    }

    if (result == null) {
      result = new StorerContext(wrappedEC)
    } else {
      result.clearContext() // Clear Context caches
    }

    result
  }

}
