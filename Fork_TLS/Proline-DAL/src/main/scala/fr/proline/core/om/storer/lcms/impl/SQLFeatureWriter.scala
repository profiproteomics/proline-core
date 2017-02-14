package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import com.typesafe.scalalogging.StrictLogging

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.StatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IFeatureWriter

class SQLFeatureWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IFeatureWriter with StrictLogging {

  def insertFeatures(features: Seq[Feature], rawMapId: Long, linkToPeakels: Boolean): Seq[Feature] = {
    
    val flattenedFeatures = new ArrayBuffer[Feature](features.length)
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { featureInsertStmt =>
    
        // Loop over features to import them
        for (ft <- features) {
          
          // Update feature raw map id
          ft.relations.rawMapId = rawMapId
          
          val newFtId = this.insertFeatureUsingPreparedStatement(ft, featureInsertStmt)
          ft.id = newFtId
          
          flattenedFeatures += ft
    
          // Import overlapping features
          if (ft.overlappingFeatures != null) {
            for (olpFt <- ft.overlappingFeatures) {
              //ft.relations.mapId = newRunMapId
    
              val newFtId = this.insertFeatureUsingPreparedStatement(olpFt, featureInsertStmt)
              olpFt.id = newFtId
    
              flattenedFeatures += olpFt
            }
          }
        }
        
      }
    
      // Link the features to overlapping features
      ezDBC.executeInBatch(LcmsDbFeatureOverlapMappingTable.mkInsertQuery) { statement =>
        features.foreach { ft =>
          if (ft.overlappingFeatures != null) {
            for (olpFt <- ft.overlappingFeatures) {
              statement.executeWith(ft.id, olpFt.id, rawMapId)
            }
          }
        }
      }
    
      // Link the features to MS2 scans
      ezDBC.executeInBatch(LcmsDbFeatureMs2EventTable.mkInsertQuery) { statement =>
        flattenedFeatures.foreach { ft =>
          if (ft.relations.ms2EventIds != null) {
            for (ms2EventId <- ft.relations.ms2EventIds) {
              statement.executeWith(ft.id, ms2EventId, rawMapId)
            }
          }
        }
      }
    }
    
    if (linkToPeakels) {
      this.linkFeaturesToPeakels(flattenedFeatures, rawMapId)
    }
    
    flattenedFeatures
  }

  protected[lcms] def insertFeatureUsingPreparedStatement(ft: Feature, stmt: PreparedStatementWrapper): Long = {

    val ftRelations = ft.relations
    val qualityScore = if (ft.qualityScore.isNaN) None else Some(ft.qualityScore)
    val theoFtId = if (ftRelations.theoreticalFeatureId == 0) None else Some(ftRelations.theoreticalFeatureId)
    val compoundId = if (ftRelations.compoundId == 0) None else Some(ftRelations.compoundId)
    val mapLayerId = if (ftRelations.mapLayerId == 0) None else Some(ftRelations.mapLayerId)
    val mapId = ft.getSourceMapId
    require( mapId > 0, "the feature must be associated with a persisted LC-MS Map" )
    
    stmt.executeWith(
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

    stmt.generatedLong
  }
  
  def linkFeaturesToPeakels(features: Seq[Feature], rawMapId: Long): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      // Link features to peakels
      logger.info(s"Linking features to peakels...")
    
      // Link features to peakels
      ezDBC.executeInBatch(LcmsDbFeaturePeakelItemTable.mkInsertQuery) { statement =>
        for (
          ft <- features;
          if ft.relations.peakelItems != null;
          peakelItem <- ft.relations.peakelItems
        ) {
          statement.executeWith(
            ft.id,
            peakelItem.peakelReference.id,
            peakelItem.isotopeIndex,
            peakelItem.isBasePeakel,
            peakelItem.properties.map(ProfiJson.serialize(_)),
            rawMapId
          )
        }
      }
    }
    
    ()
  }
  
  def insertProcessedMapFeatureItems(processedMap: ProcessedMap): Unit = {
    
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
  }
 
}