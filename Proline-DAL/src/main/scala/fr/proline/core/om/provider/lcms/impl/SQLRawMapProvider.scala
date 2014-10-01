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
  def getLcMsMaps(mapIds: Seq[Long], loadPeakels: Boolean): Seq[ILcMsMap] = this.getRawMaps(mapIds)

  /** Returns a list of run maps corresponding to a given list of raw map ids */
  def getRawMaps(rawMapIds: Seq[Long], loadPeakels: Boolean = false ): Array[RawMap] = {
    if( rawMapIds.isEmpty ) return Array()

    // Load features and group them by map id
    val featuresByMapId = this.getFeatures(rawMapIds).groupBy(_.relations.rawMapId)
    
    // If requested load peakels and group them by map id
    val peakelsByRawMapId = if( loadPeakels == false ) Map.empty[Long,Array[Peakel]]
    else peakelProvider.getPeakels(rawMapIds).groupBy(_.rawMapId)
    
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
        
        val featureScoring = featureScoringById.get(featureScoringId)
        val peakPickingSoftware = peakPickingSoftwareById(peakPickingSoftwareId)
        val peakelFittingModel = peakelFittingModelById.get(peakelFittingModelId)
        
        val peakelsOpt = if( loadPeakels == false ) None
        else Some( peakelsByRawMapId(mapId) )

        // Build the map
        rawMaps(lcmsMapIdx) = new RawMap(
          id = mapId,
          name = r.getString(LcMsMapCols.NAME),
          description = r.getStringOrElse(LcMsMapCols.DESCRIPTION, ""),
          isProcessed = false,
          creationTimestamp = r.getTimestamp(LcMsMapCols.CREATION_TIMESTAMP),
          features = featuresByMapId(mapId),
          peakels = peakelsOpt,
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
      
      // Check that provided map ids correspond to raw maps
      val nbMaps = ezDBC.selectInt("SELECT count(id) FROM raw_map WHERE id IN (" + mapIdsStr + ")")
      require(nbMaps == mapIds.length, "map ids must correspond to existing raw maps")
      
      // Load scan sequence ids
      val scanSeqIdsQuery = new SelectQueryBuilder1(LcmsDbRawMapTable).mkSelectQuery( (t1, c1) =>
        List(t1.SCAN_SEQUENCE_ID) -> "WHERE " ~ t1.ID ~ " IN(" ~ mapIdsStr ~ ") "
      )
      val scanSeqIds = ezDBC.selectLongs( scanSeqIdsQuery )
      
      // Load mapping between scan ids and scan initial ids
      val scanInitialIdById = lcmsDbHelper.getScanInitialIdById(scanSeqIds)

      // Retrieve mapping between features and MS2 scans
      val ms2EventIdsByFtId = lcmsDbHelper.getMs2EventIdsByFtId(mapIds)
      
      // Retrieve mapping between overlapping features
      val olpFtIdsByFtId = getOverlappingFtIdsByFtId(mapIds)
      val olpFeatureById = if (olpFtIdsByFtId.isEmpty) Map.empty[Long, Feature]
      else getOverlappingFeatureById(mapIds, scanInitialIdById, ms2EventIdsByFtId)
      
      // Load peakel items
      val peakelItems = peakelProvider.getRawMapPeakelItems(mapIds, loadPeakels = false)
      val peakelItemsByFtId = peakelItems.groupBy(_.featureReference.id)
      
      val ftBuffer = new ArrayBuffer[Feature]

      this.eachFeatureRecord(mapIds, ftRecord => {
        val ftId = toLong(ftRecord.getAny(FtCols.ID))

        // Try to retrieve overlapping features
        val olpFeatures = if (olpFtIdsByFtId.contains(ftId) == false ) null
        else olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
        
        // Retrieve peakel items
        val peakelItems = peakelItemsByFtId(ftId).toArray
        
        val feature = buildFeature(ftRecord, scanInitialIdById, ms2EventIdsByFtId, peakelItems, olpFeatures)

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
