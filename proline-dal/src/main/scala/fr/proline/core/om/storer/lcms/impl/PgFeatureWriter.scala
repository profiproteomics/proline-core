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

class PgFeatureWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IFeatureWriter with StrictLogging {
  
  private val allFeatureTableCols = LcmsDbFeatureTable.columnsAsStrList.mkString(",")
  private val featureTableColsWithoutPK = LcmsDbFeatureTable.columnsAsStrList.filter(_ != "id").mkString(",")
  
  private val ftOverlapMappingTableCols = LcmsDbFeatureOverlapMappingTable.columnsAsStrList.mkString(",")
  private val ftMs2EventTableCols = LcmsDbFeatureMs2EventTable.columnsAsStrList.mkString(",")
  
  private val ftPeakelItemTableCols = LcmsDbFeaturePeakelItemTable.columnsAsStrList.mkString(",")

  def insertFeatures(features: Seq[Feature], rawMapId: Long, linkToPeakels: Boolean): Seq[Feature] = {
    
    val flattenedFeatures = new ArrayBuffer[Feature](features.length)
    
    DoJDBCWork.withConnection(lcmsDbCtx) { con =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(con)
      val lcmsEzDBC = ProlineEzDBC(con, lcmsDbCtx.getDriverType)

      /*// Create TMP table
      val tmpFeatureTableName = "tmp_feature_" + (scala.math.random * 1000000).toInt
      logger.info(s"creating temporary table '$tmpFeatureTableName'...")
      
      lcmsEzDBC.execute(
        s"CREATE TEMP TABLE $tmpFeatureTableName (LIKE ${LcmsDbFeatureTable.name}) ON COMMIT DROP"
      )*/

      // Bulk insert of features
      logger.info("BULK insert of features")
      
      //val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $tmpFeatureTableName ( $allFeatureTableCols ) FROM STDIN")
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbFeatureTable.name} ($featureTableColsWithoutPK) FROM STDIN")
      
      // Iterate over the raw map features to store them
      for (ft <- features) {
        
        // Update feature raw map id
        ft.relations.rawMapId = rawMapId
        
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
      
      // End of BULK copy"
      val nbInsertedFeatures = pgBulkLoader.endCopy()
      
      logger.info(s"BULK insert of $nbInsertedFeatures features completed !")

      /*// Move TMP table content to MAIN table
      logger.info(s"move TMP table $tmpFeatureTableName into MAIN ${LcmsDbFeatureTable.name} table")
      
      lcmsEzDBC.execute(
        s"INSERT INTO ${LcmsDbFeatureTable.name} ($featureTableColsWithoutPK) " +
        s"SELECT $featureTableColsWithoutPK FROM $tmpFeatureTableName"
      )*/

      // Retrieve generated feature ids
      logger.info(s"Retrieving generated feature ids...")
      
      val idMzPairs = lcmsEzDBC.select(
        s"SELECT id, moz FROM ${LcmsDbFeatureTable.name} WHERE map_id = $rawMapId"
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
          MathUtils.nearlyEquals(feature.moz, idMzPair._2,MathUtils.EPSILON_LOW_PRECISION),
          s"error while trying to update feature id, m/z values are different: was ${feature.moz} and is now ${idMzPair._2}"
        )
        
        feature.id = idMzPair._1
        
        featureIdx += 1
      }
      
      logger.info(s"Linking overlapping features to features...")
      
      // Link the features to overlapping features
      this._linkFeaturesToOverlappingFeatures(features, rawMapId, bulkCopyManager)
      
      logger.info(s"Linking features to MS2 scans...")
      
      // Link the features to MS2 scans
      this._linkFeaturesToMs2Scans(flattenedFeatures, rawMapId, bulkCopyManager)
      
      if (linkToPeakels) {
        // Link features to peakels
        this._linkFeaturesToPeakels(flattenedFeatures, rawMapId, bulkCopyManager)
      }
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
      //ft.id,
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
  
  private def _linkFeaturesToOverlappingFeatures(
    features: Seq[Feature],
    rawMapId: Long,
    bulkCopyManager: CopyManager
  ): Unit = {

    val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbFeatureOverlapMappingTable.name} ( $ftOverlapMappingTableCols ) FROM STDIN")
    
    // Iterate over overlappingFeatures to store the corresponding mapping
    features.foreach { ft =>
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
  
  private def _linkFeaturesToMs2Scans(flattenedFeatures: Seq[Feature], rawMapId: Long, bulkCopyManager: CopyManager): Unit = {

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
  
  def linkFeaturesToPeakels(features: Seq[Feature], rawMapId: Long): Unit = {
    
    DoJDBCWork.withConnection(lcmsDbCtx) { con =>
      val bulkCopyManager = PostgresUtils.getCopyManager(con)
      this._linkFeaturesToPeakels(features, rawMapId, bulkCopyManager)
    }
    
    ()
  }

  private def _linkFeaturesToPeakels(features: Seq[Feature], rawMapId: Long, bulkCopyManager: CopyManager): Unit = {

    // Link features to peakels
    logger.info(s"Linking features to peakels...")
    
    val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbFeaturePeakelItemTable.name} ( $ftPeakelItemTableCols ) FROM STDIN")
    
    for (
      ft <- features;
      if ft.relations.peakelItems != null;
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
  
  def insertProcessedMapFeatureItems(processedMap: ProcessedMap): Unit = {
    throw new Exception("Not yet implemented !")
  }
  
  /*def insertProcessedMapFeatureItems(processedMap: ProcessedMap): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      val processedMapId = processedMap.id
      require( processedMapId > 0, "the processed map must have been persisted first")
      
      this.logger.info("storing features for processed map #"+processedMapId)
      
      // Create a HashSet which avoids to store the same feature multiple times
      val storedFtIdSet = new collection.mutable.HashSet[Long]
      
      // Attach features to the processed map
      ezDBC.executeInBatch(LcmsDbProcessedMapFeatureItemTable.mkInsertQuery) { statement => 
        processedMap.features.foreach { feature =>
          if( storedFtIdSet.contains(feature.id) == false ) {
            
            // Update feature map id
            feature.relations.processedMapId = processedMapId
            
            if( feature.isCluster ) {
              
              // Store cluster sub-features which have not been already stored
              for( subFt <- feature.subFeatures if storedFtIdSet.contains(subFt.id) == false ) {
                // Update sub-feature map id
                subFt.relations.processedMapId = processedMapId
                // Store the processed feature
                _insertProcessedMapFtItemUsingWrappedStatement( subFt, statement )
                // Memorize this feature has been stored
                storedFtIdSet += subFt.id
              }
            }
            else {
              // Store the processed feature
              _insertProcessedMapFtItemUsingWrappedStatement( feature, statement )
            }
            
            // Memorize this feature has been stored
            storedFtIdSet += feature.id
          }
        }
      }
    }
    
    ()
  }
  
  def insertFeatureClusters( features: Seq[Feature] ): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      // Insert features
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { featureInsertStmt =>
      
        // Store feature clusters 
        features.withFilter( _.isCluster ).foreach { clusterFt =>
            
          // Store the feature cluster
          val newFtId = this.insertFeatureUsingPreparedStatement( clusterFt, featureInsertStmt )
          
          // Update feature cluster id
          clusterFt.id = newFtId
     
        }
      }
      
      // Store processed feature items corresponding to feature clusters
      ezDBC.executeInBatch(LcmsDbProcessedMapFeatureItemTable.mkInsertQuery) { statement => 
        features.withFilter( _.isCluster ).foreach { ft =>
          _insertProcessedMapFtItemUsingWrappedStatement( ft, statement )
        }
      }
      
      // Link feature clusters to their corresponding sub-features
      //val subFtIds = new ArrayBuffer[Int](nbSubFts)
      ezDBC.executeInBatch(LcmsDbFeatureClusterItemTable.mkInsertQuery) { statement => 
        features.withFilter( _.isCluster ).foreach { clusterFt =>
          for( subFt <- clusterFt.subFeatures ) {
            //subFtIds += subFt.id
            statement.executeWith( clusterFt.id, subFt.id, clusterFt.relations.processedMapId )
          }
        }
      }
      
      // Set all sub-features of the processed map as clusterized
      /*subFtIds.grouped(lcmsDb.maxVariableNumber).foreach { tmpSubFtIds => {
        lcmsDb.execute( "UPDATE processed_map_feature_item SET is_clusterized = " + BoolToSQLStr(true,lcmsDb.boolStrAsInt) +
                          " WHERE feature_id IN (" + tmpSubFtIds.mkString(",") +")" )
        }
      }*/
    
    }
    
  }
  
  private def _insertProcessedMapFtItemUsingWrappedStatement( ft: Feature, statement: StatementWrapper ): Unit = {
    
    require( ft.id > 0, "features must be persisted first")
    require( ft.relations.processedMapId > 0, "features must belong to a persisted processed map")
    
    // TODO: store properties
    
    statement.executeWith(
      ft.relations.processedMapId,
      ft.id,
      ft.getCalibratedMozOrMoz,
      ft.getNormalizedIntensityOrIntensity,
      ft.getCorrectedElutionTimeOrElutionTime,
      ft.isClusterized,
      ft.selectionLevel,
      Option.empty[String]
    )
  }*/

}
