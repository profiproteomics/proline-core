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
      id = toInt(runRecord.getAnyVal("id")),
      rawFileName = runRecord.getString("raw_file_name"),
      minIntensity = runRecord.getDoubleOrElse("min_intensity",Double.NaN),
      maxIntensity = runRecord.getDoubleOrElse("max_intensity",Double.NaN),
      ms1ScanCount = runRecord.getIntOrElse("ms1_scan_count",0),
      ms2ScanCount = runRecord.getIntOrElse("ms2_scan_count",0),
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

    val precursorMoz = scanRecord.getDoubleOrElse("precursor_moz",Double.NaN)
    val precursorCharge = scanRecord.getIntOrElse("precursor_charge",0)
    
    new LcMsScan(
      id = toInt(scanRecord.getAnyVal("id")),
      initialId = scanRecord.getInt("initial_id"),
      cycle = scanRecord.getInt("cycle"),
      time = toFloat(scanRecord.getDouble("time")),
      msLevel = scanRecord.getInt("ms_level"),
      tic = scanRecord.getDouble("tic"),
      basePeakMoz = scanRecord.getDouble("base_peak_moz"),
      basePeakIntensity = scanRecord.getDouble("base_peak_intensity"),
      runId = scanRecord.getInt("run_id"),
      precursorMoz = precursorMoz,
      precursorCharge = precursorCharge
    )
    
  }

}
