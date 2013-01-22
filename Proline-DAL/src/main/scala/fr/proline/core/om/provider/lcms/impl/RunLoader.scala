package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.SQLQueryExecution

class RunLoader( val sqlExec: SQLQueryExecution )  {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.util.sql._
  import fr.proline.core.om.model.lcms._
  
  def getRuns( runIds: Seq[Int] ): Array[LcmsRun] = {
    
    val scans = this.getScans( runIds )
    // Group scans by run id
    val scansByRunId = scans.groupBy(_.runId)
    
    // TODO: load related raw file and instrument
    
    val runs = new Array[LcmsRun](runIds.length)    
    var colNames: Seq[String] = null
    var runIdx = 0
    
    // Load runs
    sqlExec.select( "SELECT * FROM run WHERE id IN (" + runIds.mkString(",") + ")" ) { r =>
        
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the run record
        val runRecord = colNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
        val runId = runRecord("id").asInstanceOf[Int]
        val runScans = scansByRunId(runId)
        
        // Build the run
        runs(runIdx) = buildRunMap(runRecord, runScans )
        runIdx += 1
        
        ()
      }
    
    runs
  }
  
  def buildRunMap( runRecord: Map[String,Any], scans: Array[LcmsScan] ): LcmsRun = {
    
    import java.util.Date
    
    new LcmsRun( id = runRecord("id").asInstanceOf[Int],
                 rawFileName = runRecord("raw_file_path").asInstanceOf[String],// raw_file_name
                 minIntensity = runRecord.getOrElse("min_intensity",Double.NaN).asInstanceOf[Double],
                 maxIntensity = runRecord.getOrElse("max_intensity",Double.NaN).asInstanceOf[Double],
                 ms1ScanCount = runRecord.getOrElse("ms1_scan_count",0).asInstanceOf[Int],
                 ms2ScanCount = runRecord.getOrElse("ms2_scan_count",0).asInstanceOf[Int],
                 rawFile = null,
                 scans = scans
               )
  }
  
  def getScans( runIds: Seq[Int] ): Array[LcmsScan] = {
    
    val nbScans: Int = sqlExec.selectInt( "SELECT count(*) FROM scan WHERE run_id IN (" + runIds.mkString(",") + ")" )
    val scans = new Array[LcmsScan](nbScans)    
    var colNames: Seq[String] = null
    var lcmsScanIdx = 0
    
    // Load scans
    sqlExec.select( "SELECT * FROM scan WHERE run_id IN (" + runIds.mkString(",") +")" ) { r =>
        
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the scan record
        val scanRecord = colNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
        
        // Build the scan
        scans(lcmsScanIdx) = buildLcmsScan( scanRecord )
        lcmsScanIdx += 1
        
        ()
      }
    
    scans
  }
  
  def buildLcmsScan( scanRecord: Map[String,Any] ): LcmsScan = {
    
    import fr.proline.util.primitives.DoubleOrFloatAsFloat._
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    val precursorMoz = scanRecord.getOrElse("precursor_moz",Double.NaN).asInstanceOf[Double]
    val precursorCharge = scanRecord.getOrElse("precursor_charge",0).asInstanceOf[Int]
    
    new LcmsScan( id = scanRecord("id").asInstanceOf[AnyVal],
                  initialId = scanRecord("initial_id").asInstanceOf[Int],
                  cycle = scanRecord("cycle").asInstanceOf[Int],
                  time = scanRecord("time").asInstanceOf[AnyVal],
                  msLevel = scanRecord("ms_level").asInstanceOf[Int],
                  tic = scanRecord("tic").asInstanceOf[Double],
                  basePeakMoz = scanRecord("base_peak_moz").asInstanceOf[Double],
                  basePeakIntensity = scanRecord("base_peak_intensity").asInstanceOf[Double],
                  runId = scanRecord("run_id").asInstanceOf[Int],
                  precursorMoz = precursorMoz,
                  precursorCharge = precursorCharge
                 )
  }

}
