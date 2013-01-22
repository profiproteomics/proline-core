package fr.proline.core.service.msi

import java.io.File
import javax.persistence.EntityTransaction
import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.easy._
import fr.proline.api.service.IService
import fr.proline.core.algo.msi.TargetDecoyResultSetSplitter
import fr.proline.core.dal.JDBCWorkBuilder
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.{IResultFileProvider,ResultFileProviderRegistry}
//import fr.proline.core.om.provider.msi.impl.ResultFileProviderContext
import fr.proline.core.om.storer.msi.{ IRsStorer, MsiSearchStorer, RsStorer }
import fr.proline.core.om.storer.msi.impl.{JPARsStorer,StorerContext,StorerContextBuilder}
import fr.proline.core.orm.util.DatabaseManager
import fr.proline.repository.{DatabaseContext,IDatabaseConnector}
import fr.proline.util.StringUtils

class ResultFileImporterJPAStorer(
  storerContext: StorerContext,
  resultIdentFile: File,
  fileType: String,
  providerKey: String,
  instrumentConfigId: Int,
  peaklistSoftwareId: Int,
  importerProperties: Map[String, Any],
  acDecoyRegex: Option[util.matching.Regex] = None ) extends IService with Logging {

  private var _hasInitiatedStorerContext: Boolean = false
  
  // Secondary constructor
  def this( dbManager: DatabaseManager,
            msiDbConnector: IDatabaseConnector,
            resultIdentFile: File,
            fileType: String,
            providerKey: String,
            instrumentConfigId: Int,
            peaklistSoftwareId: Int,
            importerProperties: Map[String, Any],
            acDecoyRegex: Option[util.matching.Regex] = None ) {
    this(
      StorerContextBuilder( dbManager, msiDbConnector, useJpa = true ), // Force JPA context
      resultIdentFile,
      fileType,
      providerKey,
      instrumentConfigId,
      peaklistSoftwareId,
      importerProperties,
      acDecoyRegex
    )
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
      
      val udsDbContext = storerContext.udsDbContext

      msiTransaction = storerContext.msiDbContext.getEntityManager.getTransaction
      msiTransaction.begin()
      msiTransacOk = false

      logger.info(" Run service " + fileType + " ResultFileImporter on " + resultIdentFile.getAbsoluteFile())
      >>>

      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider != None, "No ResultFileProvider for specified identification file format")

      // Open the result file
      //val providerCtx = new ResultFileProviderContext(providerKey,storerContext.pdiDbContext,storerContext.psDbContext)
      val resultFile = rfProvider.get.getResultFile(resultIdentFile,importerProperties,providerKey)
      >>>

      // Instantiate RsStorer   
      val rsStorer: IRsStorer = new JPARsStorer() //<> SQLStorer
      
      // Retrieve the instrument configuration
      val instrumentConfig = this._getInstrumentConfig(instrumentConfigId, udsDbContext)

      // Configure result file before parsing
      resultFile.instrumentConfig = instrumentConfig

      //Fait par le Storer: Attente partage transaction TODO    
      //    val msiTransaction = stContext.msiEm.getTransaction
      //    var msiTransacOk: Boolean = false

      logger.debug("Starting Msi Db transaction")

      //Start MSI Transaction and ResultSets store

      //Fait par le Storer: Attente partage transaction TODO  
      //      msiTransaction.begin()
      //      msiTransacOk = false

      // Insert instrument config in the MSIdb
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
        val peaklistSoftware = _getPeaklistSoftware("Default_PL", "0.1",udsDbContext)
        // FIXME: implement the method _getOrCreatePeaklistSoftware instead
        if( peaklistSoftware.id < 0 ) peaklistSoftware.id = peaklistSoftwareId
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

      // Load and store decoy result set if it exists
      if (resultFile.hasDecoyResultSet) {
        storeDecoyRs(resultFile.getResultSet(true))
        >>>
      } // Else if a regex has been passed to detect decoy protein matches		  
      else if (acDecoyRegex != None) {
        // Then split the result set into a target and a decoy one
        val (tRs, dRs) = TargetDecoyResultSetSplitter.split(targetRs, acDecoyRegex.get)
        targetRs = tRs

        storeDecoyRs(dRs)
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
        storerContext.closeAll()
      }

    }

    this.beforeInterruption()
    
    true
    //    msiTransacOk   
  }

  // TODO: put in a dedicated provider
  private def _getPeaklistSoftware(plName: String, plRevision: String, udsDbContext: DatabaseContext ): PeaklistSoftware = {
    
    var pklSoft: PeaklistSoftware = null
    val jdbcWork = JDBCWorkBuilder.withEzDBC( udsDbContext.getDriverType, { udsEzDBC =>    
      pklSoft = udsEzDBC.selectHeadOrElse(
        "SELECT * FROM peaklist_software WHERE name= ? and version= ? ", plName, plRevision)(r =>
          new PeaklistSoftware(id = r, name = r, version = r),
          new PeaklistSoftware(id = PeaklistSoftware.generateNewId, name = "Default", version = "0.1")
        )
    })
    
    udsDbContext.doWork(jdbcWork, false)
    
    pklSoft    
  }

  // TODO: put in a dedicated provider
  private def _getInstrumentConfig(instrumentConfigId: Int, udsDbContext: DatabaseContext ): InstrumentConfig = {

    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.core.om.model.msi.{ InstrumentProperties, InstrumentConfigProperties }

    var instrumentConfig: InstrumentConfig = null
    
    val jdbcWork = JDBCWorkBuilder.withEzDBC( udsDbContext.getDriverType, { udsEzDBC => 
      // Load the instrument configuration record
      udsEzDBC.selectHead(
        "SELECT instrument.*,instrument_config.* FROM instrument,instrument_config " +
        "WHERE instrument.id = instrument_config.instrument_id AND instrument_config.id =" + instrumentConfigId) { r =>

        val instrument = new Instrument(id = r.nextAnyVal, name = r, source = r)
        for (instPropStr <- r.nextStringOption) {
          instrument.properties = Some(parse[InstrumentProperties](instPropStr))
        }

        // Skip instrument_config.id field
        r.nextAny

        instrumentConfig = new InstrumentConfig(
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
    
    udsDbContext.doWork(jdbcWork, false)
    
    instrumentConfig  

  }

}