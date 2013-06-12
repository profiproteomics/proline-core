package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.{ LcmsDbRunTable, LcmsDbScanTable }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IScanSequenceProvider
import fr.proline.util.sql._
import fr.proline.util.primitives._

class SQLScanSequenceProvider(val lcmsDbCtx: DatabaseConnectionContext) extends IScanSequenceProvider {
  
  val RunCols = LcmsDbRunTable.columns
  val ScanCols = LcmsDbScanTable.columns
  
  def getScanSequences( runIds: Seq[Long] ): Array[LcMsScanSequence] = {
    
    val scans = this.getScans( runIds )
    // Group scans by run id
    val scansByRunId = scans.groupBy(_.runId)
    
    // TODO: load related raw file and instrument (need UDSdb provider ???)
    
    val scanSeqs = new Array[LcMsScanSequence](runIds.length)
    var scanSeqIdx = 0
    
    // Load runs
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val runQuery = new SelectQueryBuilder1(LcmsDbRunTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ runIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( runQuery ) { runRecord =>
        
        val runScans = scansByRunId(toLong(runRecord.getAny(RunCols.ID)))
        
        // Build the scan sequence
        scanSeqs(scanSeqIdx) = buildScanSequence(runRecord, runScans )
        
        scanSeqIdx += 1
      }
      
      scanSeqs
      
    })
  }
  
  def buildScanSequence( runRecord: ResultSetRow, scans: Array[LcMsScan] ): LcMsScanSequence = {
    
    new LcMsScanSequence(
      id = toLong(runRecord.getAny(RunCols.ID)),
      rawFileName = runRecord.getString(RunCols.RAW_FILE_NAME),
      minIntensity = runRecord.getDoubleOrElse(RunCols.MIN_INTENSITY,Double.NaN),
      maxIntensity = runRecord.getDoubleOrElse(RunCols.MAX_INTENSITY,Double.NaN),
      ms1ScansCount = runRecord.getIntOrElse(RunCols.MS1_SCAN_COUNT,0),
      ms2ScansCount = runRecord.getIntOrElse(RunCols.MS2_SCAN_COUNT,0),
      scans = scans
    )
  }
  
  def getScans( runIds: Seq[Long] ): Array[LcMsScan] = {
    
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

    val precursorMoz = scanRecord.getDoubleOption(ScanCols.PRECURSOR_MOZ)
    val precursorCharge = scanRecord.getIntOption(ScanCols.PRECURSOR_CHARGE)
    
    new LcMsScan(
      id = toLong(scanRecord.getAny(ScanCols.ID)),
      initialId = scanRecord.getInt(ScanCols.INITIAL_ID),
      cycle = scanRecord.getInt(ScanCols.CYCLE),
      time = toFloat(scanRecord.getDouble(ScanCols.TIME)),
      msLevel = scanRecord.getInt(ScanCols.MS_LEVEL),
      tic = scanRecord.getDouble(ScanCols.TIC),
      basePeakMoz = scanRecord.getDouble(ScanCols.BASE_PEAK_MOZ),
      basePeakIntensity = scanRecord.getDouble(ScanCols.BASE_PEAK_INTENSITY),
      runId = toLong(scanRecord.getAny(ScanCols.RUN_ID)),
      precursorMoz = precursorMoz,
      precursorCharge = precursorCharge
    )
    
  }

}
