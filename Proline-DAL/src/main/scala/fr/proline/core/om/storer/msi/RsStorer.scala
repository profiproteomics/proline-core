package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.om.model.msi.{ ResultSet, Peaklist, MsQuery, MSISearch, InstrumentConfig, IPeaklistContainer }
import fr.proline.core.om.storer.msi.impl._
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.repository.IDatabaseConnector

trait IRsStorer extends Logging {

  def storeResultSet(resultSet: ResultSet, dbManager: IDataStoreConnectorFactory, projectId: Int): Int

  /**
   * Store in persistence repository specified ResultSet and associated data.
   * Specified data should have already been updated : temporary id on associated data means should be saved.
   * Transaction are not managed by this method, should be done by user.
   *
   * @param resultSet : ResultSet to store
   * @param context: StorerContext containing mapping and context information (EntityManager, IDs mapping...)
   * @return ID of stored ResultSet
   * @exception  Exception if an error occur while saving data
   */
  def storeResultSet(resultSet: ResultSet, context: StorerContext): Int

  /**
   * Store, in persistence repository, specified ResultSet and associated data.
   * Specified data should have already been updated : temporary id on associated data means should be saved.
   * Transaction are not managed by this method, should be done by user.
   *
   * @param resultSet : ResultSet to store
   * @param msQueries Queries to store and to associate to MSISearch referenced by specified ResultSet
   * @param peaklistContainer IPeaklistContainer fro which Spectrum will be retrieve
   * @param context: StorerContext containing mapping and context information (EntityManager, IDs mapping...). This object will be updated by created entity cache
   * @return Int : ID of created entity
   * @exception Exception if an error occur while saving data
   */
  def storeResultSet(resultSet: ResultSet, msQueries: Seq[MsQuery], peakListContainer: IPeaklistContainer, context: StorerContext): Int

  /**
   * Store PeakList and associated software, if necessary,  in repository
   * Transaction are not managed by this method, should be done by user.
   *
   * @param peaklist : Peaklist to save
   * @return Id of the saved PeakList
   */
  def storePeaklist(peaklist: Peaklist, context: StorerContext): Int

  /**
   * Store Spectra retrieve by specified IPeaklistContainer in repository.
   * Created spectra will be associated to specified PeakList ID, Spectra title mapped to their Id
   * will be stored in specified StorerContext.
   * Transaction are not managed by this method, should be done by user.
   *
   * @param peaklistId Peaklist to associate created spectrum to
   * @param peaklistContainer IPeaklistContainer fro which Spectrum will be retrieve
   * @param context StorerContext containing mapping and context information (EntityManager, IDs mapping...). . This object will be updated by created entity cache
   * @return StorerContext with updated references
   */
  def storeSpectra(peaklistId: Int, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext

  /**
   * Store specified MsiSearch and associated data if necessary (SearchSettings, Peaklist,...) in repository
   * Map between created data and temporary ones will be stores in StorerContext
   * Transaction are not managed by this method, should be done by user.
   *
   *  @param msiSearch MsiSearch to store
   *  @param context StorerContext where mapping will be saved and/or retrieve as well as repository connection information. This object will be updated by created entity cache
   *
   *  @return Int : Id of created MsiSearch
   */
  def storeMsiSearch(msiSearch: MSISearch, context: StorerContext): Int

  /**
   * Store specified queries in repository and associated them with specified MSISearch.
   * Map between created queries and temporary ones will be stores in StorerContext
   *
   * Transaction are not managed by this method, should be done by user.
   *
   * @param msiSearchID MSISearch to associate MSQuery to
   * @param msQueries Queries to store
   * @param StorerContext where mapping will be saved and/or retrieve as well as repository connexion information
   *
   * @return StorerContext with updated references
   *
   */
  def storeMsQueries(msiSearchID: Int, msQueries: Seq[MsQuery], context: StorerContext): StorerContext

  /**
   * Insert definition of InstrumentConfig (which should exist in uds) in current MSI db if not already defined
   * Transaction are not managed by this method, should be done by user.
   */
  def insertInstrumentConfig(instrumCfg: InstrumentConfig, context: StorerContext)

}

/** A factory object for implementations of the IRsStorer trait */
object RsStorer {

  //TODO : Define common algo for JPA & SQL! 

  //import fr.proline.core.om.storer.msi.impl.GenericRsWriter
  import fr.proline.core.om.storer.msi.impl.PgRsWriter
  import fr.proline.core.om.storer.msi.impl.SQLiteRsWriter
  import fr.proline.repository.DriverType

  def apply( msiDbDriverType: DriverType ): IRsStorer = {

    val plWriter = PeaklistWriter(msiDbDriverType)

    msiDbDriverType match {
      case DriverType.POSTGRESQL => {
        new PgSQLRsStorer(new PgRsWriter(), plWriter)
      }
      case DriverType.SQLITE => new SQLRsStorer(new SQLiteRsWriter(), plWriter)
      case _ => new JPARsStorer(plWriter) //Call JPARsStorer
    }

  }
  
}
