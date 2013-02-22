package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.utils.PeptideIdent
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.IDatabaseConnector
import fr.proline.context.DecoratedExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.context.BasicExecutionContext

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe.
 *
 * @param spectrumIdByTitle already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
 * The map can be {{{null}}}
 */
class StorerContext(wrappedExecutionContext: IExecutionContext)
  extends DecoratedExecutionContext(wrappedExecutionContext) with Logging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  /* Db Context params check */
  require(getUDSDbConnectionContext != null, "UDS Db Context is null")
  require(getPDIDbConnectionContext != null, "PDI Db Context Db Context is null")
  require(getPSDbConnectionContext != null, "PS Db Context Db Context is null")
  require(getMSIDbConnectionContext != null, "MSI Db Context is null")

  var spectrumIdByTitle: Map[String, Int] = null

  var seqDbIdByTmpId: Map[Int, Int] = null // TODO To be integrated to idCaches

  private val entityCaches = mutable.Map.empty[Class[_], mutable.Map[Int, _]]

  private val idCaches = mutable.Map.empty[Class[_], mutable.Map[Int, Int]]

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

    require(classifier != null, "Classifier is null")

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

  /**
   * Retrieves the current cache for a given Msi entity.
   * These are global caches (for entities shared by ResultSets: Decoy, children...)
   * within the current persistence context (Msi {{{EntityManager}}} session and transaction).
   * Caches associate OM Id (can be "In memory" < 0) -> ORM Id once persisted {{{EntityManager}}}.
   *
   * @param classifier Class of relevant OM Msi entity obtained with Scala {{{classOf[]}}} operator.
   * @return current cache for given Msi entity.
   */
  def getIdCache[T](classifier: Class[T]): mutable.Map[Int, Int] = {

    require(classifier != null, "Classifier is null")

    val knownCache = idCaches.get(classifier)

    if (knownCache.isDefined) {
      knownCache.get.asInstanceOf[mutable.Map[Int, Int]]
    } else {
      logger.debug("Creating ID context cache for " + classifier)

      val newCache = mutable.Map.empty[Int, Int]

      idCaches += classifier -> newCache

      newCache
    }

  }

}
