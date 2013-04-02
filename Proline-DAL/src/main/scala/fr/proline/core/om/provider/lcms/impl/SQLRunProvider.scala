package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.{ LcmsDbRunTable, LcmsDbScanTable }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IRunProvider
import fr.proline.util.sql._
import fr.proline.util.primitives._

class SQLRunProvider( val lcmsDbCtx: DatabaseConnectionContext ) extends IRunProvider {
  
  val RunCols = LcmsDbRunTable.columns
  val ScanCols = LcmsDbScanTable.columns
  
  def getRuns( runIds: Seq[Int] ): Array[LcMsRun] = {
    
    val scans = this.getScans( runIds )
    // Group scans by run id
    val scansByRunId = scans.groupBy(_.runId)
    
    // TODO: load related raw file and instrument (need UDSdb provider ???)
    
    val runs = new Array[LcMsRun](runIds.length)
    var runIdx = 0
    
    // Load runs
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val runQuery = new SelectQueryBuilder1(LcmsDbRunTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ runIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( runQuery ) { runRecord =>
        
        val runScans = scansByRunId(runRecord.getInt("id"))
        
        // Build the run
        runs(runIdx) = buildRunMap(runRecord, runScans )
        runIdx += 1
      }
      
      runs
      
    })
  }
  
  def buildRunMap( runRecord: ResultSetRow, scans: Array[LcMsScan] ): LcMsRun = {
    
    new LcMsRun(
      id = toInt(runRecord.getAnyVal(RunCols.ID)),
      rawFileName = runRecord.getString(RunCols.RAW_FILE_NAME),
      minIntensity = runRecord.getDoubleOrElse(RunCols.MIN_INTENSITY,Double.NaN),
      maxIntensity = runRecord.getDoubleOrElse(RunCols.MAX_INTENSITY,Double.NaN),
      ms1ScanCount = runRecord.getIntOrElse(RunCols.MS1_SCAN_COUNT,0),
      ms2ScanCount = runRecord.getIntOrElse(RunCols.MS2_SCAN_COUNT,0),
      rawFile = null,
      scans = scans
    )
  }
  
  def getScans( runIds: Seq[Int] ): Array[LcMsScan] = {
    
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC => 
      
      val runIdsStr = runIds.mkString(",")    
      val nbScans: Int = ezDBC.selectInt( "SELECT count(id) FROM scan WHERE run_id IN (" + runIdsStr + ")" )
      
      // Load scans
      var lcmsScanIdx = 0
      val scans = new Array[LcMsScan](nbScans)
      
      val runQuery = new SelectQueryBuilder1(LcmsDbScanTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.RUN_ID ~" IN("~ runIdsStr ~") "
      )
      
      ezDBC.selectAndProcess( runQuery ) { scanRecord =>
        scans(lcmsScanIdx) = buildLcmsScan( scanRecord )
        lcmsScanIdx += 1
      }
      
      scans
    })
  }
  
  def buildLcmsScan( scanRecord: ResultSetRow ): LcMsScan = {

    val precursorMoz = scanRecord.getDoubleOrElse(ScanCols.PRECURSOR_MOZ,Double.NaN)
    val precursorCharge = scanRecord.getIntOrElse(ScanCols.PRECURSOR_CHARGE,0)
    
    new LcMsScan(
      id = toInt(scanRecord.getAnyVal(ScanCols.ID)),
      initialId = scanRecord.getInt(ScanCols.INITIAL_ID),
      cycle = scanRecord.getInt(ScanCols.CYCLE),
      time = toFloat(scanRecord.getDouble(ScanCols.TIME)),
      msLevel = scanRecord.getInt(ScanCols.MS_LEVEL),
      tic = scanRecord.getDouble(ScanCols.TIC),
      basePeakMoz = scanRecord.getDouble(ScanCols.BASE_PEAK_MOZ),
      basePeakIntensity = scanRecord.getDouble(ScanCols.BASE_PEAK_INTENSITY),
      runId = scanRecord.getInt(ScanCols.RUN_ID),
      precursorMoz = precursorMoz,
      precursorCharge = precursorCharge
    )
    
  }

}
