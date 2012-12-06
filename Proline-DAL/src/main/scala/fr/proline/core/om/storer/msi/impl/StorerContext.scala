package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable
import javax.persistence.EntityManager
import javax.persistence.Persistence
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.MsiDbSQLHelper
import fr.proline.core.om.utils.PeptideIdent
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.repository.IDatabaseConnector
import fr.proline.repository.util.JPAUtils

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe.
 *
 * @param spectrumIdByTitle already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
 * The map can be {{{null}}}
 */
class StorerContext(val dbManagement: DatabaseManager, val msiConnector: IDatabaseConnector = null) extends Logging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  // Define secondary constructors
  def this(dbManagement: DatabaseManager, projectId: Int = 0) = {
    this(dbManagement, dbManagement.getMsiDbConnector(projectId) )
  }

  lazy val msiEm: EntityManager = {
    if (msiConnector != null)
      null
    val emf = msiConnector.getEntityManagerFactory
    msiEMOpened = true
    emf.createEntityManager
  }
  private var msiEMOpened: Boolean = false

  lazy val psEm: EntityManager = {
    psEMOpened = true
    dbManagement.getPsDbConnector.getEntityManagerFactory.createEntityManager
  }
  private var psEMOpened: Boolean = false

  lazy val udsEm: EntityManager = {
    udsEMOpened = true
    dbManagement.getUdsDbConnector.getEntityManagerFactory.createEntityManager
  }
  private var udsEMOpened: Boolean = false

  lazy val pdiEm: EntityManager = {
    pdiEMOpened = true
    val p = dbManagement.getPdiDbConnector
    val emf = p.getEntityManagerFactory
    emf.createEntityManager
  }
  private var pdiEMOpened: Boolean = false

  lazy val msiDB = new MsiDbSQLHelper(msiConnector)

  var spectrumIdByTitle: Map[String, Int] = null

  var seqDbIdByTmpId: Map[Int, Int] = null // TODO To be integrated to idCaches 

  def closeOpenedEM() = {
    if (msiEMOpened)
      msiEm.close

    if (psEMOpened)
      psEm.close

    if (pdiEMOpened)
      pdiEm.close

    if (udsEMOpened)
      udsEm.close

  }

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

    if (classifier == null) {
      throw new IllegalArgumentException("Classifier is null")
    }

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