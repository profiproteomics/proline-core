package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.{ LcmsDbScanSequenceTable, LcmsDbScanTable }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IScanSequenceProvider
import fr.profi.util.sql._
import fr.profi.util.primitives._

class SQLScanSequenceProvider(val lcmsDbCtx: DatabaseConnectionContext) extends IScanSequenceProvider {
  
  val ScanSeqCols = LcmsDbScanSequenceTable.columns
  val ScanCols = LcmsDbScanTable.columns
  
  def getScanSequences( scanSequenceIds: Seq[Long] ): Array[LcMsScanSequence] = {
    if( scanSequenceIds.isEmpty ) return Array()
    
    val scans = this.getScans( scanSequenceIds )
    // Group scans by run id
    val scansByRunId = scans.groupBy(_.runId)
    
    // TODO: load related raw file and instrument (need UDSdb provider ???)
    
    val scanSeqs = new Array[LcMsScanSequence](scanSequenceIds.length)
    var scanSeqIdx = 0
    
    // Load runs
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val runQuery = new SelectQueryBuilder1(LcmsDbScanSequenceTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ scanSequenceIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( runQuery ) { runRecord =>
        
        val runScans = scansByRunId(toLong(runRecord.getAny(ScanSeqCols.ID)))
        
        // Build the scan sequence
        scanSeqs(scanSeqIdx) = buildScanSequence(runRecord, runScans )
        
        scanSeqIdx += 1
      }
      
      scanSeqs
      
    })
  }
  
  def buildScanSequence( runRecord: ResultSetRow, scans: Array[LcMsScan] ): LcMsScanSequence = {
    
    new LcMsScanSequence(
      runId = toLong(runRecord.getAny(ScanSeqCols.ID)),
      rawFileName = runRecord.getString(ScanSeqCols.RAW_FILE_NAME),
      minIntensity = runRecord.getDoubleOrElse(ScanSeqCols.MIN_INTENSITY,Double.NaN),
      maxIntensity = runRecord.getDoubleOrElse(ScanSeqCols.MAX_INTENSITY,Double.NaN),
      ms1ScansCount = runRecord.getIntOrElse(ScanSeqCols.MS1_SCAN_COUNT,0),
      ms2ScansCount = runRecord.getIntOrElse(ScanSeqCols.MS2_SCAN_COUNT,0),
      scans = scans
    )
  }
  
  def getScans( scanSequenceIds: Seq[Long] ): Array[LcMsScan] = {
    if( scanSequenceIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC => 
      
      val runIdsStr = scanSequenceIds.mkString(",")    
         
      val nbScans: Int = ezDBC.selectInt(
        s"SELECT count(id) FROM ${LcmsDbScanTable} WHERE ${ScanCols.SCAN_SEQUENCE_ID} IN (" + runIdsStr + ")"
      )
      
      // Load scans
      var lcmsScanIdx = 0
      val scans = new Array[LcMsScan](nbScans)
      
      // TODO: order by initial id
      val runQuery = new SelectQueryBuilder1(LcmsDbScanTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.SCAN_SEQUENCE_ID ~" IN("~ runIdsStr ~") "
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
      runId = toLong(scanRecord.getAny(ScanCols.SCAN_SEQUENCE_ID)),
      precursorMoz = precursorMoz,
      precursorCharge = precursorCharge
    )
    
  }

}
