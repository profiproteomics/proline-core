package fr.proline.core.om.storer.msi.impl

import fr.profi.jdbc.easy.{ int2Formattable, string2Formattable, stringOption2Formattable }
import fr.proline.core.dal.{ BuildJDBCWork, ContextFactory }
import fr.proline.core.om.model.msi.{ IPeaklistContainer, InstrumentConfig, MsQuery, Peaklist, ResultSet }
import fr.proline.core.om.storer.msi.{ IPeaklistWriter, IRsStorer, PeaklistWriter }
import fr.proline.repository.IDataStoreConnectorFactory
import javax.persistence.EntityTransaction

abstract class AbstractRsStorer(val plWriter: IPeaklistWriter = null) extends IRsStorer {

  // IPeaklistWriter to use to store PeakList and Spectrum 
  var localPlWriter: IPeaklistWriter = plWriter

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

  def storeResultSet(resultSet: ResultSet, dbManager: IDataStoreConnectorFactory, projectId: Int): Int = {

    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }

    if (dbManager == null) {
      throw new IllegalArgumentException("DbManager is null")
    }

    var msiResultSetId: Int = -1
    var storerContext: StorerContext = null // For JPA use
    var msiTransaction: EntityTransaction = null
    var msiTransacOk: Boolean = false

    try {
      storerContext = new StorerContext(ContextFactory.buildExecutionContext(dbManager, projectId, true))

      val msiDb = storerContext.getMSIDbConnectionContext
      val msiEm = msiDb.getEntityManager()

      // Begin transaction
      msiTransaction = msiEm.getTransaction
      msiTransaction.begin()
      msiTransacOk = false

      msiResultSetId = storeResultSet(
        resultSet = resultSet,
        msQueries = null,
        peakListContainer = null,
        storerContext = storerContext)

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

    msiResultSetId
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

    val omResultSetId = resultSet.id

    if (omResultSetId > 0) {
      throw new UnsupportedOperationException("Updating a ResultSet is not supported yet !")
    }

    logger.info("Storing ResultSet " + omResultSetId + "  [" + resultSet.name + ']')

    /* Create a StorerContext if none was specified */

    val oldPlWriter = localPlWriter

    if (oldPlWriter == null) {
      localPlWriter = PeaklistWriter(storerContext.getMSIDbConnectionContext.getDriverType)
    }

    // Save Spectra and Queries information (MSISearch should  be defined)
    val msiSearchOpt = resultSet.msiSearch

    for( msiSearch <- msiSearchOpt ) {
      
      // Save Peaklist information
      val peakList = msiSearch.peakList
      val msiPeaklistId = storePeaklist(peakList, storerContext)

      peakList.id = msiPeaklistId // Update OM entity with persisted Primary key

      // Save spectra retrieve by peakListContainer 
      if (peakListContainer != null) {
        storeSpectra(msiPeaklistId, peakListContainer, storerContext)
      }

      //TODO : Remove when shared transaction !!! : Close msiDB connection if used by previous store methods 

      //START EM Transaction TODO : Remove when shared transaction !!!

      /* Store MsiSearch and retrieve persisted ORM entity */

      val msiSearchId = storeMsiSearch(msiSearch, storerContext)

      // Save MSQueries 
      if ((msQueries != null) && !msQueries.isEmpty) {
        storeMsQueries(msiSearchId, msQueries, storerContext)
      }

    }

    val msiResultSetPK = createResultSet(resultSet, storerContext)

    resultSet.id = msiResultSetPK

    /* Restore oldPlWriter */
    localPlWriter = oldPlWriter

    msiResultSetPK
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
  final def storePeaklist(peaklist: Peaklist, context: StorerContext): Int = {
    localPlWriter.storePeaklist(peaklist, context)
  }

  def insertInstrumentConfig(instrumCfg: InstrumentConfig, context: StorerContext) = {
    require(instrumCfg.id > 0, "Instrument configuration must have a strictly positive identifier")

    val jdbcWork = BuildJDBCWork.withEzDBC(context.getMSIDbConnectionContext.getDriverType, { msiEzDBC =>

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
