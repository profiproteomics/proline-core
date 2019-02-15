package fr.proline.core.om.model.lcms

import java.util.Date

import fr.profi.util.misc.InMemoryIdGen

case class RawFile( 
  
  // Required fields
  val identifier: String,
  val name: String,

  // Immutable optional fields  
  val directory: Option[String] = None,
  val mzdbFileName: Option[String] = None,
  val mzdbFileDirectory: Option[String] = None,
  val sampleName: Option[String] = None,
  val creationTimestamp: Option[Date] = None,

  // Mutable optional fields
  var properties: Option[RawFileProperties] = None
) {

  def getPath(): Option[String] = directory.map( _ + "/" + name)
  
  def getMzdbFilePath(): Option[String] = {
    if( mzdbFileName.isDefined ) mzdbFileDirectory.map( _ + "/" + mzdbFileName.get) else None
  }
}

// TODO: remove me when database data are migrated
case class RawFileProperties(
  //@deprecated("use RawFile.mzdbFileDirectory and RawFile.mzdbFileName instead","0.4.1") 
  //@BeanProperty var mzdbFilePath: String
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
  
  val rawFileIdentifier: String,
  val minIntensity: Double,
  val maxIntensity: Double,
  val ms1ScansCount: Int,
  val ms2ScansCount: Int,
  val scans: Array[LcMsScan],

  // Mutable optional fields
  var properties: Option[LcMsScanSequenceProperties] = None
  
) {
  require( scans != null, "scans is null" )
  require( isSorted(scans.map(_.time)) == true, "scans must be sorted by time" )
  
  // TODO: put in Scala commons ?
  private def isSorted(values: Array[Float]): Boolean = {
    for (i <- 1 until values.length) if (values(i) < values(i-1)) return false
    true
  }
  
  lazy val scanById = Map() ++ scans.map { scan => ( scan.id -> scan ) }
  
  lazy val scanIdByInitialId = Map() ++ scans.map { scan => ( scan.initialId -> scan.id ) }
  
  lazy val endTime: Float = scans.last.time
  
  lazy val scansByTimeIndex: Map[Int,Array[LcMsScan]] = {
    scans.groupBy( scan => LcMsScanSequence.calcTimeIndex(scan.time) )
  }
  
  private lazy val _scanByTime = Map() ++ scans.map { scan => ( scan.time -> scan ) }
  
  def getScanByInitialId( initialId: Int ): Option[LcMsScan] = {
    for( scanId <- scanIdByInitialId.get(initialId) ) yield scanById(scanId)
  }

  def getScanAtTime( time: Float, msLevel: Int = 1 ): LcMsScan = {
    //require( time >= 0, s"time must be a positive number ($time)" )
    
    // Try to retrieve scan by exact time matching
    val scanOptForTime = _scanByTime.get(time)
    if (scanOptForTime.isDefined && scanOptForTime.get.msLevel == msLevel) scanOptForTime.get
    // Else fallback to lookup in the scansByTimeIndex HashMap
    else {
      val safeTime = math.min( math.max(scans.head.time, time), scans.last.time )
      var timeIndex = LcMsScanSequence.calcTimeIndex(safeTime)
      val maxTimeIndex = timeIndex + 1
      if (timeIndex > 0) timeIndex -= 1
      
      val scansByIndex = scansByTimeIndex
      
      var nearestScan: LcMsScan = null
      var lowestDeltaTime: Float = Float.MaxValue
      while (timeIndex <= maxTimeIndex) {
        val scansOpt = scansByIndex.get(timeIndex)
        if (scansOpt.isDefined) {
          val scans = scansOpt.get
          for (scan <- scans; if scan.msLevel == msLevel) {
            val time = scan.time
            val deltaTime = Math.abs(time - safeTime)
            if (deltaTime < lowestDeltaTime) {
              lowestDeltaTime = deltaTime
              nearestScan = scan
            }
            // TODO: optimize the search by breaking when delta increases after found minimum
          }
        }
        timeIndex += 1
      }
      
      nearestScan
    }
    
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
