package fr.proline.core.algo.msi

import org.junit.BeforeClass

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.AbstractEmptyDatastoreTestCase
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.parser.msi.DbUnitResultFile
import fr.proline.repository.DriverType

abstract class AbstractDbUnitResultFileTestCase extends AbstractEmptyDatastoreTestCase with DbUnitResultFileLoading {
  
  val useJPA = false
  
  @BeforeClass
  @throws(classOf[Exception])
  override def setUp() = {
    super.setUp()
      
    val resultFile = getResultFile()
    val rs = getRS()
    val msiSearch = rs.msiSearch.get
    
    // FIX the id of peaklistSoftware and instrumentConfig (they are persisted in the UDSdb)
    msiSearch.peakList.peaklistSoftware.id = - msiSearch.peakList.peaklistSoftware.id
    
    val instrumentConfig = msiSearch.searchSettings.instrumentConfig
    instrumentConfig.instrument = instrumentConfig.instrument.copy( id = - instrumentConfig.instrument.id )
    msiSearch.searchSettings.instrumentConfig = instrumentConfig.copy( id = - instrumentConfig.id )
    
    // Update result file peaklistSoftware and instrumentConfig
    resultFile.peaklistSoftware = Some(msiSearch.peakList.peaklistSoftware)
    resultFile.instrumentConfig = Some(msiSearch.searchSettings.instrumentConfig)
    
    val msiDbCtx = executionContext.getMSIDbConnectionContext
    
    msiDbCtx.beginTransaction()
    
    // TODO: do this in the ResultFileStorer
    DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val pklSoft = resultFile.peaklistSoftware.get
      
      // Insert peaklist software in the MsiDb
      val peaklistInsertQuery = MsiDbPeaklistSoftwareTable.mkInsertQuery
      msiEzDBC.execute(
        peaklistInsertQuery,
        pklSoft.id,
        pklSoft.name,
        pklSoft.version,
        pklSoft.properties.map(ProfiJson.serialize(_))
      )
    })
    
    val storerContext = StorerContext(executionContext) // Use Object factory
    val rsStorer = RsStorer(msiDbCtx)
    
    ResultFileStorer.storeResultFile(
      storerContext = storerContext,
      rsStorer = rsStorer,
      resultFile = resultFile,
      sqlCompatMode = !executionContext.isJPA, // SQL compat => FIXME: remove me when ResultFileStorer has the same behavior for JPA/SQL
      targetDecoyMode = rs.getTargetDecoyMode(),
      acDecoyRegex = None,
      saveSpectrumMatch = false,
      rsSplitter = None
    )
    
    msiDbCtx.commitTransaction()
    
    executionContext = storerContext
  }
  
}

trait DbUnitResultFileLoading extends Logging {

  // Define the interface to be implemented
  val driverType: DriverType
  val dbUnitResultFile: DbUnitResultFileLocation
  val targetRSId: Long
  val decoyRSId: Option[Long]
  
  protected var readRS: ResultSet = null
  protected var loadedRF: IResultFile = null
  
  def getRS(): ResultSet = {    
    this.readRS
  }
  
  def getResultFile(): IResultFile = {
    this.loadedRF
  }

  @BeforeClass
  protected def loadResultFile() = {
    
    val classLoader = classOf[fr.proline.repository.util.DatabaseTestCase]
    
    // Open streams
    val msiStream = classLoader.getResourceAsStream( dbUnitResultFile.msiDbDatasetPath )
    val udsStream = classLoader.getResourceAsStream( dbUnitResultFile.udsDbDatasetPath )
    val psStream = classLoader.getResourceAsStream( dbUnitResultFile.psDbDatasetPath )  
    
    // Load the DbUnit result file
    loadedRF = new DbUnitResultFile(msiStream,udsStream,psStream)

    logger.info("Result file succesfully loaded !")

    val rs = loadedRF.getResultSet(wantDecoy = false)
    if (decoyRSId.isDefined) rs.decoyResultSet = Some( loadedRF.getResultSet(wantDecoy = true) )
    
    readRS = rs
  }
  
}
