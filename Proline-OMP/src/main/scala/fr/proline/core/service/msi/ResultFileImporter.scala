package fr.proline.core.service.msi

import java.io.File

import com.codahale.jerkson.Json
import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultFileProvider
import fr.proline.core.om.provider.msi.ResultFileProviderRegistry
import fr.proline.core.om.provider.msi.impl.SQLInstrumentConfigProvider
import fr.proline.core.om.provider.msi.impl.{ SQLPeaklistSoftwareProvider => MsiSQLPklSoftProvider }
import fr.proline.core.om.provider.uds.impl.{ SQLPeaklistSoftwareProvider => UdsSQLPklSoftProvider }
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.repository.DriverType

class ResultFileImporter(
  executionContext: IExecutionContext,
  resultIdentFile: File,
  fileType: String,
  instrumentConfigId: Long,
  peaklistSoftwareId: Long,
  importerProperties: Map[String, Any],
  acDecoyRegex: Option[util.matching.Regex] = None,
  saveSpectrumMatch: Boolean = false) extends IService with Logging {

  private var targetResultSetId: Long = 0L

  override protected def beforeInterruption = {
    // Release database connections
    //this.logger.info("releasing database connections before service interruption...")
  }

  def getTargetResultSetId = targetResultSetId

  def runService(): Boolean = {

    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")

    logger.info("Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())

    val msiDbCtx = executionContext.getMSIDbConnectionContext
    var storerContext: StorerContext = null
    val rsStorer = RsStorer(msiDbCtx)
    var localMSITransaction: Boolean = false
    var msiTransacOk: Boolean = false

    try {

      // Check if a transaction is already initiated
      if (!msiDbCtx.isInTransaction) {
        msiDbCtx.beginTransaction()
        localMSITransaction = true
        msiTransacOk = false
      }

      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider != None, "No ResultFileProvider for specified identification file format")

      val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

      // Open the result file
      val resultFile = rfProvider.get.getResultFile(resultIdentFile, importerProperties, parserContext)
      >>>

      // --- Configure result file before parsing ---

      // Retrieve the instrument configuration
      val instConfigProvider = new SQLInstrumentConfigProvider(executionContext.getUDSDbConnectionContext)
      resultFile.instrumentConfig = instConfigProvider.getInstrumentConfig(instrumentConfigId)

      // Retrieve the peaklist software if needed
      if (resultFile.peaklistSoftware.isEmpty) {

        val peaklistSoftware = _getOrCreatePeaklistSoftware(peaklistSoftwareId)
        if (peaklistSoftware.id < 0) peaklistSoftware.id = peaklistSoftwareId

        resultFile.peaklistSoftware = Some(peaklistSoftware)
      }
      >>>

      storerContext = StorerContext(executionContext) // Use Object factory

      val tdMode = if (resultFile.hasDecoyResultSet) {
        // FIXME: We assume separated searches, but do we need to set this information at the parsing step ???
        Some(TargetDecoyModes.SEPARATED.toString)
      } else if (acDecoyRegex != None) {
        Some(TargetDecoyModes.CONCATENATED.toString)
      } else
        None

      // Call the result file storer
      this.targetResultSetId = ResultFileStorer.storeResultFile(
        storerContext,
        rsStorer,
        resultFile,
        !executionContext.isJPA, // SQL compat => FIXME: remove me when ResultFileStorer has the same behavior for JPA/SQL
        tdMode,
        acDecoyRegex,
        saveSpectrumMatch,
        if (acDecoyRegex != None) Some(TargetDecoyResultSetSplitter) else None
      )

      >>>

      // Commit transaction if it was initiated locally
      if (localMSITransaction) {
        msiDbCtx.commitTransaction()
      }

      msiTransacOk = true
    } finally {

      if (storerContext != null) {
        storerContext.clearContext()
      }

      if (localMSITransaction && !msiTransacOk) {
        logger.info("Rollbacking MSI Db Transaction")

        try {
          msiDbCtx.rollbackTransaction()
        } catch {
          case ex: Exception => logger.error("Error rollbacking MSI Db Transaction", ex)
        }

      }

      // Execution context should be closed by the service caller
      //executionContext.closeAll()

    }

    this.beforeInterruption()

    logger.debug("End of result file importer service")

    msiTransacOk
  }

  private def _getOrCreatePeaklistSoftware(peaklistSoftwareId: Long): PeaklistSoftware = {

    val msiDbCtx = this.executionContext.getMSIDbConnectionContext
    val msiPklSoftProvider = new MsiSQLPklSoftProvider(msiDbCtx)
    val udsPklSoftProvider = new UdsSQLPklSoftProvider(this.executionContext.getUDSDbConnectionContext)

    val udsPklSoftOpt = udsPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
    require(udsPklSoftOpt.isDefined, "can't find a peaklist software for id = " + peaklistSoftwareId)

    // Try to retrieve peaklist software from the MSidb
    var msiPklSoftOpt = msiPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
    if (msiPklSoftOpt.isEmpty) {

      // If it doesn't exist => retrieve from the UDSdb      
      val pklSoft = udsPklSoftOpt.get

      // Then insert it in the current MSIdb
      DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>
        val peaklistInsertQuery = MsiDbPeaklistSoftwareTable.mkInsertQuery
        msiEzDBC.execute(
          peaklistInsertQuery,
          pklSoft.id,
          pklSoft.name,
          pklSoft.version,
          pklSoft.properties.map(Json.generate(_))
        )
      })
    }

    udsPklSoftOpt.get
  }

}
