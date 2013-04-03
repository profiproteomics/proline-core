package fr.proline.core.om.model.lcms

import java.util.Date
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.util.misc.InMemoryIdGen
import fr.proline.core.om.model.msi.Instrument

case class RawFile( 
    
  // Required fields
  val id: Int,
  val name: String,
  val extension: String,
  val directory: String,
  val creationTimestamp: Date,
  val instrument: Option[Instrument] = None,
  
  // Mutable optional fields
  var properties: Option[RawFileProperties] = None
) {
  require( instrument != null )
}
            
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RawFileProperties

object LcMsRun extends InMemoryIdGen {
  var timeIndexWidth = 10
  def calcTimeIndex( time: Double ): Int = (time/timeIndexWidth).toInt
}

case class LcMsRun(
        
  // Required fields
  val id: Int,
  val rawFileName: String,
  //val instrumentName: String,
  val minIntensity: Double,
  val maxIntensity: Double,
  val ms1ScansCount: Int,
  val ms2ScansCount: Int,
  val rawFile: RawFile,
  val scans: Array[LcMsScan],
  
  // Mutable optional fields
  var properties: Option[LcMsRunProperties] = None
  
) {
  require( RawFile != null )
  require( scans != null )
  
  lazy val scanById = Map() ++ scans.map { scan => ( scan.id -> scan ) }
  
  lazy val scanIdByInitialId = Map() ++ scans.map { scan => ( scan.initialId -> scan.id ) }
  
  lazy val endTime: Float = scans.last.time
  
  lazy val scanIdsByTimeIndex: Map[Int,Array[Int]] = {

    val timeIndexWidth = LcMsRun.timeIndexWidth;
    
    import scala.collection.JavaConversions._
    val scanIdsByTimeIndexHashMap = new java.util.HashMap[Int,ArrayBuffer[Int]]()
    
    for( scan <- scans ) {
      
      val timeIndex = LcMsRun.calcTimeIndex(scan.time)
      
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

  def getScanAtTime( time: Float, msLevel: Int = 1 ): LcMsScan = {
    if( time < 0 ) { throw new IllegalArgumentException("time must be a positive number" ); }
    
    val runEndTime = endTime
    val safeTime = if( time > runEndTime ) runEndTime else time
    
    val timeIndex = LcMsRun.calcTimeIndex(time)      
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
  
  def isEmpty() : Boolean = {
    scans.length == 0
  }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class LcMsRunProperties(
  @BeanProperty var mzDbFilePath: String
)

case class LcMsScan(
    
  // Required fields
  val id: Int,
  val initialId: Int,
  val cycle: Int,
  val time: Float,
  val msLevel: Int,
  val tic: Double,
  val basePeakMoz: Double,
  val basePeakIntensity: Double,
  
  var runId: Int,
  
  // Immutable optional fields
  val precursorMoz: Double = Double.NaN,
  val precursorCharge: Int = 0,
  
  // Mutable optional fields
  var properties: Option[LcMsScanProperties] = None

)
            
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class LcMsScanProperties

