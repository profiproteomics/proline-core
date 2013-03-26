package fr.proline.core.service.msi

import java.io.File
import javax.persistence.EntityTransaction
import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.{ IResultFileProvider, ResultFileProviderRegistry }
import fr.proline.core.om.storer.msi.{ IRsStorer, MsiSearchStorer, RsStorer }
import fr.proline.core.om.storer.msi.impl.{ JPARsStorer, StorerContext }
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.repository.IDatabaseConnector
import fr.proline.context.DatabaseConnectionContext
import fr.proline.util.StringUtils
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.algo.msi.validation.TargetDecoyModes

class ResultFileImporterJPAStorer(
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
      ContextFactory.buildExecutionContext(dbManager, projectId, true), // Force JPA context
      resultIdentFile,
      fileType,
      instrumentConfigId,
      peaklistSoftwareId,
      importerProperties,
      acDecoyRegex)
    _hasInitiatedStorerContext = true
  }

  private var targetResultSetId = 0

  override protected def beforeInterruption = {
    // Release database connections
    //this.logger.info("releasing database connections before service interruption...")
  }

  def getTargetResultSetId = targetResultSetId

  def runService(): Boolean = {

    var serviceResultOK: Boolean = true

    // Check that a file is provided
    require(resultIdentFile != null, "ResultFileImporter service: No file specified.")

    //var storerContext: StorerContext = null // For JPA use

    var msiTransaction: EntityTransaction = null
    var msiTransacOk: Boolean = false

    try {
      //storerContext = StorerContextBuilder(dbManager,projectId,useJpa = true)

      val udsDbContext = executionContext.getUDSDbConnectionContext

      msiTransaction = executionContext.getMSIDbConnectionContext.getEntityManager.getTransaction
      msiTransaction.begin()
      msiTransacOk = false

      logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
      >>>

      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider != None, "No ResultFileProvider for specified identification file format")

      // Open the result file

      /* Wrap ExecutionContext in ProviderDecoratedExecutionContext for Parser service use */
      val parserContext = if (executionContext.isInstanceOf[ProviderDecoratedExecutionContext]) {
        executionContext.asInstanceOf[ProviderDecoratedExecutionContext]
      } else {
        new ProviderDecoratedExecutionContext(executionContext)
      }

      val resultFile = rfProvider.get.getResultFile(resultIdentFile, importerProperties, parserContext)
      >>>

      // Instantiate RsStorer   
      val rsStorer: IRsStorer = new JPARsStorer() //<> SQLStorer

      // Retrieve the instrument configuration
      val instrumentConfig = this._getInstrumentConfig(instrumentConfigId, udsDbContext)

      // Configure result file before parsing
      resultFile.instrumentConfig = instrumentConfig

      logger.debug("Starting JPA RSStorer work")

      /* Wrap ExecutionContext in StorerContext for RSStorer service use */
      val storerContext = if (executionContext.isInstanceOf[StorerContext]) {
        executionContext.asInstanceOf[StorerContext]
      } else {
        new StorerContext(executionContext)
      }

      rsStorer.insertInstrumentConfig(instrumentConfig, storerContext)

      // Retrieve MSISearch and related MS queries
      val msiSearch = resultFile.msiSearch
      val msQueryByInitialId = resultFile.msQueryByInitialId
      var msQueries: List[fr.proline.core.om.model.msi.MsQuery] = null
      if (msQueryByInitialId != null) {
        msQueries = msQueryByInitialId.map { _._2 }.toList.sort { (a, b) => a.initialId < b.initialId }
      }

      //Done by RsStorer <> SQLStorer
      //Store the MSI search with related search settings and MS queries ...
      // Store the peaklist  ... 

      // Load target result set
      var targetRs = resultFile.getResultSet(false)
      if (StringUtils.isEmpty(targetRs.name))
        targetRs.name = msiSearch.title

      if (targetRs.msiSearch != null && targetRs.msiSearch.peakList.peaklistSoftware == null) {
        //TODO : Define how to get this information !
        val peaklistSoftware = _getPeaklistSoftware("Default_PL", "0.1", udsDbContext)
        // FIXME: implement the method _getOrCreatePeaklistSoftware instead
        if (peaklistSoftware.id < 0) peaklistSoftware.id = peaklistSoftwareId
        targetRs.msiSearch.peakList.peaklistSoftware = peaklistSoftware
      }

      //-- VDS TODO: Identify decoy mode to get decoy RS from parser or to create it from target RS.

      def storeDecoyRs(decoyRs: ResultSet) {
        if (StringUtils.isEmpty(decoyRs.name))
          decoyRs.name = msiSearch.title

        //VDTESTif( msiDbConnector.getDriverType().getDriverClassName == "org.sqlite.JDBC" )
        //VDTEST          rsStorer.storeResultSet(decoyRs,this.stContext) //Comportement différent des implémentation RsStorer ?! 
        //VDTESTelse
        targetRs.decoyResultSet = Some(decoyRs)
      }
      
      val ssProps = targetRs.msiSearch.searchSettings.properties.getOrElse(new SearchSettingsProperties)

      // Load and store decoy result set if it exists
      if (resultFile.hasDecoyResultSet) {
        storeDecoyRs(resultFile.getResultSet(true))
        
        // Update search settings properties
        // FIXME: We assume separated searches, but do we need to set this information at the parsing step ???
        ssProps.setTargetDecoyMode(Some(TargetDecoyModes.SEPARATED.toString))
        targetRs.msiSearch.searchSettings.properties = Some(ssProps)
        >>>
      } // Else if a regex has been passed to detect decoy protein matches		  
      else if (acDecoyRegex != None) {
        // Then split the result set into a target and a decoy one
        val (tRs, dRs) = TargetDecoyResultSetSplitter.split(targetRs, acDecoyRegex.get)
        targetRs = tRs

        storeDecoyRs(dRs)
        
        // Update search settings properties
        ssProps.setTargetDecoyMode(Some(TargetDecoyModes.CONCATENATED.toString))
        targetRs.msiSearch.searchSettings.properties = Some(ssProps)
        
        >>>
      } else
        targetRs.decoyResultSet = None

      //  Store target result set
      this.targetResultSetId = rsStorer.storeResultSet(targetRs, msQueries, resultFile, storerContext)
      >>>

      //      msiTransaction.commit()
      //      msiTransacOk = true
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

      if (this._hasInitiatedStorerContext) {
        executionContext.closeAll()
      }

    }

    this.beforeInterruption()

    true
    //    msiTransacOk   
  }

  // TODO: put in a dedicated provider
  private def _getPeaklistSoftware(plName: String, plRevision: String, udsDbContext: DatabaseConnectionContext): PeaklistSoftware = {

    DoJDBCReturningWork.withEzDBC(udsDbContext, { udsEzDBC =>
      udsEzDBC.selectHeadOrElse(
        "SELECT * FROM peaklist_software WHERE name= ? and version= ? ", plName, plRevision)(r =>
          new PeaklistSoftware(id = r, name = r, version = r),
          new PeaklistSoftware(id = PeaklistSoftware.generateNewId, name = "Default", version = "0.1"))
    })

  }

  // TODO: put in a dedicated provider
  private def _getInstrumentConfig(instrumentConfigId: Int, udsDbContext: DatabaseConnectionContext): InstrumentConfig = {

    import fr.proline.util.primitives._
    import fr.proline.core.om.model.msi.{ InstrumentProperties, InstrumentConfigProperties }

    DoJDBCReturningWork.withEzDBC(udsDbContext, { udsEzDBC =>
      
      // Load the instrument configuration record
      udsEzDBC.selectHead(
        "SELECT instrument.*,instrument_config.* FROM instrument,instrument_config " +
        "WHERE instrument.id = instrument_config.instrument_id AND instrument_config.id =" + instrumentConfigId) { r =>

        val instrument = new Instrument(id = toInt(r.nextAnyVal), name = r, source = r)
        for (instPropStr <- r.nextStringOption) {
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
          activationType = "")
        for (instConfPropStr <- r.nextStringOption) {
          instrumentConfig.properties = Some(parse[InstrumentConfigProperties](instConfPropStr))
        }

        // Skip instrument_config.instrument_id field
        r.nextAny

        // Update activation type
        instrumentConfig.activationType = r.nextString

        instrumentConfig
      }

    })

  }

}