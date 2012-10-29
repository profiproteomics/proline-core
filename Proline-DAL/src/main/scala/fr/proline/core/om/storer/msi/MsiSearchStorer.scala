package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.MSISearch
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.om.storer.msi.impl.StorerContext

trait IMsiSearchStorer {
  
  def storeMsiSearch( msiSearch: MSISearch, context: StorerContext ): Int
  
  /**
   * Insert definition of InstrumentConfig (which should exist in uds) in current MSI db if not already defined
   * Transaction are not managed by this method, should be done by user.
   */
  def insertInstrumentConfig( instrumentConfig: InstrumentConfig, context: StorerContext ): Unit
  
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
  def storeMsQueries( msiSearchId: Int, msQueries: Seq[MsQuery], context: StorerContext ): StorerContext
}

/** A factory object for implementations of the IMsiSearchStorer trait */
object MsiSearchStorer {
  
  import fr.proline.core.om.storer.msi.impl.PgMsiSearchStorer
  import fr.proline.core.om.storer.msi.impl.SQLiteMsiSearchStorer

  def apply( msiDb: MsiDb ): IMsiSearchStorer = { msiDb.config.driver match {
    case "org.postgresql.Driver" => new PgMsiSearchStorer( msiDb )
    case "org.sqlite.JDBC" => new SQLiteMsiSearchStorer( msiDb )
    //case _ => 
    }
  }
  
}


