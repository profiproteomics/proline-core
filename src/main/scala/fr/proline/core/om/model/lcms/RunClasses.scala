package fr.proline.core.om.lcms

import java.util.Date
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer

case class Instrument( 
    
            // Required fields
            val id: Int,
            val name: String,
            val source: String,
  
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            )

case class RawFile( 
    
            // Required fields
            val id: Int,
            val name: String,
            val extension: String,
            val directory: String,
            val creationTimestamp: Date,
            val instrument: Instrument,
            
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
        
            )

object LcmsRun {
  var timeIndexWidth = 10
  def calcTimeIndex( time: Double ): Int = (time/timeIndexWidth).toInt
}

case class LcmsRun(
        
            // Required fields
            val id: Int,
            val rawFileName: String,
            //val instrumentName: String,
            val minIntensity: Double,
            val maxIntensity: Double,
            val ms1ScanCount: Int,
            val ms2ScanCount: Int,
            val rawFile: RawFile,
            val scans: Array[LcmsScan],
            
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) {
  require( scans != null )
  
  lazy val scanById = Map() ++ scans.map { scan => ( scan.id -> scan ) }
  
  lazy val scanIdByInitialId = Map() ++ scans.map { scan => ( scan.initialId -> scan.id ) }
  
  lazy val endTime: Float = scans.last.time
  
  lazy val scanIdsByTimeIndex: Map[Int,Array[Int]] = {

    val timeIndexWidth = LcmsRun.timeIndexWidth;
    
    import scala.collection.JavaConversions._
    val scanIdsByTimeIndexHashMap = new java.util.HashMap[Int,ArrayBuffer[Int]]()
    
    for( scan <- scans ) {
      
      val timeIndex = LcmsRun.calcTimeIndex(scan.time)
      
      if( !scanIdsByTimeIndex.containsKey(timeIndex) ) {
        scanIdsByTimeIndexHashMap.put(timeIndex, new ArrayBuffer[Int](1) )
      }
      
      scanIdsByTimeIndexHashMap.get(timeIndex) += scan.id
    }
    
    val scanIdsIndexBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
    for( timeIndex <- scanIdsByTimeIndexHashMap.keys ) {
      val scanIds = scanIdsByTimeIndexHashMap.get(timeIndex)
      scanIdsIndexBuilder += ( timeIndex -> scanIds.toArray )
    }
    
    scanIdsIndexBuilder.result()
  }

  def getScanAtTime( time: Float, msLevel: Int = 1 ): LcmsScan = {
    if( time < 0 ) { throw new IllegalArgumentException("time must be a positive number" ); }
    
    val runEndTime = endTime
    val safeTime = if( time > runEndTime ) runEndTime else time
    
    val timeIndex = LcmsRun.calcTimeIndex(time)      
    val scanIdsIndex = scanIdsByTimeIndex      
    var matchingScanIds = new ArrayBuffer[Int]
    
    for( index <- timeIndex-1 to timeIndex+1 ) {
      val tmpScanIds = scanIdsIndex(index)
      if( tmpScanIds != null) matchingScanIds ++= tmpScanIds
    }
    
    // Determine all matching scans and sort them in ascendant order of absolute time distance 
    val myScanById = scanById
    val matchingScans = matchingScanIds .
                        map { myScanById(_) } .
                        filter { s => s.msLevel == msLevel } .
                        sortWith { (a, b) => math.abs(a.time - time) < math.abs(b.time-time) }
    
    // Return nearest scan
    matchingScans(0)           

  }
  
}

case class LcmsScan(
    
            // Required fields
            val id: Int,
            val initialId: Int,
            val cycle: Int,
            val time: Float,
            val msLevel: Int,
            val tic: Double,
            val basePeakMoz: Double,
            val basePeakIntensity: Double,
            
            val runId: Int,
            
            // Immutable optional fields
            val precursorMoz: Double = Double.NaN,
            val precursorCharge: Int = 0,
            
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
        
            )
