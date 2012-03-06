package fr.proline.core.om.model.lcms

import java.util.Date
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.helper.MiscUtils.InMemoryIdGen

case class FeatureScoring(
    
            // Required fields
            val id: Int,
            val name: String,
            val description: String,
  
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            )

case class PeakPickingSoftware(
    
            // Required fields
            val id: Int,
            val name: String,
            val version: String,
            val algorithm: String,
  
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            )

case class PeakelFittingModel( 
    
            // Required fields
            val id: Int,
            val name: String,
            
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            )

case class MapMozCalibration( 
    
            // Required fields
            val id: Int,
            val mozList: Array[Double],
            val deltaMozList: Array[Double],
            
            val mapId: Int,
            val scanId: Int,
            
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) {
  // Requirements
  require( mozList != null && deltaMozList != null )
}

sealed abstract class LcmsMap(
            
            // Required fields
            //val id: Int,
            val name: String,
            val isProcessed: Boolean,
            val creationTimestamp: Date,
            val features: Array[Feature],
            
            // Immutable optional fields
            val description: String = null,
            val featureScoring: FeatureScoring = null
            
            ) {
  // Requirements
  require( creationTimestamp != null && features != null )
}

object RunMap extends InMemoryIdGen

case class RunMap(
            
            // Required fields
            var id: Int,
            override val name: String,
            override val isProcessed: Boolean,
            override val creationTimestamp: Date,
            override val features: Array[Feature],
            
            val runId: Int,
            val peakPickingSoftware: PeakPickingSoftware,
            
            // Immutable optional fields
            override val description: String = null,
            override val featureScoring: FeatureScoring = null,
            
            val peakelFittingModel: PeakelFittingModel = null,
            
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) extends LcmsMap( name, isProcessed, creationTimestamp, features, description, featureScoring ) {
  
  // Requirements
  require( peakPickingSoftware != null )
  
  def toProcessedMap( id: Int, number: Int, mapSetId: Int, features: Array[Feature] = this.features ) = {
    
    val curTime = new Date()
    
    ProcessedMap( id = id,
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
                  runMapIds = Array( id ),
                  mapSetId = mapSetId
                  )
    
  }
  
}

object ProcessedMap extends InMemoryIdGen

case class ProcessedMap(
            
            // Required fields
            var id: Int,
            override val name: String,
            override val isProcessed: Boolean,
            override val creationTimestamp: Date,
            override val features: Array[Feature],
            
            val number: Int,
            var modificationTimestamp: Date,
            val isMaster: Boolean,
            var isAlnReference: Boolean,
            
            val mapSetId: Int,
            var runMapIds: Array[Int], // Many values only for a master map
            
            // Immutable optional fields
            override val description: String = null,
            override val featureScoring: FeatureScoring = null,              
            
            // Mutable optional fields
            var isLocked: Boolean = false,
            var normalizationFactor: Float = 1,
            var mozCalibrations: Option[Array[MapMozCalibration]] = None, // m/z calibration matrix for the entire run
            
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) extends LcmsMap( name, isProcessed, creationTimestamp, features, description, featureScoring ) {
  
  // Requirements
  require( modificationTimestamp != null )
  if( !isMaster ) require( runMapIds.length == 1 )
  
  def copyWithoutClusters(): ProcessedMap = {
    
    val featuresWithoutClusters = new ArrayBuffer[Feature]( features.length )
    
    for( val ft <- features ) {
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
            val fromMapId: Int,
            val toMapId: Int,
            val massRange: Tuple2[Double,Double],
            val timeList: Array[Float],
            val deltaTimeList: Array[Float],
  
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) {
  
  // Requirements
  require( massRange != null && timeList != null && deltaTimeList != null )
  
  def getLandmarks(): Array[Landmark] = {
    
    var landmarks = new ArrayBuffer[Landmark](timeList.length)
    (timeList, deltaTimeList).zipped foreach { (time, deltaTime) =>
      landmarks += Landmark( time , deltaTime )
    }
    
    landmarks.toArray
    
  }
  
  def calcReferenceElutionTime( elutionTime: Float ) = {
    
    import fr.proline.core.om.helper.MiscUtils.calcLineParams
    
    val timeIndex = timeList.indexWhere( _ >= elutionTime )
    //if( timeIndex == -1 ) throw new Exception("undefined time index for elution time " + elutionTime)
    
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
      val( x1, y1 ) = ( timeList(timeIndex-1), deltaTimeList(timeIndex-1) );
      val( x2, y2) = ( timeList(timeIndex) , deltaTimeList(timeIndex) );        
      
      val ( a, b ) = calcLineParams( x1, y1, x2, y2 )
      deltaTime = (a * elutionTime + b).toFloat;
    }
    
    // Delta = aln_map - ref_map
    elutionTime - deltaTime
    
  }
  
 
  def getReversedAlignment(): MapAlignment = {      
    MapAlignment(
      fromMapId = toMapId,
      toMapId = fromMapId,
      massRange = massRange,
      timeList = timeList,
      deltaTimeList = deltaTimeList map { _ * -1 }               
    )
    
  }
  
  
}

case class MapAlignmentSet(
    
            // Required fields
            val fromMapId: Int,
            val toMapId: Int,
            val mapAlignments: Array[MapAlignment],
  
            // Mutable optional fields
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) {
  
  // Requirements
  require( mapAlignments != null )
  
  def calcReferenceElutionTime( elutionTime: Float, mass: Double ): Float = {

    // Select right map alignment
    var mapAln = mapAlignments find { x => mass >= x.massRange._1 && mass < x.massRange._2 }
    // Small workaround for masses greater than the biggest map alignment
    if( mapAln == None ) { mapAln = Some(mapAlignments.last) }
    
    // Convert aligned map elution time into reference map one
    mapAln.get.calcReferenceElutionTime( elutionTime )
  }
  
  def getReversedAlnSet(): MapAlignmentSet = {
    
    MapAlignmentSet(
      fromMapId = toMapId,
      toMapId = fromMapId,
      mapAlignments = mapAlignments map { _.getReversedAlignment }
    )

  }
  
}

case class MapSet(
    
            // Required fields
            val id: Int,
            val name: String,
            val creationTimestamp: Date,
            val childMaps: Array[ProcessedMap],
            
            // Mutable optional fields
            var masterMap: ProcessedMap = null,
            var alnReferenceMapId: Int = 0,
            var mapAlnSets: Array[MapAlignmentSet] = null,
            
            var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
            
            ) {
  
  // Requirements
  require( creationTimestamp != null && childMaps != null )


  def getChildMapIds() = childMaps map { _.id }

  def getRunMapIds(): Array[Int] = {
  
    val runMapIds = new ArrayBuffer[Int](childMaps.length)
    for( childMap <- childMaps ) {
      if( !childMap.isProcessed ) { runMapIds += childMap.id }
      else { runMapIds ++= childMap.runMapIds }
    }
    
    runMapIds.toArray
  }

  def getNormalizationFactorByMapId: Map[Int,Float] = { 
    childMaps.map( childMap => ( childMap.id -> childMap.normalizationFactor ) ).toMap
  }
  
  def getAlnReferenceMap(): Option[ProcessedMap] = {      
    if( alnReferenceMapId == 0 ) None
    else childMaps find { _.id == alnReferenceMapId }
  }
  
  def getRefMapAlnSetByMapId(): Option[Map[Int,MapAlignmentSet]] = {
    if( alnReferenceMapId == 0 ) return None
    
    // Retrieve alignments of the reference map
    val refMapAlnSets = mapAlnSets filter { _.fromMapId == alnReferenceMapId }
    val revRefMapAlnSets = mapAlnSets . 
                           filter { _.toMapId == alnReferenceMapId } . 
                           map { _.getReversedAlnSet }
    
    val refMapAlnSetByMapId = ( refMapAlnSets ++ revRefMapAlnSets ) .
                              map( alnSet => ( alnSet.toMapId -> alnSet ) ).toMap
                              
    Some(refMapAlnSetByMapId)
    
  }
  
}

