package fr.proline.core.om.storer.msi.impl

import fr.profi.jdbc.easy.{ int2Formattable, string2Formattable, stringOption2Formattable }
import fr.proline.core.dal.{ BuildJDBCWork, ContextFactory }
import fr.proline.core.om.model.msi.{ IResultFile, MsQuery, Peaklist, ResultSet }
import fr.proline.core.om.storer.msi.{ IPeaklistWriter, IRsStorer, PeaklistWriter }
import fr.proline.repository.IDataStoreConnectorFactory
import javax.persistence.EntityTransaction

abstract class AbstractRsStorer(val pklWriter: Option[IPeaklistWriter] = None) extends IRsStorer {

  // IPeaklistWriter to use to store PeakList and Spectrum 
  //var localPlWriter: IPeaklistWriter = plWriter

  type MsiResultSet = fr.proline.core.orm.msi.ResultSet
  
  /**
   * Create and persist ResultSet in repository, using storerContext for context and mapping information.
   * This method has to be implemented in concrete ResultSet storers.
   */
  protected def createResultSet(resultSet: ResultSet, context: StorerContext): Int
  
  /**
   * Store specified ResultSet in persistence repository, using storerContext for context and mapping information.
   * This default implementation of IRsStorer will call private method _storeResultSetmethod without specifying spectra or MsQueries
   * 
   * Transaction are not managed by this method, should be done by user.
   */
  final def storeResultSet(resultSet: ResultSet, storerContext: StorerContext): Int = {
    this._storeResultSet(resultSet, None, None, storerContext)    
  }

  /**
   * Store specified ResultSet in persistence repository, using storerContext for context and mapping information.
   * Some checks are performed then the private method _storeResultSet is executed to save the ResultSet.
   * 
   * Transaction are not managed by this method, should be done by user.
   * 
   * TODO: move to ResultFileStorer ???
   */
  final def storeResultSet(resultSet: ResultSet, msQueries: Seq[MsQuery], storerContext: StorerContext): Int = {

    require(resultSet.isNative, "only native result sets can be saved using this method")
    require(msQueries != null, "msQueries must not be null")
    //require(resultFile != null, "resultFile must not be null")
    require(resultSet.msiSearch.isDefined, "MSISearch must be defined")
    
    this._storeResultSet(resultSet, Some(msQueries), None, storerContext)
  }

  /**
   * This method will first save spectra and queries related data specified by peakListContainer and msQueries.
   * This will be done using IRsStorer storePeaklist, storeMsiSearch, storeMsQueries and storeSpectra methods.
   * 
   * Then the implementation of the abstract createResultSet method will be executed to save other data.
   * 
   * TODO: use other writers : peptideMatch / ProteinMatch
   *
   */
   final private def _storeResultSet(
    resultSet: ResultSet,
    msQueriesOpt: Option[Seq[MsQuery]],
    resultFileOpt: Option[IResultFile],
    storerContext: StorerContext
  ): Int = {
    
    require(resultSet != null, "resultSet must not be null")
    require(storerContext != null, "storerContext must not be null")
    
    val omResultSetId = resultSet.id

    if (omResultSetId > 0) {
      throw new UnsupportedOperationException("Updating a ResultSet is not supported yet !")
    }

    logger.info("Storing ResultSet " + omResultSetId + "  [" + resultSet.name + "]") 
    
    // Save Spectra and Queries information (MSISearch should be defined)
    for( msiSearch <- resultSet.msiSearch ) {

      // FIXME: it should be only called in the ResultFile storer
      if( msiSearch.peakList.id < 0 ) {
      
        // Create a PeakListWriter if none was specified
        val pklWriter = this.getOrBuildPeaklistWriter(storerContext)
        
        // Insert the Peaklist information
        msiSearch.peakList.id = pklWriter.insertPeaklist(msiSearch.peakList, storerContext)
      }
      
      // Insert the MSI search  and retrieve its new id
      logger.info("storing MSI search...")
      val msiSearchId = this.storeMsiSearch(msiSearch, storerContext)
      
      // Insert MS queries if they are provided
      for( msQueries <- msQueriesOpt if !msQueries.isEmpty ) {
        this.storeMsQueries(msiSearchId, msQueries, storerContext)
      }
    }

    resultSet.id = createResultSet(resultSet, storerContext)

    resultSet.id
    
  }  

  // TODO: remove me ???
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

      msiResultSetId = this.storeResultSet(
        resultSet = resultSet,
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

    msiResultSetId
  }

  /*
  /**
   * Use Constructor specified IPeaklistWriter
   *
   */
  def storeSpectra(peaklistId: Int, peaklistContainer: IResultFile, context: StorerContext): StorerContext = {
    localPlWriter.insertSpectra(peaklistId, peaklistContainer, context)
  }

  /**
   * Use Constructor specified IPeaklistWriter
   *
   */
  final def storePeaklist(peaklist: Peaklist, context: StorerContext): Int = {
    localPlWriter.insertPeaklist(peaklist, context)
  }*/



}