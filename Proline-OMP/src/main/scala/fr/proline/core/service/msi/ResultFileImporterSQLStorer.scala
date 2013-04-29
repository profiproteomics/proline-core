package fr.proline.core.service.msi

import java.io.File

import com.weiglewilczek.slf4s.Logging

import fr.profi.jdbc.easy.{EasyDBC, int2Formattable, row2Int, row2String, string2Formattable}
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.proline.core.dal.{ContextFactory, ProlineEzDBC}
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.om.model.msi.{InstrumentConfig, PeaklistSoftware, ResultSet, ResultSetProperties, SearchSettingsProperties}
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.ResultFileProviderRegistry
import fr.proline.core.om.provider.msi.impl.SQLInstrumentConfigProvider
import fr.proline.core.om.storer.msi.{IRsStorer, RsStorer}
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.util.StringUtils

class ResultFileImporterSQLStorer(
  executionContext: IExecutionContext,
  resultIdentFile: File,
  fileType: String,
  instrumentConfigId: Int,
  peaklistSoftwareId: Int,
  importerProperties: Map[String, Any],
  acDecoyRegex: Option[util.matching.Regex] = None) extends IService with Logging {

  private var _hasInitiatedStorerContext: Boolean = false

  // Secondary constructor
  def this(dbManager: IDataStoreConnectorFactory,
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
  }

  //val wasMsiConnectionOpened = storerContext.isMsiDbConnectionOpened
  //val wasPsConnectionOpened = storerContext.isPsConnectionOpened

  private var targetResultSetId = 0
  private val udsEzDBC = ProlineEzDBC(executionContext.getUDSDbConnectionContext)

  override protected def beforeInterruption = {
    // Release database connections
    this.logger.info("releasing database connections before service interruption...")
    //    this.msiDb.closeConnection()
    //this.udsEzDBC.connection.close()
    //storerContext.closeAll()

    //if( !wasMsiConnectionOpened ) storerContext.msiDbConnection.close()
    //if( !wasPsConnectionOpened ) storerContext.psDbConnection.close()
    if (_hasInitiatedStorerContext) executionContext.closeAll()
  }

  def getTargetResultSetId = targetResultSetId

  def runService(): Boolean = {

    var serviceResultOK: Boolean = true

    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")
    logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
    >>>

    val msiEzDBC = ProlineEzDBC(executionContext.getMSIDbConnectionContext)

    // Check if a transaction is already initiated
    val wasInTransaction = msiEzDBC.isInTransaction
    if (!wasInTransaction) msiEzDBC.beginTransaction()

    // Retrieve the instrument configuration
    val instrumentConfig = this._getInstrumentConfig(instrumentConfigId)

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

    // Instantiate RsStorer
    val rsStorer: IRsStorer = RsStorer(executionContext.getMSIDbConnectionContext)
    //val rsStorer = new SQLRsStorer(new SQLiteRsWriter, new SQLPeaklistWriter) 

    // Configure result file before parsing
    resultFile.instrumentConfig = instrumentConfig

    //Fait par le Storer: Attente partage transaction TODO    
    //    val msiTransaction = stContext.msiEm.getTransaction
    //    var msiTransacOk: Boolean = false

    logger.debug("Starting RSStorer work")

    /* Wrap ExecutionContext in StorerContext for RSStorer service use */
    val storerContext = if (executionContext.isInstanceOf[StorerContext]) {
      executionContext.asInstanceOf[StorerContext]
    } else {
      new StorerContext(executionContext)
    }

    //Start MSI Transaction and ResultSets store

    // Insert instrument config in the MSIdb         
    rsStorer.insertInstrumentConfig(instrumentConfig, storerContext)

    // Retrieve MSISearch and related MS queries
    val msiSearch = resultFile.msiSearch
    val msQueryByInitialId = resultFile.msQueryByInitialId
    var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
    if (msQueryByInitialId != null) {
      msQueries = msQueryByInitialId.map { _._2 }.toList.sortWith { (a, b) => a.initialId < b.initialId }
    }

    // Load the peaklist software from the MSidb if it is only an in memory object
    if (msiSearch.peakList.peaklistSoftware == null || msiSearch.peakList.peaklistSoftware.id <= 0)
      msiSearch.peakList.peaklistSoftware = this._getOrCreatePeaklistSoftware(peaklistSoftwareId, msiEzDBC)

    /*if(msiSearch != null && msiSearch.peakList.peaklistSoftware == null ){
        //TODO : Define how to get this information !
        msiSearch.peakList.peaklistSoftware = _getPeaklistSoftware("Default_PL","0.1" )
      } */

    //val driverType = msiDbConnector.getDriverType()
    //if( driverType.getDriverClassName == "org.sqlite.JDBC" ) {

    // Store the peaklist    
    val spectrumIdByTitle = rsStorer.storePeaklist(msiSearch.peakList, storerContext)
    rsStorer.storeSpectra(msiSearch.peakList.id, resultFile, storerContext)
    >>>

    // Load target result set
    var targetRs = resultFile.getResultSet(false)
    if (StringUtils.isEmpty(targetRs.name)) targetRs.name = msiSearch.title

    // Update result set properties
    val rsProps = targetRs.properties.getOrElse(new ResultSetProperties)

    if (resultFile.hasDecoyResultSet) {
      // FIXME: We assume separated searches, but do we need to set this information at the parsing step ???
      rsProps.setTargetDecoyMode(Some(TargetDecoyModes.SEPARATED.toString))
    } else if (acDecoyRegex != None) {
      rsProps.setTargetDecoyMode(Some(TargetDecoyModes.CONCATENATED.toString))
    }

    targetRs.properties = Some(rsProps)

    // FIXME: remove these 3 lines when we know that TargetDecoyMode is not needed at searchSettingsLevel
    val ssProps = targetRs.msiSearch.get.searchSettings.properties.getOrElse(new SearchSettingsProperties)
    ssProps.setTargetDecoyMode(rsProps.getTargetDecoyMode)
    msiSearch.searchSettings.properties = Some(ssProps)

    //Store the MSI search with related search settings and MS queries    
    rsStorer.storeMsiSearch(msiSearch, storerContext)
    rsStorer.storeMsQueries(msiSearch.id, msQueries, storerContext)
    >>>

    //-- VDS TODO: Identify decoy mode to get decoy RS from parser or to create it from target RS.

    def storeDecoyRs(decoyRs: ResultSet): ResultSet = {
      if (StringUtils.isEmpty(decoyRs.name))
        decoyRs.name = msiSearch.title

      //if( driverType.getDriverClassName == "org.sqlite.JDBC" )
      rsStorer.storeResultSet(decoyRs, storerContext)
      //else
      //  targetRs.decoyResultSet = Some(decoyRs)
      decoyRs
    }

    // Load and store decoy result set if it exists
    //var decoyRsId = Option.empty[Int]
    if (resultFile.hasDecoyResultSet) {
      val dRs = resultFile.getResultSet(true)
      targetRs.decoyResultSet = Some(storeDecoyRs(dRs))
    } // Else if a regex has been passed to detect decoy protein matches
    else if (acDecoyRegex != None) {
      // Then split the result set into a target and a decoy one
      val (tRs, dRs) = TargetDecoyResultSetSplitter.split(targetRs, acDecoyRegex.get)

      logger.debug {
        val targetRSId = if (tRs == null) { 0 } else { tRs.id }
        val decoyRSId = if (dRs == null) { 0 } else { dRs.id }

        "Temporary TARGET ResultSet Id: " + targetRSId + "  DECOY ResultSet id: " + decoyRSId
      }

      targetRs = tRs
      targetRs.decoyResultSet = Some(storeDecoyRs(dRs))

    } else targetRs.decoyResultSet = None
    >>>

    //  Store target result set
    this.targetResultSetId = rsStorer.storeResultSet(targetRs, storerContext)
    >>>

    //    this.msiDb.commitTransaction()// VD Pour SQLStorer Only
    //      msiTransaction.commit()
    //      msiTransacOk = true

    if (!wasInTransaction) msiEzDBC.commitTransaction
    logger.debug("End of result file importer service")

    this.beforeInterruption()

    true
    //    msiTransacOk   
  }

  private def _getPeaklistSoftware(plName: String, plRevision: String): PeaklistSoftware = {

    udsEzDBC.selectHeadOrElse(
      "SELECT * FROM peaklist_software WHERE name= ? and version= ? ", plName, plRevision)(r =>
        new PeaklistSoftware(id = r, name = r, version = r),
        new PeaklistSoftware(id = PeaklistSoftware.generateNewId, name = "Default", version = "0.1")
      )
  }

  private def _getOrCreatePeaklistSoftware(peaklistSoftwareId: Int, msiEzDBC: EasyDBC): PeaklistSoftware = {

    import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable

    def getPeaklistSoftware(ezDBC: EasyDBC): PeaklistSoftware = {

      var peaklistSoftware: PeaklistSoftware = null
      ezDBC.selectAndProcess("SELECT name, version FROM peaklist_software WHERE id=" + peaklistSoftwareId) { r =>
        peaklistSoftware = new PeaklistSoftware(id = peaklistSoftwareId, name = r, version = r.nextStringOrElse(""))
      }

      peaklistSoftware
    }

    // Try to retrieve peaklist software from the MSidb
    var peaklistSoftware = getPeaklistSoftware(msiEzDBC)
    if (peaklistSoftware == null) {

      // If it doesn't exist => retrieve from the UDSdb
      peaklistSoftware = getPeaklistSoftware(udsEzDBC)

      // Then insert it in the current MSIdb
      val peaklistInsertQuery = MsiDbPeaklistSoftwareTable.mkInsertQuery(c => List(c.ID, c.NAME, c.VERSION))
      msiEzDBC.execute(peaklistInsertQuery, peaklistSoftware.id, peaklistSoftware.name, peaklistSoftware.version)
    }

    peaklistSoftware

  }

  private def _getInstrumentConfig(instrumentConfigId: Int): InstrumentConfig = {

    val instConfigProvider = new SQLInstrumentConfigProvider(executionContext.getUDSDbConnectionContext)
    instConfigProvider.getInstrumentConfig(instrumentConfigId)

    /*
    import fr.proline.util.primitives._
    import fr.proline.core.om.model.msi.{ InstrumentProperties, InstrumentConfigProperties }

    // Load the instrument configuration record
    udsEzDBC.selectHead(
      "SELECT instrument.*,instrument_config.* FROM instrument,instrument_config " +
        "WHERE instrument.id = instrument_config.instrument_id AND instrument_config.id =" + instrumentConfigId) { r =>

        val instrument = new Instrument(id = toInt(r.nextAnyVal), name = r, source = r)
        for (instPropStr <- r.nextStringOption) {
          if (StringUtils.isEmpty(instPropStr) == false)
            instrument.properties = Some(parse[InstrumentProperties](instPropStr))
        }

        // Skip instrument_config.id field
        r.nextAny

        val instrumentConfig = new InstrumentConfig(
          id = instrumentConfigId,
          name = r.nextString,
          instrument = instrument,
          ms1Analyzer = r.nextString,
          msnAnalyzer = r.nextString,
          activationType = ""
        )
        for (instConfPropStr <- r.nextStringOption) {
          if (StringUtils.isEmpty(instConfPropStr) == false)
            instrumentConfig.properties = Some(parse[InstrumentConfigProperties](instConfPropStr))
        }

        // Skip instrument_config.instrument_id field
        r.nextAny

        // Update activation type
        instrumentConfig.activationType = r.nextString

        instrumentConfig
      }*/

  }

}