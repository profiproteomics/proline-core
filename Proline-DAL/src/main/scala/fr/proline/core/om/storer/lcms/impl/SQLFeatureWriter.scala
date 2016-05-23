package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IFeatureWriter

class SQLFeatureWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IFeatureWriter {

  def insertFeatures(rawMap: RawMap): ArrayBuffer[Feature] = {
    
    val newRawMapId = rawMap.id
    
    val flattenedFeatures = new ArrayBuffer[Feature](rawMap.features.length)
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { featureInsertStmt =>
    
        // Loop over features to import them        
        for (ft <- rawMap.features) {
          
          // Update feature raw map id
          ft.relations.rawMapId = newRawMapId
          
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
        rawMap.features.foreach { ft =>
          if (ft.overlappingFeatures != null) {
            for (olpFt <- ft.overlappingFeatures) {
              statement.executeWith(ft.id, olpFt.id, newRawMapId)
            }
          }
        }
      }
    
      // Link the features to MS2 scans
      ezDBC.executeInBatch(LcmsDbFeatureMs2EventTable.mkInsertQuery) { statement =>
        flattenedFeatures.foreach { ft =>
          if (ft.relations.ms2EventIds != null) {
            for (ms2EventId <- ft.relations.ms2EventIds) {
              statement.executeWith(ft.id, ms2EventId, newRawMapId)
            }
          }
        }
      }
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
  
  def insertPeakels(rawMap: RawMap, features: Seq[Feature]): Unit = {
    
    val newRawMapId = rawMap.id
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      // Insert peakels
      ezDBC.executePrepared(LcmsDbPeakelTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { peakelInsertStmt =>
        
        for( peakels <- rawMap.peakels; peakel <- peakels ) {
          
          // Update peakel raw map id
          peakel.rawMapId = newRawMapId
          
          // Serialize the peakel data matrix
          val peakelAsBytes = PeakelDataMatrix.pack(peakel.dataMatrix)
          
          peakelInsertStmt.executeWith(
            peakel.moz,
            peakel.elutionTime,
            peakel.apexIntensity,
            peakel.area,
            peakel.duration,
            0f, // peakel.fwhm,
            peakel.isOverlapping,
            peakel.featuresCount,
            peakel.dataMatrix.peaksCount,
            peakelAsBytes,
            peakel.properties.map( ProfiJson.serialize(_) ),
            peakel.firstScanId,
            peakel.lastScanId,
            peakel.apexScanId,
            peakel.rawMapId
          )
          
          // Update peakel id
          peakel.id = peakelInsertStmt.generatedLong
        }
        
      }
      
      // Link features to peakels
      ezDBC.executeInBatch(LcmsDbFeaturePeakelItemTable.mkInsertQuery) { statement =>
        for (
          ft <- features;
          peakelItem <- ft.relations.peakelItems
        ) {
          statement.executeWith(
            ft.id,
            peakelItem.peakelReference.id,
            peakelItem.isotopeIndex,
            peakelItem.isBasePeakel,
            peakelItem.properties.map(ProfiJson.serialize(_)),
            newRawMapId
          )
        }
      }
    }
    
    ()
  }

}