package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import org.postgresql.copy.CopyIn
import org.postgresql.copy.CopyManager
import org.postgresql.core.Utils

import com.typesafe.scalalogging.StrictLogging

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.MathUtils
import fr.profi.util.bytes._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.sql._
import fr.profi.util.primitives._

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IFeatureWriter
import fr.proline.repository.util.PostgresUtils

object PgConstants {
  val DOUBLE_PRECISION = 1e-11 // note: this is the precision we observe when using PgCopy (maybe toString is involved)
}

class PgFeatureWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IFeatureWriter with StrictLogging {
  
  private val allFeatureTableCols = LcmsDbFeatureTable.columnsAsStrList.mkString(",")
  private val featureTableColsWithoutPK = LcmsDbFeatureTable.columnsAsStrList.filter(_ != "id").mkString(",")
  
  private val ftOverlapMappingTableCols = LcmsDbFeatureOverlapMappingTable.columnsAsStrList.mkString(",")
  private val ftMs2EventTableCols = LcmsDbFeatureMs2EventTable.columnsAsStrList.mkString(",")
  
  private val allPeakelTableCols = LcmsDbPeakelTable.columnsAsStrList.mkString(",")
  private val peakelTableColsWithoutPK = LcmsDbPeakelTable.columnsAsStrList.filter(_ != "id").mkString(",")
  
  private val ftPeakelItemTableCols = LcmsDbFeaturePeakelItemTable.columnsAsStrList.mkString(",")

  def insertFeatures(rawMap: RawMap): ArrayBuffer[Feature] = {
    
    val newRawMapId = rawMap.id
    
    val flattenedFeatures = new ArrayBuffer[Feature](rawMap.features.length)
    
    DoJDBCWork.withConnection(lcmsDbCtx) { con =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(con)

      // Create TMP table
      val tmpFeatureTableName = "tmp_feature_" + (scala.math.random * 1000000).toInt
      logger.info(s"creating temporary table '$tmpFeatureTableName'...")
      
      val lcmsEzDBC = ProlineEzDBC(con, lcmsDbCtx.getDriverType)
      lcmsEzDBC.execute(
        s"CREATE TEMP TABLE $tmpFeatureTableName (LIKE ${LcmsDbFeatureTable.name}) ON COMMIT DROP"
      )

      // Bulk insert of features
      logger.info("BULK insert of features")
      
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $tmpFeatureTableName ( $allFeatureTableCols ) FROM STDIN")
      
      // Iterate over the raw map features to store them
      for (ft <- rawMap.features) {
        
        // Update feature raw map id
        ft.relations.rawMapId = newRawMapId
        
        this.insertFeatureUsingCopyManager(ft, pgBulkLoader)
        //ft.id = newFtId
        
        flattenedFeatures += ft
  
        // Import overlapping features
        if (ft.overlappingFeatures != null) {
          for (olpFt <- ft.overlappingFeatures) {
  
            val newFtId = this.insertFeatureUsingCopyManager(olpFt, pgBulkLoader)
            //olpFt.id = newFtId
  
            flattenedFeatures += olpFt
          }
        }
      }
      
      // End of BULK copy
      val nbInsertedFeatures = pgBulkLoader.endCopy()
      
      // Move TMP table content to MAIN table
      logger.info(s"move TMP table $tmpFeatureTableName into MAIN ${LcmsDbFeatureTable.name} table")
      
      lcmsEzDBC.execute(
        s"INSERT INTO ${LcmsDbFeatureTable.name} ($featureTableColsWithoutPK) " +
        s"SELECT $featureTableColsWithoutPK FROM $tmpFeatureTableName"
      )

      // Retrieve generated feature ids
      logger.info(s"Retrieving generated feature ids...")
      
      val idMzPairs = lcmsEzDBC.select(
        s"SELECT id, moz FROM ${LcmsDbFeatureTable.name} WHERE map_id = $newRawMapId"
      ) { r => Tuple2( r.nextLong, r.nextDouble ) }
      
      val featuresCount = flattenedFeatures.length
      assert(
        idMzPairs.length == featuresCount,
        s"invalid number of retrieved feature ids: got ${idMzPairs.length} but expected $featuresCount"
      )
      
      val sortedIdMzPairs = idMzPairs.sortBy(_._1)
      
      // Update feature ids
      var featureIdx = 0
      while( featureIdx < featuresCount ) {
        val feature = flattenedFeatures(featureIdx)
        val idMzPair = sortedIdMzPairs(featureIdx)
        assert(
          feature.moz == idMzPair._2,
          s"error while trying to update feature id, m/z values are different: was ${feature.moz} and is now ${idMzPair._2}"
        )
        
        feature.id = idMzPair._1
        
        featureIdx += 1
      }
      
      logger.info(s"Linking overlapping features to features...")
      
      // Link the features to overlapping features
      this._linkFeaturesToOverlappingFeatures(rawMap, bulkCopyManager)
      
      logger.info(s"Linking features to MS2 scans...")
      
      // Link the features to MS2 scans
      this._linkFeaturesToMs2Scans(newRawMapId, flattenedFeatures, bulkCopyManager)
    }
    
    flattenedFeatures
  }

  @inline
  protected def insertFeatureUsingCopyManager(ft: Feature, pgBulkLoader: CopyIn): Unit = {
    
    val ftRelations = ft.relations
    val qualityScore = if (ft.qualityScore.isNaN) None else Some(ft.qualityScore)
    val theoFtId = if (ftRelations.theoreticalFeatureId == 0) None else Some(ftRelations.theoreticalFeatureId)
    val compoundId = if (ftRelations.compoundId == 0) None else Some(ftRelations.compoundId)
    val mapLayerId = if (ftRelations.mapLayerId == 0) None else Some(ftRelations.mapLayerId)
    val mapId = ft.getSourceMapId
    require( mapId > 0, "the feature must be associated with a persisted LC-MS Map" )
    
    // Build a row containing feature values
    val featureValues = List(
      ft.id,
      ft.moz,
      ft.charge,
      ft.elutionTime,
      ft.apexIntensity,
      ft.intensity,
      ft.duration,
      qualityScore,
      ft.ms1Count,
      ft.ms2Count,
      ftRelations.peakelsCount,
      ft.isCluster,
      ft.isOverlapping,
      ft.properties.map(ProfiJson.serialize(_)),
      ftRelations.firstScanId,
      ftRelations.lastScanId,
      ftRelations.apexScanId,
      theoFtId,
      compoundId,
      mapLayerId,
      mapId
    )
    
    // Store the feature
    val featureBytes = encodeRecordForPgCopy(featureValues, false)
    pgBulkLoader.writeToCopy(featureBytes, 0, featureBytes.length)
  }
  
  private def _linkFeaturesToOverlappingFeatures(rawMap: RawMap, bulkCopyManager: CopyManager): Unit = {

    val rawMapId = rawMap.id
    
    val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbFeatureOverlapMappingTable.name} ( $ftOverlapMappingTableCols ) FROM STDIN")
    
    // Iterate over overlappingFeatures to store the corresponding mapping
    rawMap.features.foreach { ft =>
      if (ft.overlappingFeatures != null) {
        for (olpFt <- ft.overlappingFeatures) {
          
          // Build a row containing feature_overlap_mapping values
          val ftOverlapMappingValues = List(
            ft.id,
            olpFt.id,
            rawMapId
          )
          
          // Store the mapping
          val ftOverlapMappingBytes = encodeRecordForPgCopy(ftOverlapMappingValues)
          pgBulkLoader.writeToCopy(ftOverlapMappingBytes, 0, ftOverlapMappingBytes.length)
        }
      }
    }

    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
  }
  
  private def _linkFeaturesToMs2Scans(rawMapId: Long, flattenedFeatures: Seq[Feature], bulkCopyManager: CopyManager): Unit = {

    val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbFeatureMs2EventTable.name} ( $ftMs2EventTableCols ) FROM STDIN")
    
    flattenedFeatures.foreach { ft =>
      if (ft.relations.ms2EventIds != null) {
        for (ms2EventId <- ft.relations.ms2EventIds) {
          
          // Build a row containing feature_ms2_event values
          val ftMs2EventValues = List(
            ft.id,
            ms2EventId,
            rawMapId
          )
          
          // Store the mapping
          val ftMs2EventBytes = encodeRecordForPgCopy(ftMs2EventValues)
          pgBulkLoader.writeToCopy(ftMs2EventBytes, 0, ftMs2EventBytes.length)
        }
      }
    }
    
    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
  }
  
  // TODO: do this using PgCopy
  def insertPeakels(rawMap: RawMap, features: Seq[Feature]): Unit = {
    
    val newRawMapId = rawMap.id
    
    DoJDBCWork.withConnection(lcmsDbCtx) { con =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(con)

      // Create TMP table
      val tmpPeakelTableName = "tmp_peakel_" + (scala.math.random * 1000000).toInt
      logger.info(s"creating temporary table '$tmpPeakelTableName'...")
      
      val lcmsEzDBC = ProlineEzDBC(con, lcmsDbCtx.getDriverType)
      lcmsEzDBC.execute(
        s"CREATE TEMP TABLE $tmpPeakelTableName (LIKE ${LcmsDbPeakelTable.name}) ON COMMIT DROP"
      )

      // Bulk insert of features
      logger.info("BULK insert of peakels")
      
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $tmpPeakelTableName ( $allPeakelTableCols ) FROM STDIN")
      
      // Iterate over the raw map peakels to store them
      val peakels = rawMap.peakels.get
      for( peakel <- peakels ) {
        
        // Update peakel raw map id
        peakel.rawMapId = newRawMapId
        
        this.insertPeakelUsingCopyManager(peakel, pgBulkLoader)
        
        // Update peakel id
        //peakel.id = peakelInsertStmt.generatedLong
      }
      
      // End of BULK copy
      val nbInsertedPeakels = pgBulkLoader.endCopy()
      
      logger.info(s"BULK insert of $nbInsertedPeakels peakels completed !")

      // Move TMP table content to MAIN table
      logger.info(s"move TMP table $tmpPeakelTableName into MAIN ${LcmsDbPeakelTable.name} table")
      
      lcmsEzDBC.execute(
        s"INSERT INTO ${LcmsDbPeakelTable.name} ($peakelTableColsWithoutPK) " +
        s"SELECT $peakelTableColsWithoutPK FROM $tmpPeakelTableName"
      )

      // Retrieve generated peakel ids
      logger.info(s"Retrieving generated peakel ids...")
      
      val idMzPairs = lcmsEzDBC.select(
        s"SELECT id, moz FROM ${LcmsDbPeakelTable.name} WHERE map_id = $newRawMapId"
      ) { r => Tuple2( r.nextLong, r.nextDouble ) }
      
      val peakelsCount = peakels.length
      assert(
        idMzPairs.length == peakelsCount,
        s"invalid number of retrieved peakel ids: got ${idMzPairs.length} but expected $peakelsCount"
      )
      
      val sortedIdMzPairs = idMzPairs.sortBy(_._1)
      
      // Update peakel ids
      var peakelIdx = 0
      while( peakelIdx < peakelsCount ) {
        val peakel = peakels(peakelIdx)
        val idMzPair = sortedIdMzPairs(peakelIdx)
        // Check we retrieve records in the same order
        assert(
          MathUtils.nearlyEquals(peakel.moz, idMzPair._2, PgConstants.DOUBLE_PRECISION),
          s"error while trying to update peakel id, m/z values are different: was ${peakel.moz} and is now ${idMzPair._2}"
        )
        
        peakel.id = idMzPair._1
        
        peakelIdx += 1
      }
    
      // Link features to peakels
      logger.info(s"Linking features to peakels...")
      
      this._linkFeaturesToPeakels(newRawMapId, features, bulkCopyManager)
    }
    
    ()
  }

  @inline
  protected def insertPeakelUsingCopyManager(peakel: Peakel, pgBulkLoader: CopyIn): Unit = {
    
    // Serialize the peakel data matrix
    val peakelAsBytes = PeakelDataMatrix.pack(peakel.dataMatrix)
    
    val peakelValues = List(
      peakel.id,
      peakel.moz,
      peakel.elutionTime,
      peakel.apexIntensity,
      peakel.area,
      peakel.duration,
      0f, // peakel.fwhm,
      peakel.isOverlapping,
      peakel.featuresCount,
      peakel.dataMatrix.peaksCount,
      // TODO: handle this conversion in encodeRecordForPgCopy
      """\\x""" + Utils.toHexString(peakelAsBytes),
      peakel.properties.map( ProfiJson.serialize(_) ),
      peakel.firstScanId,
      peakel.lastScanId,
      peakel.apexScanId,
      peakel.rawMapId
    )
    
    // Store the peakel
    val peakelBytes = encodeRecordForPgCopy(peakelValues, false)
    pgBulkLoader.writeToCopy(peakelBytes, 0, peakelBytes.length)
  }
  
  private def _linkFeaturesToPeakels(rawMapId: Long, features: Seq[Feature], bulkCopyManager: CopyManager): Unit = {

    val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbFeaturePeakelItemTable.name} ( $ftPeakelItemTableCols ) FROM STDIN")
    
    for (
      ft <- features;
      peakelItem <- ft.relations.peakelItems
    ) {
      
      // Build a row containing feature_peakel_item values
      val ftPeakelItemValues = List(
        ft.id,
        peakelItem.peakelReference.id,
        peakelItem.isotopeIndex,
        peakelItem.isBasePeakel,
        peakelItem.properties.map(ProfiJson.serialize(_)),
        rawMapId
      )
      
      // Store the feature_peakel_item
      val ftPeakelItemBytes = encodeRecordForPgCopy(ftPeakelItemValues)
      pgBulkLoader.writeToCopy(ftPeakelItemBytes, 0, ftPeakelItemBytes.length)
    }
    
    // End of BULK copy
    val nbInsertedRecords = pgBulkLoader.endCopy()
  }
}
