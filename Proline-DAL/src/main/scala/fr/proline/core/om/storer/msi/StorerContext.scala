package fr.proline.core.om.storer.msi

import scala.collection.mutable
import fr.proline.core.om.utils.PeptideIdent
import javax.persistence.EntityManager
import com.weiglewilczek.slf4s.Logging

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe.
 *
 * @param spectrumIdByTitle already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
 * The map can be {{{null}}}
 */
class StorerContext(val msiEm: EntityManager,
  val psEm: EntityManager,
  val udsEm: EntityManager,
  val pdiEm: EntityManager,
  val spectrumIdByTitle: Map[String, Int]) extends Logging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  /* Check EntityManagers class parameters */
  checkEntityManager(msiEm)
  checkEntityManager(psEm)
  checkEntityManager(udsEm)
  checkEntityManager(pdiEm)

  private val entityCaches = mutable.Map.empty[Class[_], mutable.Map[Int, _]]

  /**
   * Retrieved and created Msi Peptide entities.
   */
  val msiPeptides = mutable.Map.empty[PeptideIdent, MsiPeptide]

  /**
   * Retrieves the current cache for a given Msi entity.
   * These are global caches (for entities shared by ResultSets: Decoy, children...)
   * within the current persistence context (Msi {{{EntityManager}}} session and transaction).
   * Caches associate OM Id (can be "In memory" < 0) -> ORM loaded or persisted entity from context {{{EntityManager}}}.
   * MsiSeqDatabase cache can contain {{{null}}} values if SeqDatabase not found in Pdi Db.
   *
   * @param classifier Class of relevant Msi entity obtained with Scala {{{classOf[]}}} operator.
   * @return current cache for given Msi entity.
   */
  def getEntityCache[T](classifier: Class[T]): mutable.Map[Int, T] = {

    if (classifier == null) {
      throw new IllegalArgumentException("Classifier is null")
    }

    val knownCache = entityCaches.get(classifier)

    if (knownCache.isDefined) {
      knownCache.get.asInstanceOf[mutable.Map[Int, T]]
    } else {
      logger.debug("Creating context cache for " + classifier)

      val newCache = mutable.Map.empty[Int, T]

      entityCaches += classifier -> newCache

      newCache
    }

  }

  /* Private methods */
  private def checkEntityManager(em: EntityManager) {

    if ((em == null) || !em.isOpen) {
      throw new IllegalArgumentException("Invalid EntityManager")
    }

  }

}