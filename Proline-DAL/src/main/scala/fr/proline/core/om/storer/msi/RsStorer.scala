package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.{MsiDb, DatabaseManagement}
import fr.proline.core.om.model.msi.{ResultSet, Peaklist, MsQuery, MSISearch, InstrumentConfig, IPeaklistContainer}
import fr.proline.core.om.storer.msi.impl.{StorerContext, SQLiteRsWriter, SQLRsStorer, PgRsWriter, JPARsStorer}
import fr.proline.repository.DatabaseConnector


trait IRsStorer extends Logging {
  
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
  def storeResultSet(resultSet : ResultSet, context : StorerContext) : Int 
    
  /**
   * Store, in persistence repository, specified ResultSet and associated data.
   * Specified data should have already been updated : temporary id on associated data means should be saved.
   * Transaction are not managed by this method, should be done by user.
   *  
   * @param resultSet : ResultSet to store
   * @return ID of stored ResultSet 
   * @exception Exception if an error occur while saving data
   */
  def storeResultSet( resultSet: ResultSet ) : Int  
 
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
  def storeResultSet(resultSet : ResultSet, msQueries : Seq [MsQuery], peakListContainer : IPeaklistContainer, context : StorerContext) : Int
  
  /**
   * Store PeakList and associated software, if necessary,  in repository 
   * Transaction are not managed by this method, should be done by user.
   * 
   * @param peaklist : Peaklist to save 
   * @return Id of the saved PeakList
   */  
  def storePeaklist(peaklist: Peaklist, context : StorerContext):Int
  
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
  def storeSpectra( peaklistId: Int, peaklistContainer: IPeaklistContainer, context : StorerContext ): StorerContext
 
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
  def storeMsiSearch(msiSearch : MSISearch, context : StorerContext) : Int
  
  
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
  def storeMsQueries(msiSearchID : Int, msQueries : Seq[MsQuery], context : StorerContext) : StorerContext
  
  /**
   * Insert definition of InstrumentConfig (which should exist in uds) in current MSI db if not already defined
   * Transaction are not managed by this method, should be done by user.
   */
  def insertInstrumentConfig(instrumCfg : InstrumentConfig, context : StorerContext) 
  
}

/** A factory object for implementations of the IRsStorer trait */
object RsStorer {
  
  //TODO : Define common algo for JPA & SQL! 
  
  import fr.proline.core.om.storer.msi.impl.GenericRsWriter
  import fr.proline.core.om.storer.msi.impl.PgRsWriter
  import fr.proline.core.om.storer.msi.impl.SQLiteRsWriter

  def apply(dbMgmt: DatabaseManagement, msiDb: MsiDb ): IRsStorer = {
    
    /*if(msiDb == null)
      new JPARsStorer( dbMgmt, null, null) //Call JPARsStorer
    */
    //val dbMgmt = stContext.dbManagement
    //val msiDb = stContext.msiDB
    
    msiDb.config.driver match {
    	case "org.postgresql.Driver" => new SQLRsStorer( dbMgmt, new PgRsWriter( msiDb ), IPeaklistWriter.apply(msiDb.config.driver.toString) )
    	case "org.sqlite.JDBC" => new SQLRsStorer( dbMgmt, new SQLiteRsWriter(msiDb ), IPeaklistWriter.apply(msiDb.config.driver.toString))
//    case _ => new RsStorer( dbMgmt, new GenericRsWriter(msiDb )) 
      case _ => new JPARsStorer( dbMgmt, msiDb.dbConnector,IPeaklistWriter.apply(msiDb.config.driver.toString)) //Call JPARsStorer
    }
  }
  
  def apply(dbMgmt: DatabaseManagement, projectID: Int): IRsStorer = {
    val msiDbConnector = dbMgmt.getMSIDatabaseConnector(projectID,false)
    if(msiDbConnector == null)
      new JPARsStorer( dbMgmt, msiDbConnector, null) //Call JPARsStorer
    
    val driverName = msiDbConnector.getProperty(DatabaseConnector.PROPERTY_DRIVERCLASSNAME) 
    driverName match {
    	case "org.postgresql.Driver" => new SQLRsStorer( dbMgmt, new PgRsWriter( new MsiDb(MsiDb.buildConfigFromDatabaseConnector(msiDbConnector))), IPeaklistWriter.apply(driverName) )
    	case "org.sqlite.JDBC" => new SQLRsStorer( dbMgmt, new SQLiteRsWriter( new MsiDb(MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) ) ), IPeaklistWriter.apply(driverName))
    	case _ => new JPARsStorer( dbMgmt, dbMgmt.getMSIDatabaseConnector(projectID, false), IPeaklistWriter.apply(driverName)) //Call JPARsStorer    
    }
  }
  
}
