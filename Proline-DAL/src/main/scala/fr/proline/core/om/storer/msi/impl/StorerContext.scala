package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.utils.PeptideIdent
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.repository.DatabaseContext
import fr.proline.repository.IDatabaseConnector

/**
 * RsStorer context container. Contains current ResultSet (and DecoyRS) "persistence context".
 * Objects of this class are NOT thread-safe.
 *
 * @param spectrumIdByTitle already persisted Msi Spectrum Ids retrievable by {{{Spectrum.title}}} string.
 * The map can be {{{null}}}
 */
class StorerContext(val udsDbContext: DatabaseContext,
  val pdiDbContext: DatabaseContext,
  val psDbContext: DatabaseContext,
  val msiDbContext: DatabaseContext) extends Logging {

  type MsiPeptide = fr.proline.core.orm.msi.Peptide

  /* Constructor params check */
  require(udsDbContext != null, "UDS Db Context is null")
  require(pdiDbContext != null, "PDI Db Context Db Context is null")
  require(psDbContext != null, "PS Db Context Db Context is null")
  require(msiDbContext != null, "MSI Db Context is null")

  var spectrumIdByTitle: Map[String, Int] = null

  var seqDbIdByTmpId: Map[Int, Int] = null // TODO To be integrated to idCaches

  def isJPA(): Boolean = {
    msiDbContext.isJPA
  }

  def closeAll() {

    if (msiDbContext != null) {
      msiDbContext.close()
    }

    if (psDbContext != null) {
      psDbContext.close()
    }

    if (pdiDbContext != null) {
      pdiDbContext.close()
    }

    if (udsDbContext != null) {
      udsDbContext.close()
    }

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

object StorerContextBuilder {

  def apply(dbManager: DatabaseManager, projectId: Int, useJpa: Boolean = true): StorerContext = {
    this.apply(dbManager, dbManager.getMsiDbConnector(projectId), useJpa)
  }

  def apply(dbManager: DatabaseManager, msiDbConnector: IDatabaseConnector, useJpa: Boolean): StorerContext = {

    val udsDbConnector = dbManager.getUdsDbConnector

    val udsDb = if (useJpa) {
      new DatabaseContext(udsDbConnector)
    } else {
      new DatabaseContext(udsDbConnector.getDataSource.getConnection, udsDbConnector.getDriverType)
    }

    val pdiDbConnector = dbManager.getPdiDbConnector

    val pdiDb = if (useJpa) {
      new DatabaseContext(pdiDbConnector)
    } else {
      new DatabaseContext(pdiDbConnector.getDataSource.getConnection, pdiDbConnector.getDriverType)
    }

    val psDbConnector = dbManager.getPsDbConnector

    val psDb = if (useJpa) {
      new DatabaseContext(psDbConnector)
    } else {
      new DatabaseContext(psDbConnector.getDataSource.getConnection, psDbConnector.getDriverType)
    }

    val msiDb = if (useJpa) {
      new DatabaseContext(msiDbConnector)
    } else {
      new DatabaseContext(msiDbConnector.getDataSource.getConnection, msiDbConnector.getDriverType)
    }

    new StorerContext(udsDb, pdiDb, psDb, msiDb)
  }

}
