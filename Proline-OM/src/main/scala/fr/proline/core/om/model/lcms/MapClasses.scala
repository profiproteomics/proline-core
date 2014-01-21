package fr.proline.core.om.model.lcms

import java.util.Date
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.beans.BeanProperty

//import com.fasterxml.jackson.annotation.JsonInclude
//import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.util.misc.InMemoryIdGen

case class FeatureScoring(
    
  // Required fields
  val id: Long,
  val name: String,
  val description: String,

  // Mutable optional fields
  var properties: Option[FeatureScoringProperties] = None
  
)

//@JsonInclude( Include.NON_NULL )
case class FeatureScoringProperties

object PeakPickingSoftware extends InMemoryIdGen

case class PeakPickingSoftware(
    
  // Required fields
  val id: Long,
  val name: String,
  val version: String,
  val algorithm: String,

  // Mutable optional fields
  var properties: Option[PeakPickingSoftwareProperties] = None

)

//@JsonInclude( Include.NON_NULL )
case class PeakPickingSoftwareProperties

case class PeakelFittingModel( 
    
  // Required fields
  val id: Long,
  val name: String,
  
  // Mutable optional fields
  var properties: Option[PeakelFittingModelProperties] = None
  
)

//@JsonInclude( Include.NON_NULL )
case class PeakelFittingModelProperties

case class MapMozCalibration( 
    
  // Required fields
  val id: Long,
  val mozList: Array[Double],
  val deltaMozList: Array[Double],
  
  val mapId: Long,
  val scanId: Long,
  
  // Mutable optional fields
  var properties: Option[MapMozCalibrationProperties] = None
  
) {
  // Requirements
  require( mozList != null && deltaMozList != null )
}

//@JsonInclude( Include.NON_NULL )
case class MapMozCalibrationProperties

//@JsonInclude( Include.NON_NULL )
case class LcMsMapProperties

abstract class ILcMsMap {
  
  // Required fields
  //val id: Long,
  val name: String
  val isProcessed: Boolean
  val creationTimestamp: Date
  val features: Array[Feature]
  
  // Immutable optional fields
  val description: String
  val featureScoring: Option[FeatureScoring]
  
  // Mutable optional fields
  var properties: Option[LcMsMapProperties]
  
  require( creationTimestamp != null && features != null )
  
  // Debug purpose
  def toTsvFile( filePath: String ) {
    import java.io.FileOutputStream
    import java.io.PrintWriter
    val file = new java.io.File(filePath)
    val writer = new PrintWriter(file)
    
    val header = "feature_id mass moz charge is_cluster elution_time correct_elution_time duration raw_abundance ms2_count"
    writer.println(header.replaceAll(" ", "\t"))
    
    for( ft <- features ) {
      val row: List[Any] = List(
        ft.id,ft.mass,ft.moz,ft.charge.toString,ft.isCluster,
        ft.elutionTime,ft.correctedElutionTime,ft.duration,
        ft.intensity,ft.ms2Count.toString
      )
      writer.println( row.mkString("\t"))
      writer.flush()
    }
    
    writer.close()
  }

}

object RawMap extends InMemoryIdGen

// TODO:  move in Scala Commons ???
trait IEntityIdentifier {
  def id: Long
}
case class Identifier( var id: Long ) extends IEntityIdentifier

case class RawMap(
            
  // Required fields
  var id: Long,
  val name: String,
  val isProcessed: Boolean,
  val creationTimestamp: Date,
  val features: Array[Feature],
  
  var runId: Long,
  val peakPickingSoftware: PeakPickingSoftware,
  
  // Immutable optional fields
  val description: String = "",
  val featureScoring: Option[FeatureScoring] = None,
  
  val peakelFittingModel: Option[PeakelFittingModel] = None,
  
  // Mutable optional fields
  var properties: Option[LcMsMapProperties] = None
  
) extends ILcMsMap with IEntityIdentifier {
  
  // Requirements
  require( peakPickingSoftware != null, "a pick peaking software must be provided" )
  //require( features.count(_.correctedElutionTime.isDefined) == 0, "can't use processed map features as run map features" )
  
  def toProcessedMap( number: Int, mapSetId: Long, features: Array[Feature] = this.features ) = {
    
    val procMapId = ProcessedMap.generateNewId
    val curTime = new Date()
    
    // Update the processed map id of each feature
    features.foreach { ft =>
      ft.relations.processedMapId = procMapId
    }
    
    ProcessedMap(
      id = procMapId,
      number = number,
      name = name,
      description = description,
      creationTimestamp = curTime,
      modificationTimestamp = curTime,
      isProcessed = true,
      isMaster = false,
      isAlnReference = false,
      features = features,
      featureScoring = featureScoring,
      rawMapIdentifiers = List( this ),
      runId = Some(runId),
      mapSetId = mapSetId
    )
    
  }
  
}

object ProcessedMap extends InMemoryIdGen

case class ProcessedMap(
            
  // Required fields
  var id: Long,
  val name: String,
  val isProcessed: Boolean,
  val creationTimestamp: Date,
  // TODO: another model may be to separate features from feature clusters (it may avoid some array copy)
  val features: Array[Feature],
  
  val number: Int,
  var modificationTimestamp: Date,
  val isMaster: Boolean,
  var isAlnReference: Boolean,
  
  var mapSetId: Long,
  //@transient val rawMaps: Array[RawMap], // Many values only for a master map
  @transient var rawMapIdentifiers: Seq[IEntityIdentifier],
  
  // Immutable optional fields
  val description: String = "",
  val featureScoring: Option[FeatureScoring] = None,
  
  // Mutable optional fields
  var runId: Option[Long] = None,
  var isLocked: Boolean = false,
  var normalizationFactor: Float = 1,
  var mozCalibrations: Option[Array[MapMozCalibration]] = None, // m/z calibration matrix for the entire run
  
  var properties: Option[LcMsMapProperties] = None
  
) extends ILcMsMap {
  
  // Requirements
  require( modificationTimestamp != null )
  if( !isMaster ) require( rawMapIdentifiers.length == 1 )
  
  def getRawMapIds(): Seq[Long] = rawMapIdentifiers.map(_.id)
  
  // TODO: note this is a way to generalize to MSI OM
  def getRawMaps(): Seq[Option[RawMap]] = {
    rawMapIdentifiers.map { rawMapIdentifier =>
      rawMapIdentifier match {
        case rawMap: RawMap => Some(rawMap)
        case _ => None
      }
    }
  }
  
  def copyWithoutClusters(): ProcessedMap = {
    
    val featuresWithoutClusters = new ArrayBuffer[Feature]( features.length )
    
    for(ft <- features ) {
      if( !ft.isCluster ) { featuresWithoutClusters += ft }
      else { featuresWithoutClusters ++= ft.subFeatures }
    }
    
    this.copy( features = featuresWithoutClusters.toArray )
    
  }
  
  def copyWithSelectedFeatures(): ProcessedMap = {
     
    val selectedFeatures = features filter { _.selectionLevel >= 2 }
    this.copy( features = selectedFeatures )
    
  }
  
}

case class Landmark( time: Float, deltaTime: Float )

case class MapAlignment(
    
  // Required fields
  val refMapId: Long,
  val targetMapId: Long,
  val massRange: Tuple2[Float,Float],
  val timeList: Array[Float],
  val deltaTimeList: Array[Float],

  // Mutable optional fields
  var properties: Option[MapAlignmentProperties] = None
  
) extends Logging {
  
  // Requirements
  require( massRange != null && timeList != null && deltaTimeList != null )
  this._checkSlopes()
  
  // Define some lazy vals
  lazy val deltaTimeVersusTime = timeList.zip(deltaTimeList)
  
  private def _checkSlopes(): Unit = {
    
    deltaTimeVersusTime.sliding(2).foreach { lmPair =>
      require(lmPair(1)._1 > lmPair(0)._1,"MapAlignment must contain only strictly increasing time values")
      
      val targetTime1 = lmPair(0)._1 + lmPair(0)._2
      val targetTime2 = lmPair(1)._1 + lmPair(1)._2
      //val delaTimeDiff = lmPair(1)._2 - lmPair(0)._2
      
      //val slope = delaTimeDiff / timeDiff
      //if( slope >= 1 ) println(lmPair(0)._1 + " "+lmPair(0)._2)
      //if( slope >= 1 ) println(lmPair(1)._1 + " "+lmPair(1)._2)
      require(targetTime2 > targetTime1,"MapAlignment must contain only strictly increasing (time + delta time) values")
      
    }
    
    ()
  }
  
  def getLandmarks(): Array[Landmark] = {
    
    var landmarks = new ArrayBuffer[Landmark](timeList.length)
    deltaTimeVersusTime.foreach { case (time, deltaTime) =>
      landmarks += Landmark( time , deltaTime )
    }
    
    landmarks.toArray
    
  }
  
  /*
  @deprecated("0.0.9","can't compute reference time using a time list of the reference map")
  def calcReferenceElutionTime( elutionTime: Float ): Float = {
    // Delta = aln_map - ref_map
    elutionTime - this.calcDeltaTime(elutionTime)
  }*/
  
  /**
   * Converts an elution time using the time list of the reference map (refMap)
   * and the corresponding delta time list allowing to compute targetMap elution times.
   * 
   * @param refMapTime The time to convert (must be a in the refMap scale).
   * @return The elution time converted in the targetMap scale.
   */
  def calcTargetMapElutionTime( refMapTime: Float ): Float = {    
    // Delta = aln_map - ref_map
    refMapTime + this.calcDeltaTime(refMapTime)
  }
  
  protected def calcDeltaTime( elutionTime: Float ): Float = {
    
    import fr.proline.util.math.linearInterpolation
    
    linearInterpolation(elutionTime, deltaTimeVersusTime, false)
    
    /*var index = deltaTimeVersusTime.indexWhere( _._1 >= elutionTime )
    if( index == -1  ) {
      index = if( elutionTime < deltaTimeVersusTime.head._1 ) 0 else deltaTimeVersusTime.length - 1
    }
    
    this._calcDeltaTime( index, elutionTime )*/
  }
  
  /*
  private def _calcDeltaTime( timeIndex: Int, elutionTime: Float ) = {
    require( timeIndex >= -1 && timeIndex < deltaTimeList.length, "time index is out of range" )
    
    import fr.proline.util.math.calcLineParams
    
    var deltaTime: Float = 0
    
    // If we are looking left-side of the alignment boundaries
    // We take the delta time of the first landmark
    if( timeIndex == 0  ) {
      deltaTime = deltaTimeList(0)
    // Else if we are looking right-side of the alignment boundaries
    // We take the delta time of the last landmark
    } else if( timeIndex == -1 ) { // || highTimeIndex + 1 > timeList.length
      deltaTime = deltaTimeList.last
    // Else we are inside the  alignment boundaries
    // We compute the linear interpolation
    } else {
      val( x1, y1 ) = ( timeList(timeIndex-1), deltaTimeList(timeIndex-1) )
      val( x2, y2) = ( timeList(timeIndex) , deltaTimeList(timeIndex) )
      
      if( x1 == x2 ) deltaTime = (y1 + y2)/2
      else {
        val ( a, b ) = calcLineParams( x1, y1, x2, y2 )
        //println("a="+a +" b="+ b + "y="+ (a * elutionTime + b).toFloat )
        deltaTime = (a * elutionTime + b).toFloat
      }

    }
    
    deltaTime    
  }*/
 
  def getReversedAlignment(): MapAlignment = {
    
    val nbLandmarks = timeList.length
    val revTimeList = new Array[Float](nbLandmarks)
    val revDeltaTimeList = new Array[Float](nbLandmarks)
    
    for( i <- 0 until nbLandmarks) {
      val deltaTime = deltaTimeList(i)
      val targetMapTime = timeList(i) + deltaTime
      revTimeList(i) = targetMapTime
      revDeltaTimeList(i) = -deltaTime
    }
    
    val tmpAln = MapAlignment(
      refMapId = targetMapId,
      targetMapId = refMapId,
      massRange = massRange,
      timeList = revTimeList,
      deltaTimeList = revDeltaTimeList             
    )
    
    tmpAln
    /*val landmarks = tmpAln.getLandmarks
    
    // Keep only correctly ordered landmarks (time greater than previous one)
    val filteredLandmarks = new ArrayBuffer[Landmark]
    var curTime = 0f
    landmarks.foreach { lm =>
      if( lm.time > curTime ) {
        curTime = lm.time
        filteredLandmarks += lm
      }
    }
    
    if( filteredLandmarks.length < landmarks.length ) {
      tmpAln.copy(
        timeList = filteredLandmarks.map( _.time ).toArray,
        deltaTimeList = filteredLandmarks.map( _.deltaTime ).toArray
      )
    } else tmpAln*/
    
  }
  
}

//@JsonInclude( Include.NON_NULL )
case class MapAlignmentProperties

case class MapAlignmentSet(
    
  // Required fields
  val refMapId: Long,
  val targetMapId: Long,
  val mapAlignments: Array[MapAlignment],

  // Mutable optional fields
  var properties: Option[MapAlignmentSetProperties] = None
  
) {
  
  // Requirements
  require( mapAlignments != null )
  
  /*
  @deprecated("0.0.9","can't compute reference time using a time list of the reference map")
  def calcReferenceElutionTime( elutionTime: Float, mass: Double ): Float = {

    // Select right map alignment
    var mapAln = mapAlignments find { x => mass >= x.massRange._1 && mass < x.massRange._2 }
    // Small workaround for masses greater than the biggest map alignment
    if( mapAln.isEmpty ) { mapAln = Some(mapAlignments.last) }
    
    // Convert aligned map elution time into reference map one
    mapAln.get.calcReferenceElutionTime( elutionTime )
  }*/
  
  /**
   * Converts an elution time using the time list of the reference map (refMap)
   * and the corresponding delta time list allowing to compute targetMap elution times.
   * 
   * @param fromMapTime The elution time to convert (must be in the fromMap scale).
   * @param mass A mass value which may be used to select the appropriate map alignment.
   * @return The elution time converted in the targetMap scale.
   */
  def calcTargetMapElutionTime( refMapTime: Float, mass: Option[Double] ): Float = {

    val mapAln = if( mapAlignments.length == 0 ) mapAlignments(0)
    else {
      // Select right map alignment
      val foundMapAln = mass.map { m => mapAlignments find { x => m >= x.massRange._1 && m < x.massRange._2 } } getOrElse(None)
        
      // Small workaround for masses greater than the map alignment with highest number of data points
      if( foundMapAln.isDefined ) foundMapAln.get    
      else mapAlignments.sortBy( _.timeList.length).last
    }
    
    // Convert reference map elution time into the target map one
    mapAln.calcTargetMapElutionTime( refMapTime )
  }
  
  def getReversedAlnSet(): MapAlignmentSet = {
    
    MapAlignmentSet(
      refMapId = targetMapId,
      targetMapId = refMapId,
      mapAlignments = mapAlignments map { _.getReversedAlignment }
    )

  }
  
}

//@JsonInclude( Include.NON_NULL )
case class MapAlignmentSetProperties

object MapSet extends InMemoryIdGen

case class MapSet(
    
  // Required fields
  val id: Long,
  val name: String,
  val creationTimestamp: Date,
  val childMaps: Array[ProcessedMap],
  
  // Mutable optional fields
  var masterMap: ProcessedMap = null,
  protected var alnReferenceMapId: Long = 0,
  var mapAlnSets: Array[MapAlignmentSet] = null,
  
  var properties: Option[MapSetProperties] = None
  
  ) {
  
  // Requirements
  require( creationTimestamp != null && childMaps != null )
  
  private lazy val _mapAlnSetByMapIdPair: Map[Pair[Long,Long],MapAlignmentSet] = {
    val allMapAlnSets = mapAlnSets ++ mapAlnSets.map(_.getReversedAlnSet)
    Map() ++ allMapAlnSets.map( alnSet => (alnSet.refMapId,alnSet.targetMapId) -> alnSet )    
  }
  
  def getChildMapIds() = childMaps map { _.id }

  def getRawMapIds(): Array[Long] = {
  
    val rawMapIds = new ArrayBuffer[Long](childMaps.length)
    for( childMap <- childMaps ) {
      if( childMap.isProcessed == false ) { rawMapIds += childMap.id }
      else { rawMapIds ++= childMap.getRawMapIds }
    }
    
    rawMapIds.toArray
  }
  
  def getProcessedMapIdByRawMapId = {
    (for( childMap <- childMaps; if childMap.isProcessed; rawMapId <- childMap.getRawMapIds ) yield rawMapId -> childMap.id).toMap
  }

  def getNormalizationFactorByMapId: Map[Long,Float] = {
    childMaps.map( childMap => ( childMap.id -> childMap.normalizationFactor ) ).toMap
  }
  
  def getAlnReferenceMap(): Option[ProcessedMap] = {
    if( alnReferenceMapId == 0 ) None
    else childMaps find { _.id == alnReferenceMapId }
  }
  
  def getAlnReferenceMapId = alnReferenceMapId
  
  def setAlnReferenceMapId( alnRefMapId: Long ) = {
    this.alnReferenceMapId = alnRefMapId
    
    val alnRefMapOpt = this.getAlnReferenceMap
    require(alnRefMapOpt.isDefined,"unkown map with id="+alnRefMapId)
    
    alnRefMapOpt.get.isAlnReference = true
  }
  
  def convertElutionTime( time: Float, refMapId: Long, targetMapId: Long, mass: Option[Double] = None): Float = {
    require( mapAlnSets != null, "can't convert elution time without map alignments" )
    
    // If the reference is the target map => returns the provided time
    if( refMapId == targetMapId ) return time
    
    // If we have an alignment between the reference and the target
    if( _mapAlnSetByMapIdPair.contains(refMapId->targetMapId) ) {
      val mapAlnSet = _mapAlnSetByMapIdPair(refMapId->targetMapId)
      mapAlnSet.calcTargetMapElutionTime(time, mass)
    // Else we need to make to consecutive time conversions
    } else {
      // Convert time into the reference map scale
      val toRefMapAlnSet = _mapAlnSetByMapIdPair(refMapId -> this.alnReferenceMapId)
      val refMapTime = toRefMapAlnSet.calcTargetMapElutionTime(time, mass)
     
      // Convert reference map time into the target map scale
      val mapAlnSet = _mapAlnSetByMapIdPair(this.alnReferenceMapId -> targetMapId)
      mapAlnSet.calcTargetMapElutionTime(refMapTime, mass)
    }

  }
  
  def rebuildMasterFeaturesUsingBestChild() {
    val nbMasterFeatures = this.masterMap.features.length
    if( nbMasterFeatures == 0 ) return ()
    
    val newMasterFeatures = new ArrayBuffer[Feature](nbMasterFeatures)
    for( mft <- this.masterMap.features ) {
      
      val mftChildren = mft.children
      
      // Memorize the peptide id
      val peptideId = mft.relations.peptideId
      
      // Retrieve the highest feature child    
      val highestFtChild = mftChildren.reduceLeft { (a,b) => 
        if( a.getNormalizedIntensityOrIntensity > b.getNormalizedIntensityOrIntensity ) a else b
      }
      
      // Re-build the master feature using this best child
      val newMft = highestFtChild.toMasterFeature( id = mft.id, children = mft.children )
      
      // Restore the peptide id
      newMft.relations.peptideId = peptideId
      
      // Append this master of the list of master features
      newMasterFeatures += newMft
    }
    
    this.masterMap = this.masterMap.copy(features = newMasterFeatures.toArray)
  }
  
  def rebuildChildMaps(): MapSet = {
    
    val masterFeatures = this.masterMap.features
    
    // --- Update the map set processed maps ---
    val ftsByChildMapId = new HashMap[Long,ArrayBuffer[Feature]]
    val childMapById = new HashMap[Long,ProcessedMap]
    
    for( childMap <- childMaps ) {
      childMapById += childMap.id -> childMap
      ftsByChildMapId += childMap.id -> new ArrayBuffer(masterFeatures.length)
    }
    
    // Group master features children by child map id
    for( mft <- masterFeatures; childFt <- mft.children ) {
      ftsByChildMapId(childFt.relations.processedMapId) += childFt
    }
    
    // Re-build the processed maps
    val newChildMaps = new ArrayBuffer[ProcessedMap]
    for( (childMapId,features) <- ftsByChildMapId ) {
      
      features.foreach { ft =>
        ft.eachSubFeatureOrThisFeature { subFt =>
          require(subFt.relations.processedMapId == childMapId)
        }
      }
      
      val childMap = childMapById(childMapId)
      newChildMaps += childMap.copy( features = features.distinct.toArray )
    }
    
    this.copy( childMaps = newChildMaps.toArray )
  }
  
  // Debug purpose
  def toTsvFile( filePath: String ) {
    import java.io.FileOutputStream
    import java.io.PrintWriter
    val file = new java.io.File(filePath)
    val writer = new PrintWriter(file)
    
    val masterMapHeader = "master_feature_id mass charge elution_time peptide_id"
    val childMapHeader = "feature_id moz is_cluster elution_time correct_elution_time duration raw_abundance ms2_count"
    writer.print(masterMapHeader.replaceAll(" ", "\t"))
    for( childMap <- childMaps ) {
      writer.print("\t"+childMapHeader.replaceAll(" ", "\t"))
    }
    writer.println()
    
    val childMapIds = this.getChildMapIds
    
    for( mft <- masterMap.features ) {
      
      val mftCells: List[Any] = List(
        mft.id,mft.mass,mft.charge,
        mft.elutionTime,mft.relations.peptideId
      )
      writer.print( mftCells.mkString("\t"))
      
      val childFtByMapId = Map() ++ mft.children.map( ft => ft.relations.processedMapId -> ft )
      for( childMapId <- childMapIds ) {
        val ftOpt = childFtByMapId.get(childMapId)
        if( ftOpt.isDefined ) {
          val ft = ftOpt.get
          val ftCells: List[Any] = List(
            ft.id,ft.moz,ft.isCluster,
            ft.elutionTime,ft.correctedElutionTime,ft.duration,
            ft.intensity,ft.ms2Count.toString
          )
          writer.print( "\t"+ ftCells.mkString("\t") )
        } else writer.print( "\t"+ Array.fill(8)("").mkString("\t") )
      }
      writer.println()
      writer.flush()
    }
    
    writer.close()
  }
  
  /*
  @deprecated("0.0.9","use map set convertElutionTime method instead")
  def getRefMapAlnSetByMapId(): Option[Map[Long,MapAlignmentSet]] = {
    if( this.alnReferenceMapId == 0 ) return None
    
    val refMapAlnSetByMapId = this._getRefMapAlnSets.map( alnSet => ( alnSet.targetMapId -> alnSet ) ).toMap
    
    Some(refMapAlnSetByMapId)
  }
  
  private def _getRefMapAlnSets(): Array[MapAlignmentSet] = {    
    
    // Retrieve alignments of the reference map
    val refMapAlnSets = mapAlnSets filter { _.refMapId == alnReferenceMapId }
    val revRefMapAlnSets = mapAlnSets . 
                           filter { _.targetMapId == alnReferenceMapId } . 
                           map { _.getReversedAlnSet }
    
    ( refMapAlnSets ++ revRefMapAlnSets )
    
  }*/

}

//@JsonInclude( Include.NON_NULL )
case class MapSetProperties


