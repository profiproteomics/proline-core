package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.{ArrayBuffer,HashMap}
import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.ResultSetRow
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1}
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.FeatureProperties
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.ILcMsMap
import fr.proline.util.sql.BoolToSQLStr
import fr.proline.util.sql.StringOrBoolAsBool.string2boolean
import fr.proline.core.om.provider.lcms.ILcMsMapProvider
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureOverlapMappingTable

abstract class AbstractSQLLcMsMapProvider extends ILcMsMapProvider {
  
  val boolStrAsInt: Boolean
  val lcmsSqlCtx: SQLConnectionContext
  val sqlExec = lcmsSqlCtx.ezDBC
  val lcmsDbHelper = new LcmsDbHelper( sqlExec )
  val featureScoringById = lcmsDbHelper.getFeatureScoringById()
  val FtCols = LcmsDbFeatureTable.columns

  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( runMapIds: Seq[Int] ): Map[Int,Array[Int]] = {
    
    val olpFtIdsByFtId = new HashMap[Int,ArrayBuffer[Int]]
    val olpIdMapQuery = new SelectQueryBuilder1(LcmsDbFeatureOverlapMappingTable).mkSelectQuery( (t,c) =>
      List(t.OVERLAPPED_FEATURE_ID,t.OVERLAPPING_FEATURE_ID) ->
      "WHERE "~ t.MAP_ID ~" IN("~ runMapIds.mkString(",") ~") "
    )
    
    sqlExec.selectAndProcess( olpIdMapQuery ) { r =>
      val( overlappedFeatureId, overlappingFeatureId ) = (r.nextInt, r.nextInt)
      olpFtIdsByFtId.getOrElseUpdate(overlappedFeatureId, new ArrayBuffer[Int](1) ) += overlappingFeatureId
      ()
    }
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
    for( (ftId,olpFtIds) <- olpFtIdsByFtId ) { 
      mapBuilder += ( ftId -> olpFtIds.toArray )
    }
    mapBuilder.result()

  }
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById( mapIds: Seq[Int],
                                 scanInitialIdById: Map[Int,Int],
                                 ms2EventIdsByFtId: Map[Int,Array[Int]] 
                               ): Map[Int,Feature] = {
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Feature]
    
    // Load overlapping features
    val olpFtQuery = new SelectQueryBuilder1(LcmsDbFeatureTable).mkSelectQuery( (t,c) =>
      List(t.*) ->
      "WHERE "~ t.MAP_ID ~" IN("~ mapIds.mkString(",") ~") "~
      "AND "~ t.IS_OVERLAPPING ~"="~ BoolToSQLStr(true,boolStrAsInt)
    )
    
    sqlExec.selectAndProcess( olpFtQuery ) { r =>
      
      val feature = buildFeature( r, scanInitialIdById, ms2EventIdsByFtId )
      mapBuilder += ( feature.id -> feature )
      
      ()
    }
    
    mapBuilder.result()
  }
  
  def eachFeatureRecord( mapIds: Seq[Int], onEachFt: ResultSetRow => Unit ): Unit = {
    
    // Iterate over features (is_overlapping = false)
    val ftQuery = new SelectQueryBuilder1(LcmsDbFeatureTable).mkSelectQuery( (t,c) =>
      List(t.*) ->
      "WHERE "~ t.MAP_ID ~" IN("~ mapIds.mkString(",") ~") "~
      "AND "~ t.IS_OVERLAPPING ~"="~ BoolToSQLStr(false,boolStrAsInt)
    )
    
    sqlExec.selectAndProcess( ftQuery ) { r =>
      onEachFt( r )
      ()
    }
      
  }
  
  /** Builds a feature object */
  def buildFeature( // Run map feature attributes
                    ftRecord: ResultSetRow,
                    scanInitialIdById: Map[Int,Int],
                    ms2EventIdsByFtId: Map[Int,Array[Int]],
                    isotopicPatterns: Option[Array[IsotopicPattern]] = None,
                    overlappingFeatures: Array[Feature] = null,
                    
                    // Processed map feature attributes
                    subFeatures: Array[Feature] = null,
                    children: Array[Feature] = null,
                    calibratedMoz: Double = Double.NaN,
                    normalizedIntensity: Double = Double.NaN,
                    correctedElutionTime: Float = Float.NaN,
                    isClusterized: Boolean = false,
                    selectionLevel: Int = 2
                    ): Feature = {
    
    import fr.proline.util.sql.StringOrBoolAsBool._
    import fr.proline.util.primitives._

    val ftId: Int = toInt(ftRecord.getAnyVal(FtCols.ID))
    val firstScanId = ftRecord.getInt(FtCols.FIRST_SCAN_ID)
    val lastScanId = ftRecord.getInt(FtCols.LAST_SCAN_ID)
    val apexScanId = ftRecord.getInt(FtCols.APEX_SCAN_ID)
    val ms2EventIds = ms2EventIdsByFtId.getOrElse(ftId,null)
    
    new Feature(
       id = ftId,
       moz = ftRecord.getDouble(FtCols.MOZ),
       intensity = toFloat(ftRecord.getAnyVal(FtCols.INTENSITY)),
       charge = ftRecord.getInt(FtCols.CHARGE),
       elutionTime = toFloat(ftRecord.getAnyVal(FtCols.ELUTION_TIME)),
       qualityScore = ftRecord.getDoubleOrElse(FtCols.QUALITY_SCORE,Double.NaN),
       ms1Count = ftRecord.getInt(FtCols.MS1_COUNT),
       ms2Count = ftRecord.getInt(FtCols.MS2_COUNT),
       isOverlapping = ftRecord.getAnyVal(FtCols.IS_OVERLAPPING),
       isotopicPatterns = isotopicPatterns,
       overlappingFeatures = overlappingFeatures,
       children = children,
       subFeatures = subFeatures,
       calibratedMoz = calibratedMoz,
       normalizedIntensity = normalizedIntensity,
       correctedElutionTime = correctedElutionTime,
       isClusterized = isClusterized,
       selectionLevel = selectionLevel,
       properties = ftRecord.getStringOption(FtCols.SERIALIZED_PROPERTIES).map( parse[FeatureProperties](_) ),
       relations = new FeatureRelations(
         firstScanInitialId = scanInitialIdById(firstScanId),
         lastScanInitialId = scanInitialIdById(lastScanId),
         apexScanInitialId = scanInitialIdById(apexScanId),
         ms2EventIds = ms2EventIds,
         firstScanId = firstScanId,
         lastScanId = lastScanId,
         apexScanId = apexScanId,
         theoreticalFeatureId = ftRecord.getIntOrElse(FtCols.THEORETICAL_FEATURE_ID, 0),
         compoundId = ftRecord.getIntOrElse(FtCols.COMPOUND_ID, 0),
         mapLayerId = ftRecord.getIntOrElse(FtCols.MAP_LAYER_ID, 0),
         mapId = ftRecord.getInt(FtCols.MAP_ID)
       )
     )
    
  }
  

}