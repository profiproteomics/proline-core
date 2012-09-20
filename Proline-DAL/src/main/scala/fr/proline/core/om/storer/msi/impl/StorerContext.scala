package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.DatabaseManagement
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.utils.PeptideIdent
import fr.proline.core.orm.utils.JPAUtil
import fr.proline.repository.DatabaseConnector
import javax.persistence.EntityManager
import javax.persistence.Persistence

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe.
 *
 * @param spectrumIdByTitle already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
 * The map can be {{{null}}}
 */
class StorerContext(val dbManagement: DatabaseManagement, msiConnector: DatabaseConnector = null) extends Logging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  // Define secondary constructors
  def this(dbManagement: DatabaseManagement, projectId: Int = 0) = {
    this(dbManagement, dbManagement.getMSIDatabaseConnector(projectId, true))
  }

  lazy val msiEm: EntityManager = {
    if (msiConnector != null)
      null
    val emf = Persistence.createEntityManagerFactory(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName, msiConnector.getEntityManagerSettings)
    msiEMOpened = true
    emf.createEntityManager
  }
  private var msiEMOpened: Boolean = false

  lazy val psEm: EntityManager = {
    psEMOpened = true
    dbManagement.psEMF.createEntityManager
  }
  private var psEMOpened: Boolean = false

  lazy val udsEm: EntityManager = {
    udsEMOpened = true
    dbManagement.udsEMF.createEntityManager
  }
  private var udsEMOpened: Boolean = false

  lazy val pdiEm: EntityManager = {
    pdiEMOpened = true
    dbManagement.pdiEMF.createEntityManager
  }
  private var pdiEMOpened: Boolean = false

  lazy val msiDB: MsiDb = new MsiDb(msiConnector)

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