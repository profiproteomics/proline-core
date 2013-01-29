package fr.proline.core.om.storer.msi.impl

import java.sql.Connection
import fr.profi.jdbc.easy._
import fr.proline.core.dal._
import fr.proline.core.om.model.msi.IPeaklistContainer
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.PeaklistWriter
import fr.proline.repository.util.JDBCWork
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.context.DatabaseConnectionContext
import javax.persistence.EntityTransaction
import fr.proline.context.ContextFactory

abstract class AbstractRsStorer(val plWriter: IPeaklistWriter = null) extends IRsStorer {

  // IPeaklistWriter to use to store PeakList and Spectrum 
  var localPlWriter = plWriter

  type MsiResultSet = fr.proline.core.orm.msi.ResultSet

  /**
   * Store specified ResultSet in persistence repository, using  storerContext for context and mapping information
   *
   * This default implementation of IRsStorer will call  final def storeResultSet(...) method without
   * specifying spectra or MsQueries
   *
   */
  def storeResultSet(resultSet: ResultSet, storerContext: StorerContext): Int = {

    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }

    if (storerContext == null) {
      throw new IllegalArgumentException("StorerContext is null")
    }

    storeResultSet(resultSet = resultSet,
      msQueries = null,
      peakListContainer = null,
      storerContext = storerContext)

  }

  def storeResultSet(resultSet: ResultSet, dbManager: IDataStoreConnectorFactory, projectId: Int ): Int = {

    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }

    if (dbManager == null) {
      throw new IllegalArgumentException("DbManager is null")
    }

    var rsId: Int = 0
    var storerContext: StorerContext = null // For JPA use
    var msiTransaction: EntityTransaction = null
    var msiTransacOk: Boolean = false

    try {
      
      storerContext = new StorerContext(ContextFactory.getExecutionContextInstance(dbManager, projectId, true))
      
      val msiDb = storerContext.getMSIDbConnectionContext
      val msiEm = msiDb.getEntityManager()

      // Begin transaction
      msiTransaction = msiEm.getTransaction
      msiTransaction.begin()
      msiTransacOk = false

      rsId = storeResultSet(
        resultSet = resultSet,
        msQueries = null,
        peakListContainer = null,
        storerContext = storerContext
      )

      // Commit transaction
      msiTransaction.commit()
      msiTransacOk = true
      
    } finally {

      if ((msiTransaction != null) && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          msiTransaction.rollback()
        } catch {
          case ex: Exception => logger.error("Error rollbacking MSI Db Transaction", ex)
        }

      }

      if (storerContext != null) {
        storerContext.closeAll()
      }

    }

    rsId
  }

  /**
   * This method will first save spectra and queries related data specified by peakListContainer and msQueries. This will be done using
   * IRsStorer storePeaklist, storeMsiSearch,  storeMsQueries and storeSpectra methods
   *
   * Then the implementation of the abstract createResultSet method will be executed to save other data.
   * TODO : use other Storer / Writer : peptideStorer / ProteinMatch
   *
   *
   */
  final def storeResultSet(resultSet: ResultSet, msQueries: Seq[MsQuery], peakListContainer: IPeaklistContainer, storerContext: StorerContext): Int = {

    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }

    if (storerContext == null) {
      throw new IllegalArgumentException("StorerContext is null")
    }

    logger.info("Storing ResultSet " + resultSet.name)

    //Create a StorerContext if none was specified
    
    val omResultSetId = resultSet.id

    if (omResultSetId > 0)
      throw new UnsupportedOperationException("Updating a ResultSet is not supported yet !")

    val oldPlWriter = localPlWriter

    if (localPlWriter == null) {
      localPlWriter = PeaklistWriter(storerContext.getMSIDbConnectionContext.getDriverType)
    }

    var plID: Int = -1

    // Save Spectra and Queries information (MSISearch should  be defined)
    val msiSearch = resultSet.msiSearch
    
    if (msiSearch != null) {

      // Save Peaklist information
      plID = this.storePeaklist(msiSearch.peakList, storerContext)
      //update Peaklist ID in MSISearch
      msiSearch.peakList.id = plID

      // Save spectra retrieve by peakListContainer 
      if (peakListContainer != null)
        this.storeSpectra(plID, peakListContainer, storerContext)

      //TODO : Remove when shared transaction !!! : Close msiDB connection if used by previous store methods 

      //START EM Transaction TODO : Remove when shared transaction !!!

      /* Store MsiSearch and retrieve persisted ORM entity */
      val tmpMsiSearchID = msiSearch.id
      val newMsiSearchID = storeMsiSearch(msiSearch, storerContext)

      // Save MSQueries 
      if (msQueries != null && !msQueries.isEmpty)
        storeMsQueries(newMsiSearchID, msQueries, storerContext)
    }

    resultSet.id = createResultSet(resultSet, storerContext)

    localPlWriter = oldPlWriter

    resultSet.id
  }

  def createResultSet(resultSet: ResultSet, context: StorerContext): Int

  /**
   * Use Constructor specified IPeaklistWriter
   *
   */
  def storeSpectra(peaklistId: Int, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext = {
    localPlWriter.storeSpectra(peaklistId, peaklistContainer, context)
  }

  /**
   * Use Constructor specified IPeaklistWriter
   *
   */
  def storePeaklist(peaklist: Peaklist, context: StorerContext): Int = {
    localPlWriter.storePeaklist(peaklist, context)
  }

  def insertInstrumentConfig(instrumCfg: InstrumentConfig, context: StorerContext) = {
    require(instrumCfg.id > 0, "Instrument configuration must have a strictly positive identifier")

    val jdbcWork = JDBCWorkBuilder.withEzDBC( context.getMSIDbConnectionContext.getDriverType, { msiEzDBC =>
      
      // Check if the instrument config exists in the MSIdb
      val count = msiEzDBC.selectInt("SELECT count(*) FROM instrument_config WHERE id=" + instrumCfg.id)

      // If the instrument config doesn't exist in the MSIdb
      if (count == 0) {
        msiEzDBC.executePrepared("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
          stmt.executeWith(
            instrumCfg.id,
            instrumCfg.name,
            instrumCfg.ms1Analyzer,
            Option(instrumCfg.msnAnalyzer),
            Option.empty[String])
        }
      }

    })
    
    context.getMSIDbConnectionContext.doWork(jdbcWork, true)
  }

}