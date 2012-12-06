package fr.proline.core.om.provider.lcms.impl

import java.util.HashMap
import scala.collection.mutable.ArrayBuffer

import fr.proline.core.om.model.lcms._
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.util.sql._

trait IMapLoader {
  
  /** If true then boolean will be stringified as integer (1|0), else as corresponding strings ("true"|"false") */
  val lcmsDb: SQLQueryHelper
  val lcmsDbTx = lcmsDb.getOrCreateTransaction
  val boolStrAsInt = false //lcmsDb.boolStrAsInt
  
  val lcmsDbHelper = new LcmsDbHelper( lcmsDb )
  val featureScoringById = lcmsDbHelper.getFeatureScoringById()
  
  def getMaps( mapIds: Seq[Int] ): Array[_]
  def getFeatures( mapIds: Seq[Int] ): Array[Feature]

  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( runMapIds: Seq[Int] ): Map[Int,Array[Int]] = {
    
    val olpFtIdsByFtId = new java.util.HashMap[Int,ArrayBuffer[Int]]
    lcmsDbTx.selectAndProcess( "SELECT overlapped_feature_id, overlapping_feature_id FROM feature_overlap_map " + 
                               "WHERE run_map_id IN (" + runMapIds.mkString(",") + ")" ) { r =>
      val( overlappedFeatureId, overlappingFeatureId ) = (r.nextInt.get, r.nextInt.get)
      if( !olpFtIdsByFtId.containsKey(overlappedFeatureId) ) {
        olpFtIdsByFtId.put(overlappedFeatureId, new ArrayBuffer[Int](1) )
      }
      olpFtIdsByFtId.get(overlappedFeatureId) += overlappingFeatureId
      ()
    }
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
    for( ftId <- olpFtIdsByFtId.keySet().toArray() ) { 
      mapBuilder += ( ftId.asInstanceOf[Int] -> olpFtIdsByFtId.get(ftId).toArray[Int] )
    }
    mapBuilder.result()

  }
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById( mapIds: Seq[Int],
                                 scanInitialIdById: Map[Int,Int],
                                 ms2EventIdsByFtId: Map[Int,Array[Int]] 
                               ): Map[Int,Feature] = {
    
    var featureColNames: Seq[String] = null
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Feature]
    
    // Load overlapping features
    lcmsDbTx.selectAndProcess( "SELECT * FROM feature WHERE map_id IN (" + mapIds.mkString(",")+") "+
                               "AND is_overlapping = " + BoolToSQLStr(true,boolStrAsInt) ) { r =>
        
      if( featureColNames == null ) { featureColNames = r.columnNames }
      
      // Build the feature record
      val ftRecord = featureColNames.map( colName => ( colName -> r.nextObject.getOrElse(null) ) ).toMap
      val mapId = ftRecord("map_id").asInstanceOf[Int]
      
      val feature = buildFeature( ftRecord, scanInitialIdById, ms2EventIdsByFtId, null, null, mapId )
      mapBuilder += ( feature.id -> feature )
      
      ()
    }
    
    mapBuilder.result()
  }
  
  def eachFeatureRecord( mapIds: Seq[Int], onEachFt: Map[String,Any] => Unit ): Unit = {
    var featureColNames: Seq[String] = null
    
    lcmsDbTx.selectAndProcess( "SELECT * FROM feature WHERE map_id IN (" + mapIds.mkString(",")+") "+
                               "AND is_overlapping = " + BoolToSQLStr(false,boolStrAsInt) ) { r =>
      
      if( featureColNames == null ) { featureColNames = r.columnNames }
      
      // Build the feature record
      val ftRecord = featureColNames.map( colName => ( colName -> r.nextObject.getOrElse(null) ) ).toMap
      onEachFt( ftRecord )
      
      ()
    }
      
  }
  
  /** Builds a feature object */
  def buildFeature( // Run map feature attributes
                    featureRecord: Map[String,Any],
                    scanInitialIdById: Map[Int,Int],
                    ms2EventIdsByFtId: Map[Int,Array[Int]],
                    isotopicPatterns: Option[Array[IsotopicPattern]],
                    overlappingFeatures: Array[Feature],
                    mapId: Int,
                    
                    // Processed map feature attributes
                    subFeatures: Array[Feature] = null,
                    children: Array[Feature] = null,
                    calibratedMoz: Double = Double.NaN,
                    normalizedIntensity: Double = Double.NaN,
                    correctedElutionTime: Float = Float.NaN,
                    isClusterized: Boolean = false,
                    selectionLevel: Int = 2
                    ): Feature = {
    
    val ftId = featureRecord("id").asInstanceOf[Int]
    val firstScanId = featureRecord("first_scan_id").asInstanceOf[Int]
    val lastScanId = featureRecord("last_scan_id").asInstanceOf[Int]
    val apexScanId = featureRecord("apex_scan_id").asInstanceOf[Int]
    val ms2EventIds = ms2EventIdsByFtId.getOrElse(ftId,null)
    
    // TODO: load serialized properties

    new Feature( id = ftId,
                 moz = featureRecord("moz").asInstanceOf[Double],
                 intensity = featureRecord("intensity").asInstanceOf[Double].toFloat,
                 charge = featureRecord("charge").asInstanceOf[Int],
                 elutionTime = featureRecord("elution_time").asInstanceOf[Double].toFloat,
                 qualityScore = featureRecord.getOrElse("quality_score",Double.NaN).asInstanceOf[Double],
                 ms1Count = featureRecord("ms1_count").asInstanceOf[Int],
                 ms2Count = featureRecord("ms2_count").asInstanceOf[Int],
                 isOverlapping = SQLStrToBool( featureRecord("is_overlapping").toString() ),
                 isotopicPatterns = isotopicPatterns,
                 overlappingFeatures = overlappingFeatures,
                 children = children,
                 subFeatures = subFeatures,
                 calibratedMoz = calibratedMoz,
                 normalizedIntensity = normalizedIntensity,
                 correctedElutionTime = correctedElutionTime,
                 isClusterized = isClusterized,
                 selectionLevel = selectionLevel,
                 relations = new FeatureRelations(
                   firstScanInitialId = scanInitialIdById(firstScanId),
                   lastScanInitialId = scanInitialIdById(lastScanId),
                   apexScanInitialId = scanInitialIdById(apexScanId),
                   ms2EventIds = ms2EventIds,
                   firstScanId = firstScanId,
                   lastScanId = lastScanId,
                   apexScanId = apexScanId,
                   theoreticalFeatureId = featureRecord.getOrElse("theoretical_feature_id",0).asInstanceOf[Int],
                   compoundId = featureRecord.getOrElse("compound_id",0).asInstanceOf[Int],
                   mapLayerId = featureRecord.getOrElse("map_layer_id",0).asInstanceOf[Int],
                   mapId = mapId
                 )
               )
    
  }
  

}