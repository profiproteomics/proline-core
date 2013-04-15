package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.{ArrayBuffer,HashMap}
import com.codahale.jerkson.Json.parse
import fr.profi.jdbc.ResultSetRow
import fr.profi.jdbc.easy.EasyDBC
import fr.proline.util.sql.StringOrBoolAsBool._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1}
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureOverlapMappingTable
import fr.proline.core.dal.tables.lcms.LcmsDbMapTable
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.ILcMsMapProvider
import fr.proline.util.sql.StringOrBoolAsBool.string2boolean
import fr.proline.util.primitives._

abstract class AbstractSQLLcMsMapProvider extends ILcMsMapProvider {
  
  protected val lcmsDbCtx: DatabaseConnectionContext
  protected val LcMsMapCols = LcmsDbMapTable.columns
  protected val FtCols = LcmsDbFeatureTable.columns
  
  protected val lcmsDbHelper = new LcmsDbHelper(lcmsDbCtx)
  protected val featureScoringById = lcmsDbHelper.getFeatureScoringById()
  
  private def _boolToStr(ezDBC: EasyDBC, bool: Boolean) = ezDBC.dialect.booleanFormatter.formatBoolean(bool)
  
  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( runMapIds: Seq[Int] ): Map[Int,Array[Int]] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
      
      val olpFtIdsByFtId = new HashMap[Int,ArrayBuffer[Int]]
      val olpIdMapQuery = new SelectQueryBuilder1(LcmsDbFeatureOverlapMappingTable).mkSelectQuery( (t,c) =>
        List(t.OVERLAPPED_FEATURE_ID,t.OVERLAPPING_FEATURE_ID) ->
        "WHERE "~ t.MAP_ID ~" IN("~ runMapIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( olpIdMapQuery ) { r =>
        val( overlappedFeatureId, overlappingFeatureId ) = (r.nextInt, r.nextInt)
        olpFtIdsByFtId.getOrElseUpdate(overlappedFeatureId, new ArrayBuffer[Int](1) ) += overlappingFeatureId
        ()
      }
      
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[Int]]
      for( (ftId,olpFtIds) <- olpFtIdsByFtId ) { 
        mapBuilder += ( ftId -> olpFtIds.toArray )
      }
      mapBuilder.result()
      
  })

  }
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById(
    mapIds: Seq[Int],
    scanInitialIdById: Map[Int,Int],
    ms2EventIdsByFtId: Map[Int,Array[Int]]
  ): Map[Int,Feature] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Feature]
      
      // Load overlapping features
      val olpFtQuery = new SelectQueryBuilder1(LcmsDbFeatureTable).mkSelectQuery( (t,c) =>
        List(t.*) ->
        "WHERE "~ t.MAP_ID ~" IN("~ mapIds.mkString(",") ~") "~
        "AND "~ t.IS_OVERLAPPING ~"="~ _boolToStr(ezDBC,true)
      )
      
      ezDBC.selectAndProcess( olpFtQuery ) { r =>
        
        val feature = buildFeature( r, scanInitialIdById, ms2EventIdsByFtId )
        mapBuilder += ( feature.id -> feature )
        
        ()
      }
      
      mapBuilder.result()
    
    })
  }
  
  def eachFeatureRecord(mapIds: Seq[Int], onEachFt: ResultSetRow => Unit): Unit = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      // Iterate over features (is_overlapping = false)
      val ftQuery = new SelectQueryBuilder1(LcmsDbFeatureTable).mkSelectQuery( (t,c) =>
        List(t.*) ->
        "WHERE "~ t.MAP_ID ~" IN("~ mapIds.mkString(",") ~") "~
        "AND "~ t.IS_OVERLAPPING ~"="~ _boolToStr(ezDBC,false)
      )
      
      ezDBC.selectAndProcess( ftQuery ) { r =>
        onEachFt( r )
        ()
      }
    
    })
      
  }
  
  /** Builds a feature object */
  def buildFeature(
    // Run map feature attributes
    ftRecord: ResultSetRow,
    scanInitialIdById: Map[Int,Int],
    ms2EventIdsByFtId: Map[Int,Array[Int]],
    isotopicPatterns: Option[Array[IsotopicPattern]] = None,
    overlappingFeatures: Array[Feature] = null,
    
    // Processed map feature attributes
    subFeatures: Array[Feature] = null,
    children: Array[Feature] = null,
    calibratedMoz: Option[Double] = None,
    normalizedIntensity: Option[Float] = None,
    correctedElutionTime: Option[Float] = None,
    isClusterized: Boolean = false,
    selectionLevel: Int = 2
  ): Feature = {

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