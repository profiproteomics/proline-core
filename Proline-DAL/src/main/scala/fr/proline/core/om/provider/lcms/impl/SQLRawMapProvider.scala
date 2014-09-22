package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{ SelectQueryBuilder1, SelectQueryBuilder2 }
import fr.proline.core.dal.tables.lcms.{ LcmsDbMapTable, LcmsDbRawMapTable }
import fr.proline.core.dal.helper.LcmsDbHelper
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IRawMapProvider
import fr.profi.util.sql._
import fr.profi.util.primitives._

class SQLRawMapProvider(
  val lcmsDbCtx: DatabaseConnectionContext,
  val scans: Array[LcMsScan],
  val loadPeaks: Boolean = false
) extends AbstractSQLLcMsMapProvider with IRawMapProvider {

  val RawMapCols = LcmsDbRawMapTable.columns

  protected val peakPickingSoftwareById = lcmsDbHelper.getPeakPickingSoftwareById()
  protected val peakelFittingModelById = lcmsDbHelper.getPeakelFittingModelById()

  /** Returns a list of LC-MS maps corresponding to a given list of raw map ids */
  def getLcMsMaps(mapIds: Seq[Long]): Seq[ILcMsMap] = this.getRawMaps(mapIds)

  /** Returns a list of run maps corresponding to a given list of raw map ids */
  def getRawMaps(rawMapIds: Seq[Long]): Array[RawMap] = {
    if( rawMapIds.isEmpty ) return Array()

    val features = this.getFeatures(rawMapIds)
    // Group features by map id
    val featuresByMapId = features.groupBy(_.relations.rawMapId)

    val rawMaps = new Array[RawMap](rawMapIds.length)
    var lcmsMapIdx = 0

    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>

      val rawMapQuery = new SelectQueryBuilder2(LcmsDbMapTable, LcmsDbRawMapTable).mkSelectQuery((t1, c1, t2, c2) =>
        List(t1.*, t2.SCAN_SEQUENCE_ID, t2.PEAK_PICKING_SOFTWARE_ID, t2.PEAKEL_FITTING_MODEL_ID) ->
          "WHERE " ~ t2.ID ~ " IN(" ~ rawMapIds.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.ID
      )

      // Load processed map features
      ezDBC.selectAndProcess(rawMapQuery) { r =>

        val mapId = toLong(r.getAny(LcMsMapCols.ID))
        val featureScoringId = toLong(r.getAny(LcMsMapCols.FEATURE_SCORING_ID))
        val peakPickingSoftwareId = toLong(r.getAny(RawMapCols.PEAK_PICKING_SOFTWARE_ID))
        val peakelFittingModelId = toLong(r.getAny(RawMapCols.PEAKEL_FITTING_MODEL_ID))

        val mapFeatures = featuresByMapId(mapId)
        val featureScoring = featureScoringById.get(featureScoringId)
        val peakPickingSoftware = peakPickingSoftwareById(peakPickingSoftwareId)
        val peakelFittingModel = peakelFittingModelById.get(peakelFittingModelId)

        // Build the map
        rawMaps(lcmsMapIdx) = new RawMap(
          id = mapId,
          name = r.getString(LcMsMapCols.NAME),
          description = r.getStringOrElse(LcMsMapCols.DESCRIPTION, ""),
          isProcessed = false,
          creationTimestamp = r.getTimestamp(LcMsMapCols.CREATION_TIMESTAMP),
          features = features,
          runId = toLong(r.getAny(RawMapCols.SCAN_SEQUENCE_ID)),
          peakPickingSoftware = peakPickingSoftware,
          featureScoring = featureScoring,
          peakelFittingModel = peakelFittingModel
        )
        
        lcmsMapIdx += 1
      }
    })

    rawMaps
  }

  /** Returns a list of features corresponding to a given list of run map ids */
  def getFeatures(mapIds: Seq[Long]): Array[Feature] = {
    if( mapIds.isEmpty ) return Array()

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val mapIdsStr = mapIds.mkString(",")
      
      // Check that provided map ids correspond to run maps
      val nbMaps = ezDBC.selectInt("SELECT count(id) FROM run_map WHERE id IN (" + mapIdsStr + ")")
      if (nbMaps < mapIds.length) throw new Exception("map ids must correspond to existing run maps")
      
      // Load run ids
      val runIdsQuery = new SelectQueryBuilder1(LcmsDbRawMapTable).mkSelectQuery( (t1, c1) =>
        List(t1.SCAN_SEQUENCE_ID) -> "WHERE " ~ t1.ID ~ " IN(" ~ mapIdsStr ~ ") "
      )
      val runIds = ezDBC.selectLongs( runIdsQuery )
      
      // Load mapping between scan ids and scan initial ids
      val scanInitialIdById = lcmsDbHelper.getScanInitialIdById(runIds)

      // Retrieve mapping between features and MS2 scans
      val ms2EventIdsByFtId = lcmsDbHelper.getMs2EventIdsByFtId(mapIds)

      // TODO: load isotopic patterns if needed
      //val ipsByFtId = if( loadPeaks ) getIsotopicPatternsByFtId( mapIds ) else null

      // Retrieve mapping between overlapping features
      val olpFtIdsByFtId = getOverlappingFtIdsByFtId(mapIds)

      var olpFeatureById: Map[Long, Feature] = null
      if (olpFtIdsByFtId.size > 0) {
        olpFeatureById = getOverlappingFeatureById(mapIds, scanInitialIdById, ms2EventIdsByFtId)
      }
      
      val ftBuffer = new ArrayBuffer[Feature]

      this.eachFeatureRecord(mapIds, ftRecord => {
        val ftId = toLong(ftRecord.getAny(FtCols.ID))

        // Try to retrieve overlapping features
        var olpFeatures: Array[Feature] = null
        if (olpFtIdsByFtId.contains(ftId)) {
          olpFeatures = olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
        }
        
        // TODO: load isotopic patterns if needed
        val feature = buildFeature(ftRecord, scanInitialIdById, ms2EventIdsByFtId, null, olpFeatures)

        ftBuffer += feature
      })

      ftBuffer.toArray

    })

  }

  /*
  /** Builds a feature object coming from a run map */
  def buildRunMapFeature( featureRecord: ResultSetRow,
                          scanInitialIdById: Map[Int,Int],
                          ms2EventIdsByFtId: Map[Int,Array[Int]],
                          isotopicPatterns: Option[Array[IsotopicPattern]],
                          overlappingFeatures: Array[Feature]
                          ): Feature = {
    
    buildFeature( featureRecord, scanInitialIdById, ms2EventIdsByFtId, isotopicPatterns, overlappingFeatures )

  }*/

}
