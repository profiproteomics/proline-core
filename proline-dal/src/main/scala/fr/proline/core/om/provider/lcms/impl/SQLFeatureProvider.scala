package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.ResultSetRow
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.om.model.lcms._

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
  
class SQLFeatureProvider(
  val lcmsDbCtx: LcMsDbConnectionContext,
  val loadPeaks: Boolean = false
) {

  protected val peakelProvider = new SQLPeakelProvider(lcmsDbCtx)
  protected val lcmsDbHelper = new LcmsDbHelper(lcmsDbCtx)

  protected val FtCols = LcmsDbFeatureTable.columns
  protected val ProcFtCols = LcmsDbProcessedMapFeatureItemTable.columns

  /** Returns a list of processed features  */
  // TODO: reduce code redundancy with SQLProcessedMapProvider
  def getProcessedFeatures( featureIds: Seq[Long], loadSubFeatures: Boolean = false ): Array[Feature] = {
    if( featureIds.isEmpty ) return Array()

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx) { ezDBC =>

      // --- Load processed map ids ---
      val procMapIdsSqlQuery = new SelectQueryBuilder1(LcmsDbProcessedMapFeatureItemTable).mkSelectQuery( (t1,c1) =>
        List(t1.PROCESSED_MAP_ID) -> "WHERE " ~ t1.FEATURE_ID ~ " IN (" ~ featureIds.mkString(",") ~ ") "
      ).replace("SELECT","SELECT DISTINCT")

      val processedMapIds = ezDBC.selectLongs( procMapIdsSqlQuery )

      // --- Load run ids and run map ids
      val( rawMapIds, runIds ) = ( new ArrayBuffer[Long](), new ArrayBuffer[Long]() )

      val sqlQuery = new SelectQueryBuilder2(LcmsDbRawMapTable, LcmsDbProcessedMapRawMapMappingTable).mkSelectQuery( (t1,c1,t2,c2) =>
        List(t1.ID,t1.SCAN_SEQUENCE_ID) ->
          "WHERE " ~ t2.PROCESSED_MAP_ID ~ " IN(" ~ processedMapIds.mkString(",") ~ ") " ~
            "AND " ~ t1.ID ~ "=" ~ t2.RAW_MAP_ID
      )

      ezDBC.selectAndProcess( sqlQuery ) { r =>
        rawMapIds += toLong(r.nextAny)
        runIds += toLong(r.nextAny)
        ()
      }

      // Load mapping between scan ids and scan initial ids
      val scanInitialIdById = lcmsDbHelper.getScanInitialIdById( runIds )

      // Retrieve mapping between features and MS2 scans
      val ms2EventIdsByFtId = lcmsDbHelper.getMs2EventIdsByFtId( rawMapIds )

      /*
      // Retrieve mapping between overlapping features
      val olpFtIdsByFtId = getOverlappingFtIdsByFtId( rawMapIds )
      val olpFeatureById = if (olpFtIdsByFtId.isEmpty) Map.empty[Long, Feature]
      else getOverlappingFeatureById(rawMapIds, scanInitialIdById, ms2EventIdsByFtId)
      */

      // Retrieve mapping between cluster and sub-features
      val subFtIdsByClusterFtId =  if(!loadSubFeatures) Map.empty[Long, Array[Long]]
      else getSubFtIdsByClusterFtId( featureIds )      
      val subFtIds = subFtIdsByClusterFtId.values.toArray.flatten
      
      // Load peakel items
      val peakelItems = peakelProvider.getFeaturePeakelItems(featureIds ++ subFtIds, loadPeakels = false)
      val peakelItemsByFtId = peakelItems.groupBy(_.featureReference.id)
      
      val ftBuffer = new ArrayBuffer[Feature]()
      
      def buildFeatureFromProcessedFtRecord( processedFtRecord: ResultSetRow): Feature = {
        val ftId = toLong(processedFtRecord.getAny(FtCols.ID))

        /*
        // Try to retrieve overlapping features
        val olpFeatures = if (olpFtIdsByFtId.contains(ftId) == false ) null
        else olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
        */

        // Retrieve peakel items
        val peakelItems = peakelItemsByFtId.getOrElse(ftId,Array())

        // TODO: factorize code with SQLProcessedMapProvider
        this.buildFeature(
          processedFtRecord,
          scanInitialIdById,
          ms2EventIdsByFtId,
          peakelItems,
          null, // olpFeatures
          null, // subFeatures
          null, // children
          processedFtRecord.getDoubleOption(ProcFtCols.CALIBRATED_MOZ),
          processedFtRecord.getDoubleOption(ProcFtCols.NORMALIZED_INTENSITY).map( _.toFloat ),
          processedFtRecord.getDoubleOption(ProcFtCols.CORRECTED_ELUTION_TIME).map( _.toFloat ),
          processedFtRecord.getBoolean(ProcFtCols.IS_CLUSTERIZED),
          processedFtRecord.getIntOrElse(ProcFtCols.SELECTION_LEVEL,2),
          toLong( processedFtRecord.getAny(ProcFtCols.PROCESSED_MAP_ID) )
        )
      }

      // Load processed features
      this.eachProcessedFeatureRecord(featureIds, processedFtRecord => {
        ftBuffer += buildFeatureFromProcessedFtRecord(processedFtRecord)
      })
      
      if(!loadSubFeatures) ftBuffer.toArray
      else {
        
        // Load sub-features
        val subFtById = new java.util.HashMap[Long,Feature]()
        
        this.eachProcessedFeatureRecord(subFtIds, processedFtRecord => {
          val feature = buildFeatureFromProcessedFtRecord(processedFtRecord)
          subFtById.put( feature.id, feature )
        })
        
        // Link sub-features to loaded features
        val ftArray = new Array[Feature]( ftBuffer.length )
        var ftIndex = 0
        for( ft <- ftBuffer ) {

          if( subFtIdsByClusterFtId.contains( ft.id ) ) {
            ft.subFeatures = subFtIdsByClusterFtId(ft.id) map { subFtId =>
              val subFt = subFtById.get(subFtId)
              if( subFt == null ) throw new Exception( "can't find a sub-feature with id=" + subFtId )
              subFt
            }
          }

          ftArray(ftIndex) = ft
          ftIndex += 1
        }

        ftArray
      }

    }

  }

  /** Returns a map of sub features feature keyed by its id */
  // TODO : remove code redundancy with SQLProcessedMapProvider (only the SQL Query changes)
  def getSubFtIdsByClusterFtId( featureIds: Seq[Long] ): Map[Long,Array[Long]] = {
    if( featureIds.isEmpty ) return Map()

    val subFtIdBufferByClusterFtId = new HashMap[Long,ArrayBuffer[Long]]

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>

      val ftClusterRelationQuery = new SelectQueryBuilder1(LcmsDbFeatureClusterItemTable).mkSelectQuery( (t,c) =>
        List(t.CLUSTER_FEATURE_ID,t.SUB_FEATURE_ID) ->
          "WHERE "~ t.CLUSTER_FEATURE_ID ~" IN ("~ featureIds.mkString(",") ~") "
      )

      ezDBC.selectAndProcess( ftClusterRelationQuery ) { r =>

        val( clusterFeatureId, subFeatureId ) = (toLong(r.nextAny), toLong(r.nextAny))
        subFtIdBufferByClusterFtId.getOrElseUpdate(clusterFeatureId, new ArrayBuffer[Long](1)) += subFeatureId

        ()
      }
    }

    // Convert the HashMap into an immutable Map
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Long,Array[Long]]
    for( clusterFtId <- subFtIdBufferByClusterFtId.keys ) {
      mapBuilder += ( clusterFtId -> subFtIdBufferByClusterFtId(clusterFtId).toArray[Long] )
    }
    mapBuilder.result()

  }

  def eachProcessedFeatureRecord( featureIds: Seq[Long], onEachFt: ResultSetRow => Unit ): Unit = {
    if( featureIds.isEmpty ) return ()

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>

      val procFtQuery = new SelectQueryBuilder2(LcmsDbFeatureTable, LcmsDbProcessedMapFeatureItemTable).mkSelectQuery( (t1,c1,t2,c2) =>
        List(t1.*,t2.*) ->
          "WHERE " ~ t1.ID ~ " IN (" ~ featureIds.mkString(",") ~ ") " ~
            "AND " ~ t1.ID ~ "=" ~ t2.FEATURE_ID
        // "AND is_clusterized = " + BoolToSQLStr(isClusterized,boolStrAsInt)
      )

      // Load processed map features
      ezDBC.selectAndProcess( procFtQuery ) { r =>

        // Build the feature record
        onEachFt( r )

        ()
      }

    }

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
    val mapId = toLong(ftRecord.getAny(FtCols.MAP_ID))
    val rawMapId = if( mapId == processedMapId ) 0L else mapId

    new Feature(
      id = ftId,
      moz = ftRecord.getDouble(FtCols.MOZ),
      intensity = toFloat(ftRecord.getAny(FtCols.APEX_INTENSITY)),
      charge = ftRecord.getInt(FtCols.CHARGE),
      elutionTime = toFloat(ftRecord.getAny(FtCols.ELUTION_TIME)),
      duration = toFloat(ftRecord.getAny(FtCols.DURATION)),
      qualityScore = ftRecord.getDoubleOrElse(FtCols.QUALITY_SCORE,Double.NaN).toFloat,
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
      // FIXME: feature properties can't be deserialized at the moment
      properties = ftRecord.getStringOption(FtCols.SERIALIZED_PROPERTIES.toAliasedString).map( ProfiJson.deserialize[FeatureProperties](_) ),
      //properties = None,
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
  
}