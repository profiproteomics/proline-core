package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IRawMapStorer

class SQLRawMapStorer(lcmsDbCtx: DatabaseConnectionContext) extends IRawMapStorer {

  def storeRawMap(rawMap: RawMap, storePeakels: Boolean = false): Unit = {

    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>

      // Create new map
      val newRawMapId = this.insertMap(ezDBC, rawMap, null)

      // Update run map id
      rawMap.id = newRawMapId

      // Store the related run map
      val peakPickingSoftwareId = rawMap.peakPickingSoftware.id
      val peakelFittingModelId = rawMap.peakelFittingModel.map(_.id)

      ezDBC.execute(
        LcmsDbRawMapTable.mkInsertQuery,
        newRawMapId,
        rawMap.runId,
        peakPickingSoftwareId,
        peakelFittingModelId
      )
      
      val flattenedFeatures = new ArrayBuffer[Feature](rawMap.features.length)

      // Insert features
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
      ezDBC.executePrepared(LcmsDbFeatureOverlapMappingTable.mkInsertQuery) { statement =>
        rawMap.features.foreach { ft =>
          if (ft.overlappingFeatures != null) {
            for (olpFt <- ft.overlappingFeatures) {
              statement.executeWith(ft.id, olpFt.id, newRawMapId)
            }
          }
        }
      }

      // Link the features to MS2 scans
      ezDBC.executePrepared(LcmsDbFeatureMs2EventTable.mkInsertQuery) { statement =>
        flattenedFeatures.foreach { ft =>
          if (ft.relations.ms2EventIds != null) {
            for (ms2EventId <- ft.relations.ms2EventIds) {
              statement.executeWith(ft.id, ms2EventId, newRawMapId)
            }
          }
        }
      }
      
      // Store peakels if requested
      if( storePeakels ) {
        
        // Insert peakels
        ezDBC.executePrepared(LcmsDbPeakelTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { peakelInsertStmt =>
          
          for( peakels <- rawMap.peakels; peakel <- peakels ) {
            
            // Update peakel raw map id
            peakel.rawMapId = newRawMapId
            
            // Serialize the peakel data matrix
            val peaksAsBytes = org.msgpack.ScalaMessagePack.write(peakel.dataMatrix)
            
            peakelInsertStmt.executeWith(
              peakel.moz,
              peakel.elutionTime,
              peakel.apexIntensity,
              peakel.area,
              peakel.duration,
              0f, // peakel.fwhm,
              peakel.isOverlapping,
              peakel.featuresCount,
              peakel.dataMatrix.spectrumIds.length, // number of peaks
              peaksAsBytes,
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
        ezDBC.executePrepared(LcmsDbFeaturePeakelItemTable.mkInsertQuery) { statement =>
          for (
            ft <- flattenedFeatures;
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

    })

    ()

  }

  protected def insertMap(ezDBC: EasyDBC, lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date): Long = {

    // FIXME: scoring should not be null
    val ftScoringId = lcmsMap.featureScoring.map(_.id).getOrElse(1L)
    val lcmsMapType = if (lcmsMap.isProcessed) 1 else 0
    
    var newMapId:Long = 0L
    ezDBC.executePrepared(LcmsDbMapTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { statement =>
      val mapDesc = if (lcmsMap.description == null) None else Some(lcmsMap.description)

      statement.executeWith(
        lcmsMap.name,
        mapDesc,
        lcmsMapType,
        lcmsMap.creationTimestamp,
        modificationTimestamp,
        lcmsMap.properties.map( ProfiJson.serialize(_) ),
        ftScoringId
      )

      newMapId = statement.generatedLong
    }

    newMapId

  }

  protected def insertFeatureUsingPreparedStatement(ft: Feature, stmt: PreparedStatementWrapper): Long = {

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

}