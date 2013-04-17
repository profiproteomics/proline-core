package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.SQLQueryExecution

/*
class RunLoader( val sqlExec: SQLQueryExecution )  {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.util.sql._
  import fr.proline.core.om.model.lcms._
  
  def getRuns( runIds: Seq[Int] ): Array[LcMsRun] = {
    
    val scans = this.getScans( runIds )
    // Group scans by run id
    val scansByRunId = scans.groupBy(_.runId)
    
    // TODO: load related raw file and instrument
    
    val runs = new Array[LcMsRun](runIds.length)    
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
  
  def buildRunMap( runRecord: Map[String,Any], scans: Array[LcMsScan] ): LcMsRun = {
    
    import java.util.Date
    
    new LcMsRun( id = runRecord("id").asInstanceOf[Int],
                 rawFileName = runRecord("raw_file_path").asInstanceOf[String],// raw_file_name
                 minIntensity = runRecord.getOrElse("min_intensity",Double.NaN).asInstanceOf[Double],
                 maxIntensity = runRecord.getOrElse("max_intensity",Double.NaN).asInstanceOf[Double],
                 ms1ScansCount = runRecord.getOrElse("ms1_scan_count",0).asInstanceOf[Int],
                 ms2ScansCount = runRecord.getOrElse("ms2_scan_count",0).asInstanceOf[Int],
                 rawFile = null,
                 scans = scans
               )
  }
  
  def getScans( runIds: Seq[Int] ): Array[LcMsScan] = {
    
    val nbScans: Int = sqlExec.selectInt( "SELECT count(*) FROM scan WHERE run_id IN (" + runIds.mkString(",") + ")" )
    val scans = new Array[LcMsScan](nbScans)    
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
  
  def buildLcmsScan( scanRecord: Map[String,Any] ): LcMsScan = {
    
    import fr.proline.util.primitives._

    val precursorMoz = scanRecord.getOrElse("precursor_moz",Double.NaN).asInstanceOf[Double]
    val precursorCharge = scanRecord.getOrElse("precursor_charge",0).asInstanceOf[Int]
    
    new LcMsScan( id = toInt(scanRecord("id")),
                  initialId = scanRecord("initial_id").asInstanceOf[Int],
                  cycle = scanRecord("cycle").asInstanceOf[Int],
                  time = toFloat(scanRecord("time")),
                  msLevel = scanRecord("ms_level").asInstanceOf[Int],
                  tic = scanRecord("tic").asInstanceOf[Double],
                  basePeakMoz = scanRecord("base_peak_moz").asInstanceOf[Double],
                  basePeakIntensity = scanRecord("base_peak_intensity").asInstanceOf[Double],
                  runId = scanRecord("run_id").asInstanceOf[Int],
                  precursorMoz = precursorMoz,
                  precursorCharge = precursorCharge
                 )
  }

}*/
