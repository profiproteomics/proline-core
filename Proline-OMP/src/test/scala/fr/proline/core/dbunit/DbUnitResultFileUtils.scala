package fr.proline.core.dbunit

import fr.profi.jdbc.easy._
import fr.proline.context.IExecutionContext
import fr.proline.core.parser.msi.DbUnitResultFile
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.{ SQLPeaklistSoftwareProvider => MsiSQLPklSoftProvider }
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.profi.util.serialization.ProfiJson

object DbUnitResultFileUtils {
  
  def importDbUnitResultFile( datasetLocation: DbUnitResultFileLocation, execCtx: IExecutionContext ): ResultSet =  {
    val rf = this.loadDbUnitResultFile( datasetLocation )
    this.storeDbUnitResultFile( rf, execCtx )
  }
  
  def loadDbUnitResultFile( datasetLocation: DbUnitResultFileLocation ): DbUnitResultFile = {
    
    val classLoader = classOf[fr.proline.repository.util.DatabaseTestCase]
    
    // Open streams
    val msiStream = classLoader.getResourceAsStream( datasetLocation.msiDbDatasetPath )
    val udsStream = classLoader.getResourceAsStream( datasetLocation.udsDbDatasetPath )
    val psStream = classLoader.getResourceAsStream( datasetLocation.psDbDatasetPath )
    
    val dbUnitRF = new DbUnitResultFile(msiStream,udsStream,psStream)  
    
    // Close input streams
    msiStream.close()
    udsStream.close()
    psStream.close()
    
    dbUnitRF
  }
  
  // TODO: put in Singleton Utils object
  // TODO : remove code redundancy with class AbstractDbUnitResultFileTestCase
  def storeDbUnitResultFile( dbUnitResultFile: DbUnitResultFile, execCtx: IExecutionContext ): ResultSet =  {
    
    val rs = dbUnitResultFile.getResultSet(wantDecoy = false)
    val msiSearch = rs.msiSearch.get
    
    // FIX the id of peaklistSoftware and instrumentConfig (they are persisted in the UDSdb)
    msiSearch.peakList.peaklistSoftware.id = - msiSearch.peakList.peaklistSoftware.id
    
    val instrumentConfig = msiSearch.searchSettings.instrumentConfig
    instrumentConfig.instrument = instrumentConfig.instrument.copy( id = - instrumentConfig.instrument.id )
    msiSearch.searchSettings.instrumentConfig = instrumentConfig.copy( id = - instrumentConfig.id )
    
    // Update result file peaklistSoftware and instrumentConfig
    dbUnitResultFile.peaklistSoftware = Some(msiSearch.peakList.peaklistSoftware)
    dbUnitResultFile.instrumentConfig = Some(msiSearch.searchSettings.instrumentConfig)
    
    val msiDbCtx = execCtx.getMSIDbConnectionContext
    
    msiDbCtx.beginTransaction()
    
    // TODO: do this in the ResultFileStorer
    DoJDBCWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val msiPklSoftProvider = new MsiSQLPklSoftProvider(msiDbCtx)
      
      val pklSoft = dbUnitResultFile.peaklistSoftware.get
      
      // Try to retrieve peaklist software from the MSidb
      val msiPklSoftOpt = msiPklSoftProvider.getPeaklistSoftware(pklSoft.id)
      if (msiPklSoftOpt.isEmpty) {        
        
        // Insert peaklist software in the MsiDb
        val peaklistInsertQuery = MsiDbPeaklistSoftwareTable.mkInsertQuery
        msiEzDBC.execute(
          peaklistInsertQuery,
          pklSoft.id,
          pklSoft.name,
          pklSoft.version,
          pklSoft.properties.map(ProfiJson.serialize(_))
        )
      }
      
    })
    
    val storerContext = StorerContext(execCtx) // Use Object factory
    val rsStorer = RsStorer(msiDbCtx)
    
    val storedRS = ResultFileStorer.storeResultFile(
      storerContext = storerContext,
      rsStorer = rsStorer,
      resultFile = dbUnitResultFile,
      sqlCompatMode = !execCtx.isJPA, // SQL compat => FIXME: remove me when ResultFileStorer has the same behavior for JPA/SQL
      targetDecoyMode = rs.getTargetDecoyMode(),
      acDecoyRegex = None,
      saveSpectrumMatch = false,
      rsSplitter = None
    )
    
    msiDbCtx.commitTransaction()
    
    storedRS
  }

}