package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.{ArrayBuffer, HashMap}

import fr.profi.jdbc.ResultSetRow
import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.sql.StringOrBoolAsBool._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{SelectQueryBuilder1}
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.ILcMsMapProvider

abstract class AbstractSQLLcMsMapProvider extends ILcMsMapProvider {
  
  val scans: Array[LcMsScan]
  protected val scanById = Map() ++ scans.map( s => s.id -> s )
  
  protected val lcmsDbCtx: DatabaseConnectionContext
  protected val LcMsMapCols = LcmsDbMapColumns
  protected val FtCols = LcmsDbFeatureColumns
  protected val PeakelCols = LcmsDbPeakelColumns
  protected val PeakelItemCols = LcmsDbFeaturePeakelItemColumns
  
  protected val lcmsDbHelper = new LcmsDbHelper(lcmsDbCtx)
  protected val featureScoringById = lcmsDbHelper.getFeatureScoringById()
  
  private def _boolToStr(ezDBC: EasyDBC, bool: Boolean) = ezDBC.dialect.booleanFormatter.formatBoolean(bool)
  
  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( rawMapIds: Seq[Long] ): Map[Long,Array[Long]] = {
    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { ezDBC =>
      
      val olpFtIdsByFtId = new HashMap[Long,ArrayBuffer[Long]]
      val olpIdMapQuery = new SelectQueryBuilder1(LcmsDbFeatureOverlapMappingTable).mkSelectQuery( (t,c) =>
        List(t.OVERLAPPED_FEATURE_ID,t.OVERLAPPING_FEATURE_ID) ->
        "WHERE "~ t.MAP_ID ~" IN("~ rawMapIds.mkString(",") ~") "
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
  
  protected def eachFeatureRecord(mapIds: Seq[Long], onEachFt: ResultSetRow => Unit): Unit = {
    
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
  protected def buildFeature(
    // Raw map feature attributes
    ftRecord: ResultSetRow,
    scanInitialIdById: Map[Long,Int],
    ms2EventIdsByFtId: Map[Long,Array[Long]],
    peakelItems: Array[FeaturePeakelItem] = null,
    overlappingFeatures: Array[Feature] = null,
    
    // Processed map feature attributes
    subFeatures: Array[Feature] = null,
    children: Array[Feature] = null,
    calibratedMoz: Option[Double] = None,
    normalizedIntensity: Option[Float] = None,
    correctedElutionTime: Option[Float] = None,
    isClusterized: Boolean = false,
    selectionLevel: Int = 2,
    processedMapId: Long = 0L
  ): Feature = {

    val ftId: Long = toLong(ftRecord.getAny(FtCols.ID))
    val firstScanId = toLong(ftRecord.getAny(FtCols.FIRST_SCAN_ID))
    val lastScanId = toLong(ftRecord.getAny(FtCols.LAST_SCAN_ID))
    val apexScanId = toLong(ftRecord.getAny(FtCols.APEX_SCAN_ID))
    val ms2EventIds = ms2EventIdsByFtId.getOrElse(ftId,null)
    // TODO : Retrieve duration from feature table
    val duration = scanById(lastScanId).time - scanById(firstScanId).time
    val mapId = toLong(ftRecord.getAny(FtCols.MAP_ID))
    val rawMapId = if( mapId == processedMapId ) 0L else mapId
    
    new Feature(
       id = ftId,
       moz = ftRecord.getDouble(FtCols.MOZ),
       intensity = toFloat(ftRecord.getAny(FtCols.APEX_INTENSITY)),
       charge = ftRecord.getInt(FtCols.CHARGE),
       elutionTime = toFloat(ftRecord.getAny(FtCols.ELUTION_TIME)),
       duration = duration,
       qualityScore = ftRecord.getDoubleOrElse(FtCols.QUALITY_SCORE,Double.NaN),
       ms1Count = ftRecord.getInt(FtCols.MS1_COUNT),
       ms2Count = ftRecord.getInt(FtCols.MS2_COUNT),
       isOverlapping = toBoolean(ftRecord.getAny(FtCols.IS_OVERLAPPING)),
       overlappingFeatures = overlappingFeatures,
       children = children,
       subFeatures = subFeatures,
       calibratedMoz = calibratedMoz,
       normalizedIntensity = normalizedIntensity,
       correctedElutionTime = correctedElutionTime,
       isClusterized = isClusterized,
       selectionLevel = selectionLevel,
       properties = ftRecord.getStringOption(FtCols.SERIALIZED_PROPERTIES).map( ProfiJson.deserialize[FeatureProperties](_) ),
       relations = new FeatureRelations(
         peakelItems = peakelItems,
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
         rawMapId = rawMapId,
         processedMapId = processedMapId
       )
     )
    
  }
  
  def getPeakels(rawMapIds: Seq[Long]): Array[Peakel] = {

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val mapIdsStr = rawMapIds.mkString(",")
      
      // Check that provided map ids correspond to raw maps
      val nbMaps = ezDBC.selectInt("SELECT count(id) FROM raw_map WHERE id IN (" + mapIdsStr + ")")
      require(nbMaps == rawMapIds.length, "map ids must correspond to existing run maps")
      
      // Build peakels SQL query
      val peakelQuery = new SelectQueryBuilder1(LcmsDbPeakelTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.MAP_ID ~" IN("~ rawMapIds.mkString(",") ~") "
      )
      
      // Iterate over peakels
      ezDBC.select( peakelQuery ) { r =>
        
        // TODO: use the buildPeakel method from PeakelProvider
        
        // Read and deserialize peaks
        val peaksAsBytes = r.getBytes(PeakelCols.PEAKS)
        val lcMsPeaks = org.msgpack.ScalaMessagePack.read[Array[LcMsPeak]](peaksAsBytes)
        
        // Read and deserialize properties
        val propsAsJSON = r.getStringOption(PeakelCols.SERIALIZED_PROPERTIES)
        val propsOpt = propsAsJSON.map( ProfiJson.deserialize[PeakelProperties](_) )
        
        Peakel(
          id = r.getLong(PeakelCols.ID),
          moz = r.getDouble(PeakelCols.MOZ),
          elutionTime = toFloat(r.getAny(PeakelCols.ELUTION_TIME)),
          apexIntensity = toFloat(r.getAny(PeakelCols.APEX_INTENSITY)),
          area = toFloat(r.getAny(PeakelCols.APEX_INTENSITY)),
          duration = toFloat(r.getAny(PeakelCols.DURATION)),
          fwhm = r.getAnyOption(PeakelCols.FWHM).map(toFloat(_)),
          isOverlapping = toBoolean(r.getAny(PeakelCols.IS_OVERLAPPING)),
          featuresCount = r.getInt(PeakelCols.FEATURE_COUNT),
          peaks = lcMsPeaks,
          firstScanId = r.getLong(PeakelCols.FIRST_SCAN_ID),
          lastScanId = r.getLong(PeakelCols.LAST_SCAN_ID),
          apexScanId = r.getLong(PeakelCols.APEX_SCAN_ID),
          rawMapId = r.getLong(PeakelCols.MAP_ID),
          properties = propsOpt
        )

      } toArray

    })

  }
  
  protected def getPeakelItemsByFeatureId(rawMapIds: Seq[Long]): Map[Long,Seq[FeaturePeakelItem]] = {

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val mapIdsStr = rawMapIds.mkString(",")
      
      // Build feature peakel items SQL query
      val peakelItemQuery = new SelectQueryBuilder1(LcmsDbFeaturePeakelItemTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.MAP_ID ~" IN("~ rawMapIds.mkString(",") ~") "
      )
      
      val peakelItemsByFtId = new HashMap[Long,ArrayBuffer[FeaturePeakelItem]]()
      
      ezDBC.selectAndProcess( peakelItemQuery ) { r =>
        
        // TODO: use the buildFeaturePeakelItem method from PeakelProvider
        
        // Read and deserialize properties
        val propsAsJSON = r.getStringOption(PeakelItemCols.SERIALIZED_PROPERTIES)
        val propsOpt = propsAsJSON.map( ProfiJson.deserialize[FeaturePeakelItemProperties](_) )
        
        val peakelItem = FeaturePeakelItem(
          peakelReference = PeakelIdentifier( r.getLong(PeakelItemCols.PEAKEL_ID) ),
          isotopeIndex = r.getInt(PeakelItemCols.ISOTOPE_INDEX),
          properties = propsOpt
        )
        
        val ftId = r.getLong(PeakelItemCols.FEATURE_ID)
        peakelItemsByFtId.getOrElseUpdate(ftId, new ArrayBuffer[FeaturePeakelItem]) += peakelItem
      }
      
      peakelItemsByFtId.toMap
    })
    
  }

}