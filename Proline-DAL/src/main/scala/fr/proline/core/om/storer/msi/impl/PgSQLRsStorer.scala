package fr.proline.core.om.storer.msi.impl

import java.sql.Connection

import org.postgresql.copy.CopyIn
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection

import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.MsiDbMsQueryTable
import fr.proline.core.om.storer.msi.IRsWriter
import fr.proline.core.om.model.msi.Ms1Query
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.util.sql._

class PgSQLRsStorer(private val _storer: IRsWriter, private val _plWriter: IPeaklistWriter) extends SQLRsStorer(_storer, _plWriter) {

  // TODO: add to SQLRsStorer constructor instead ???
  override val msiSearchStorer = new PgMsiSearchStorer()

}