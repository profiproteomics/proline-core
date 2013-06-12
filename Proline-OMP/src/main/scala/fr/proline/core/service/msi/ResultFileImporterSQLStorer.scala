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
import fr.proline.core.om.provider.msi.impl.SQLPeaklistSoftwareProvider
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.repository.DriverType

@deprecated("0.1.0","use ResultFileImporter instead")
class ResultFileImporterSQLStorer(
  executionContext: IExecutionContext,
  resultIdentFile: File,
  fileType: String,
  instrumentConfigId: Int,
  peaklistSoftwareId: Int,
  importerProperties: Map[String, Any],
  acDecoyRegex: Option[util.matching.Regex] = None,
  saveSpectrumMatch: Boolean = false) extends IService with Logging {

  private var _hasInitiatedStorerContext: Boolean = false

  // Secondary constructor
  /*def this(dbManager: IDataStoreConnectorFactory,
           projectId: Int,
           resultIdentFile: File,
           fileType: String,
           instrumentConfigId: Int,
           peaklistSoftwareId: Int,
           importerProperties: Map[String, Any],
           acDecoyRegex: Option[util.matching.Regex] = None) {
    this(
      ContextFactory.buildExecutionContext(dbManager, projectId, false), // Force SQL context
      resultIdentFile,
      fileType,
      instrumentConfigId,
      peaklistSoftwareId,
      importerProperties,
      acDecoyRegex
    )
    _hasInitiatedStorerContext = true
  }*/

  //val wasMsiConnectionOpened = storerContext.isMsiDbConnectionOpened
  //val wasPsConnectionOpened = storerContext.isPsConnectionOpened

  private var targetResultSetId: Long = 0L

  override protected def beforeInterruption = {
    // Release database connections
    //this.logger.info("releasing database connections before service interruption...")
  }

  def getTargetResultSetId = targetResultSetId

  def runService(): Boolean = {
    
    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")
    
    logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
    >>>

    val msiDbCtx = executionContext.getMSIDbConnectionContext
    var serviceResultOK: Boolean = true

    // Check if a transaction is already initiated
    val wasInTransaction = msiDbCtx.isInTransaction
    if (!wasInTransaction) msiDbCtx.beginTransaction()

    // Get Right ResultFile provider
    val rfProviderOpt = ResultFileProviderRegistry.get(fileType)
    require(rfProviderOpt != None, "No ResultFileProvider for specified identification file format")

    // Open the result file
    /* Wrap ExecutionContext in ProviderDecoratedExecutionContext for Parser service use */
    val parserContext = if (executionContext.isInstanceOf[ProviderDecoratedExecutionContext]) {
      executionContext.asInstanceOf[ProviderDecoratedExecutionContext]
    } else {
      new ProviderDecoratedExecutionContext(executionContext)
    }

    val resultFile = rfProviderOpt.get.getResultFile(resultIdentFile, importerProperties, parserContext)
    >>>

    // --- Configure result file before parsing ---
    
    // Retrieve the instrument configuration
    resultFile.instrumentConfig = Some(this._getInstrumentConfig(instrumentConfigId))

    // Retrieve the peaklist software if needed
    if( resultFile.peaklistSoftware.isEmpty ) {

      val peaklistSoftware = _getOrCreatePeaklistSoftware(peaklistSoftwareId)
      if (peaklistSoftware.id < 0) peaklistSoftware.id = peaklistSoftwareId
      
      resultFile.peaklistSoftware = Some(peaklistSoftware)
    }

    logger.debug("Starting RSStorer work")
    
    // Instantiate RsStorer
    val rsStorer: IRsStorer = RsStorer(executionContext.getMSIDbConnectionContext)
    //val rsStorer = new SQLRsStorer(new SQLiteRsWriter, new SQLPeaklistWriter) 

    /* Wrap ExecutionContext in StorerContext for RSStorer service use */
    val storerContext = if (executionContext.isInstanceOf[StorerContext]) {
      executionContext.asInstanceOf[StorerContext]
    } else {
      new StorerContext(executionContext)
    }

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
      !executionContext.isJPA,
      tdMode,
      acDecoyRegex,
      saveSpectrumMatch,
      if( acDecoyRegex != None ) Some(TargetDecoyResultSetSplitter) else None        
    )

    >>>

    if (!wasInTransaction) msiDbCtx.commitTransaction
    logger.debug("End of result file importer service")

    if (this._hasInitiatedStorerContext) {
      executionContext.closeAll()
    }

    serviceResultOK
  }

  private def _getOrCreatePeaklistSoftware(peaklistSoftwareId: Int): PeaklistSoftware = {
    
    val msiDbCtx = this.executionContext.getMSIDbConnectionContext
    val msiPklSoftProvider = new SQLPeaklistSoftwareProvider(msiDbCtx)

    // Try to retrieve peaklist software from the MSidb
    var pklSoftOpt = msiPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
    if (pklSoftOpt.isEmpty) {
      
      val udsPklSoftProvider = new SQLPeaklistSoftwareProvider(this.executionContext.getUDSDbConnectionContext)

      // If it doesn't exist => retrieve from the UDSdb
      pklSoftOpt = udsPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
      require(pklSoftOpt.isDefined,"can't find a peaklist software for id = " + peaklistSoftwareId)
      
      val pklSoft = pklSoftOpt.get

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

    pklSoftOpt.get

  }

  private def _getInstrumentConfig(instrumentConfigId: Int): InstrumentConfig = {

    val instConfigProvider = new SQLInstrumentConfigProvider(executionContext.getUDSDbConnectionContext)
    instConfigProvider.getInstrumentConfig(instrumentConfigId).get

  }

}