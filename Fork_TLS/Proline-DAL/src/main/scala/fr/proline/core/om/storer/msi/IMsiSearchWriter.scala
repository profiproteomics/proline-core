package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.LazyLogging

import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.MSISearch
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.om.storer.msi.impl.StorerContext

trait IMsiSearchWriter {

  def insertMsiSearch(msiSearch: MSISearch, context: StorerContext): Long

  /**
   * Insert definition of InstrumentConfig (which should exist in uds) in current MSI db if not already defined
   * Transaction are not managed by this method, should be done by user.
   */
  def insertInstrumentConfig(instrumentConfig: InstrumentConfig, context: StorerContext): Unit

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
  def insertMsQueries(msiSearchId: Long, msQueries: Seq[MsQuery], context: StorerContext): StorerContext
}

/** A factory object for implementations of the IMsiSearchStorer trait */
object MsiSearchWriter {

  import fr.proline.core.om.storer.msi.impl.PgMsiSearchWriter
  import fr.proline.core.om.storer.msi.impl.SQLMsiSearchWriter
  import fr.proline.repository.DriverType

  def apply(msiDbDriverType: DriverType): IMsiSearchWriter = {
    msiDbDriverType match {
      case DriverType.POSTGRESQL => PgMsiSearchWriter
      case _ => SQLMsiSearchWriter
    }
  }

}


