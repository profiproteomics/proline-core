package fr.proline.core.om.model.lcms

import java.util.Date
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.beans.BeanProperty
//import com.fasterxml.jackson.annotation.JsonInclude
//import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.profi.util.misc.InMemoryIdGen
import fr.proline.core.om.model.msi.Instrument

case class RawFile( 
    
  // Required fields
  val name: String,
  val extension: String,
  
  // Immutable optional fields
  val directory: Option[String] = None,
  val creationTimestamp: Option[Date] = None,
  val instrument: Option[Instrument] = None,
  
  // Mutable optional fields
  var properties: Option[RawFileProperties] = None
) {
  require( instrument != null )
}
            
//@JsonInclude( Include.NON_NULL )
case class RawFileProperties(
  @BeanProperty var mzdbFilePath: String
)

object LcMsRun extends InMemoryIdGen

case class LcMsRun(
  
  // Required fields
  val id: Long,
  val number: Int,
  val runStart: Float,
  val runStop: Float,
  val duration: Float,
  val rawFile: RawFile,
  
  var lcMethod: Option[String] = None,
  var msMethod: Option[String] = None,
  var analyst: Option[String] = None,
  var scanSequence: Option[LcMsScanSequence] = None,  
  var properties: Option[LcMsRunProperties] = None
) {
  require( RawFile != null )
  
  def getRawFileName = rawFile.name
  
}

//@JsonInclude( Include.NON_NULL )
case class LcMsRunProperties()


object LcMsScanSequence {
  var timeIndexWidth = 10
  def calcTimeIndex( time: Double ): Int = (time/timeIndexWidth).toInt
}

case class LcMsScanSequence(
  
  // Required fields
  val runId: Long, // MUST be the run id
  
  val rawFileName: String,
  val minIntensity: Double,
  val maxIntensity: Double,
  val ms1ScansCount: Int,
  val ms2ScansCount: Int,
  val scans: Array[LcMsScan],
  var instrument: Option[Instrument] = None,
  
  // Mutable optional fields
  var properties: Option[LcMsScanSequenceProperties] = None
  
) {
  require( scans != null )
  
  lazy val scanById = Map() ++ scans.map { scan => ( scan.id -> scan ) }
  
  lazy val scanIdByInitialId = Map() ++ scans.map { scan => ( scan.initialId -> scan.id ) }
  
  lazy val endTime: Float = scans.last.time
  
  lazy val scanIdsByTimeIndex: Map[Int,Array[Long]] = {
    scans.groupBy( scan => LcMsScanSequence.calcTimeIndex(scan.time) ).map { case (idx,scans) =>
      idx -> scans.map(_.id)
    }
  }
  
  def getScanByInitialId( initialId: Int ): Option[LcMsScan] = {
    for( scanId <- scanIdByInitialId.get(initialId) ) yield scanById(scanId)
  }

  def getScanAtTime( time: Float, msLevel: Int = 1 ): LcMsScan = {
    require( time >= 0, "time must be a positive number" )
    
    val runEndTime = endTime
    val safeTime = if( time > runEndTime ) runEndTime else time
    
    val timeIndex = LcMsScanSequence.calcTimeIndex(safeTime)
    val scanIdsIndex = scanIdsByTimeIndex
    val myScanById = scanById
    
    // Determine all matching scans
    val matchingScans = for(
      index <- timeIndex-1 to timeIndex+1;
      val tmpScanIds = scanIdsIndex(index);
      if tmpScanIds != null;
      tmpScanId <- tmpScanIds;
      val scan = myScanById(tmpScanId);
      if scan.msLevel == msLevel
    ) yield scan

    // Return nearest scan from provided time
    matchingScans.minBy( s => math.abs(s.time - safeTime) )
  }
  
  def isEmpty() : Boolean = {
    scans.length == 0
  }
  
  def calcFeatureDuration( feature: Feature ): Float = {
    calcDeltaTime(feature.relations.firstScanId,feature.relations.lastScanId)
  }
  
  def calcDeltaTime( firstSanId: Long, lastScanId: Long ): Float = {
    this.scanById(lastScanId).time - this.scanById(firstSanId).time
  }
  
}

//@JsonInclude( Include.NON_NULL )
case class LcMsScanSequenceProperties()

object LcMsScan extends InMemoryIdGen
case class LcMsScan(
    
  // Required fields
  var id: Long,
  val initialId: Int,
  val cycle: Int,
  val time: Float,
  val msLevel: Int,
  val tic: Double,
  val basePeakMoz: Double,
  val basePeakIntensity: Double,
  
  var runId: Long,
  
  // Immutable optional fields
  val precursorMoz: Option[Double] = None,
  val precursorCharge: Option[Int] = None,
  
  // Mutable optional fields
  var properties: Option[LcMsScanProperties] = None

)
            
//@JsonInclude( Include.NON_NULL )
case class LcMsScanProperties()
