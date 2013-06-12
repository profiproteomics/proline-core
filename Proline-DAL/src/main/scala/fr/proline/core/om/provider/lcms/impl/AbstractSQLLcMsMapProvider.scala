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
  
  val scans: Array[LcMsScan]
  protected val scanById = Map() ++ scans.map( s => s.id -> s )
  
  protected val lcmsDbCtx: DatabaseConnectionContext
  protected val LcMsMapCols = LcmsDbMapTable.columns
  protected val FtCols = LcmsDbFeatureTable.columns
  
  protected val lcmsDbHelper = new LcmsDbHelper(lcmsDbCtx)
  protected val featureScoringById = lcmsDbHelper.getFeatureScoringById()
  
  private def _boolToStr(ezDBC: EasyDBC, bool: Boolean) = ezDBC.dialect.booleanFormatter.formatBoolean(bool)
  
  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( runMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
      
      val olpFtIdsByFtId = new HashMap[Long,ArrayBuffer[Long]]
      val olpIdMapQuery = new SelectQueryBuilder1(LcmsDbFeatureOverlapMappingTable).mkSelectQuery( (t,c) =>
        List(t.OVERLAPPED_FEATURE_ID,t.OVERLAPPING_FEATURE_ID) ->
        "WHERE "~ t.MAP_ID ~" IN("~ runMapIds.mkString(",") ~") "
      )
      
      ezDBC.selectAndProcess( olpIdMapQuery ) { r =>
        val( overlappedFeatureId, overlappingFeatureId ) = (toLong(r.nextAny), toLong(r.nextAny))
        olpFtIdsByFtId.getOrElseUpdate(overlappedFeatureId, new ArrayBuffer[Long](1) ) += overlappingFeatureId
        ()
      }
      
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
      for( (ftId,olpFtIds) <- olpFtIdsByFtId ) { 
        mapBuilder += ( ftId -> olpFtIds.toArray )
      }
      mapBuilder.result()
      
  })

  }
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById(
    mapIds: Seq[Long],
    scanInitialIdById: Map[Long,Int],
    ms2EventIdsByFtId: Map[Long,Array[Long]]
  ): Map[Long,Feature] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
    
      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Feature]
      
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
  
  def eachFeatureRecord(mapIds: Seq[Long], onEachFt: ResultSetRow => Unit): Unit = {
    
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
    scanInitialIdById: Map[Long,Int],
    ms2EventIdsByFtId: Map[Long,Array[Long]],
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

    val ftId: Long = toLong(ftRecord.getAny(FtCols.ID))
    val firstScanId = toLong(ftRecord.getAny(FtCols.FIRST_SCAN_ID))
    val lastScanId = toLong(ftRecord.getAny(FtCols.LAST_SCAN_ID))
    val apexScanId = toLong(ftRecord.getAny(FtCols.APEX_SCAN_ID))
    val ms2EventIds = ms2EventIdsByFtId.getOrElse(ftId,null)
    val duration = scanById(lastScanId).time - scanById(firstScanId).time
    
    new Feature(
       id = ftId,
       moz = ftRecord.getDouble(FtCols.MOZ),
       intensity = toFloat(ftRecord.getAny(FtCols.INTENSITY)),
       charge = ftRecord.getInt(FtCols.CHARGE),
       elutionTime = toFloat(ftRecord.getAny(FtCols.ELUTION_TIME)),
       duration = duration,
       qualityScore = ftRecord.getDoubleOrElse(FtCols.QUALITY_SCORE,Double.NaN),
       ms1Count = ftRecord.getInt(FtCols.MS1_COUNT),
       ms2Count = ftRecord.getInt(FtCols.MS2_COUNT),
       isOverlapping = toBoolean(ftRecord.getAny(FtCols.IS_OVERLAPPING)),
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
         theoreticalFeatureId = ftRecord.getLongOrElse(FtCols.THEORETICAL_FEATURE_ID, 0L),
         compoundId = ftRecord.getLongOrElse(FtCols.COMPOUND_ID, 0L),
         mapLayerId = ftRecord.getLongOrElse(FtCols.MAP_LAYER_ID, 0L),
         mapId = toLong(ftRecord.getAny(FtCols.MAP_ID))
       )
     )
    
  }
  

}