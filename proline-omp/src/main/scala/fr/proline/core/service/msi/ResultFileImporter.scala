package fr.proline.core.service.msi

import java.io.File

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.om.model.msi.FragmentationRuleSet
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultFileProvider
import fr.proline.core.om.provider.msi.ResultFileProviderRegistry
import fr.proline.core.om.provider.msi.impl.SQLFragmentationRuleProvider
import fr.proline.core.om.provider.msi.impl.SQLInstrumentConfigProvider
import fr.proline.core.om.provider.msi.impl.{SQLPeaklistSoftwareProvider => MsiSQLPklSoftProvider}
import fr.proline.core.om.provider.uds.impl.{SQLPeaklistSoftwareProvider => UdsSQLPklSoftProvider}
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext

class ResultFileImporter(
  executionContext: IExecutionContext,
  resultIdentFile: File,
  fileType: String,
  instrumentConfigId: Long,
  peaklistSoftwareId: Long,
  importerProperties: Map[String, Any],
  acDecoyRegex: Option[util.matching.Regex] = None,
  storeSpectraData: Boolean = true,
  storeSpectrumMatches: Boolean = false,
  useJpaStorer: Boolean = false, // use of SQLRsStorer by default
  fragmentationRuleSetId : Option[Long] = None
) extends IService with LazyLogging {

  private var targetResultSetId: Long = 0L
  private var targetResultSet :Option[ResultSet] = None

  private var resultFile: IResultFile = null

  override protected def beforeInterruption = {
    // Close result file if needed
    this.logger.info("releasing result file before service interruption...")
    if(resultFile != null)
      resultFile.close()
  }

  def getTargetResultSetId : Long = targetResultSetId
  def getTargetResultSetOpt : Option[ResultSet] = targetResultSet

  def runService(): Boolean = {

    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")

    logger.info("Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile)

    val msiDbCtx = executionContext.getMSIDbConnectionContext
    var storerContext: StorerContext = null
    val rsStorer = RsStorer(msiDbCtx, useJPA = useJpaStorer)
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
      require(rfProvider.isDefined, "No ResultFileProvider for specified identification file format")

      val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

      // Open the result file
      resultFile = rfProvider.get.getResultFile(resultIdentFile, importerProperties, parserContext)
      executeOnProgress() //execute registered action during progress

      // --- Configure result file before parsing ---
      // Retrieve the instrument configuration
      val instConfigProvider = new SQLInstrumentConfigProvider(executionContext.getUDSDbConnectionContext)
      resultFile.instrumentConfig = instConfigProvider.getInstrumentConfig(instrumentConfigId)

      // Retrieve the fragmentationRuleSet
      val fragRuleSetOpt: Option[FragmentationRuleSet] = if (fragmentationRuleSetId.isDefined) {
        val fragmentationRulesetProvider: SQLFragmentationRuleProvider = new SQLFragmentationRuleProvider(executionContext.getUDSDbConnectionContext)
        fragmentationRulesetProvider.getFragmentationRuleSet(fragmentationRuleSetId.get)
      }  else None
      resultFile.fragmentationRuleSet = fragRuleSetOpt

      // Retrieve the peaklist software if needed
      if (resultFile.peaklistSoftware.isEmpty) {

        val peaklistSoftware = _getOrCreatePeaklistSoftware(peaklistSoftwareId)
        if (peaklistSoftware.id < 0) peaklistSoftware.id = peaklistSoftwareId

        resultFile.peaklistSoftware = Some(peaklistSoftware)
      }
      executeOnProgress() //execute registered action during progress

      //--- READ FILE BEFORE STORE
      resultFile.parseResultSet(false) //read target
      if(resultFile.hasDecoyResultSet)
        resultFile.parseResultSet(true)
      executeOnProgress() //execute registered action during progress

      storerContext = StorerContext(executionContext) // Use Object factory

      val tdMode = if (resultFile.hasDecoyResultSet) {
        // FIXME: We assume separated searches, but do we need to set this information at the parsing step ???
        Some(TargetDecoyModes.SEPARATED.toString)
      } else if (acDecoyRegex.isDefined) {
        Some(TargetDecoyModes.CONCATENATED.toString)
      } else
        None

      // Call the result file storer
      val storedRS = ResultFileStorer.storeResultFile(
        storerContext,
        rsStorer,
        resultFile,
        !executionContext.isJPA, // SQL compat => FIXME: remove me when ResultFileStorer has the same behavior for JPA/SQL
        tdMode,
        acDecoyRegex,
        storeSpectraData,
        storeSpectrumMatches,
        if (acDecoyRegex.isDefined) Some(TargetDecoyResultSetSplitter) else None
      )
      this.targetResultSetId = storedRS.id
      targetResultSet = Some(storedRS)

      executeOnProgress() //execute registered action during progress

      // Commit transaction if it was initiated locally
      if (localMSITransaction) {
        msiDbCtx.commitTransaction()
      }

      msiTransacOk = true
    } catch {
      
      case t: Throwable => {
        logger.error("Error while importing resultFile", t)
        throw t
      }
      
    }finally {

      if (resultFile != null) {

        try {
          resultFile.close()
        } catch {
          case ex: Exception => logger.error("Error closing resultFile", ex)
        }

      }

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
    val msiPklSoftOpt = msiPklSoftProvider.getPeaklistSoftware(peaklistSoftwareId)
    if (msiPklSoftOpt.isEmpty) {

      // If it doesn't exist => retrieve from the UDSdb      
      val pklSoft = udsPklSoftOpt.get

      // Then insert it in the current MSIdb
      DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
        val peaklistInsertQuery = MsiDbPeaklistSoftwareTable.mkInsertQuery()
        msiEzDBC.execute(
          peaklistInsertQuery,
          pklSoft.id,
          pklSoft.name,
          pklSoft.version,
          pklSoft.properties.map(ProfiJson.serialize(_))
        )
      }
    }

    udsPklSoftOpt.get
  }

}
