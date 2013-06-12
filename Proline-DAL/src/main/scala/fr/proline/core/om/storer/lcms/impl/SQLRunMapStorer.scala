package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.generate

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureMs2EventTable
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureOverlapMappingTable
import fr.proline.core.dal.tables.lcms.LcmsDbMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbRunMapTable
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IRunMapStorer

class SQLRunMapStorer(lcmsDbCtx: DatabaseConnectionContext) extends IRunMapStorer {

  def storeRunMap(runMap: RunMap, storePeaks: Boolean = false): Unit = {

    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>

      // Create new map
      val newRunMapId = this.insertMap(ezDBC, runMap, null)

      // Update run map id
      runMap.id = newRunMapId

      // Store the related run map   
      val peakPickingSoftwareId = runMap.peakPickingSoftware.id
      val peakelFittingModelId = runMap.peakelFittingModel.map(_.id)

      ezDBC.execute(
        LcmsDbRunMapTable.mkInsertQuery,
        newRunMapId,
        runMap.runId,
        peakPickingSoftwareId,
        peakelFittingModelId
      )
      
      val flattenedFeatures = new ArrayBuffer[Feature](runMap.features.length)

      // Insert features
      ezDBC.executePrepared(LcmsDbFeatureTable.mkInsertQuery, true) { featureInsertStmt =>

        // Loop over features to import them        
        for (ft <- runMap.features) {
          ft.relations.mapId = newRunMapId
  
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
        runMap.features.foreach { ft =>
          if (ft.overlappingFeatures != null) {
            for (olpFt <- ft.overlappingFeatures) {
              statement.executeWith(ft.id, olpFt.id, newRunMapId)
            }
          }
        }
      }

      // Link the features to MS2 scans
      ezDBC.executePrepared(LcmsDbFeatureMs2EventTable.mkInsertQuery) { statement =>
        flattenedFeatures.foreach { ft =>
          if (ft.relations.ms2EventIds != null) {
            for (ms2EventId <- ft.relations.ms2EventIds) {
              statement.executeWith(ft.id, ms2EventId, newRunMapId)}
          }
        }
      }

    })

    /*
    ////// Import isotopic patterns
    if( this.storePeaks && ft.hasIsotopicPatterns ) {
      
      val ipHashes
      foreach val ip (@{ft.isotopicPatterns}) {
        val ipHash = ref(ip) ne 'HASH' ? ip.attributesAsHashref : ip
        push( ipHashes, ipHash )
      }
      
      ////// Store properties in the MSI-DB
      val rdbObjectTree = Pairs::Lcms::RDBO::ObjectTree.new(
                              schema_name     = 'feature.isotopic_patterns',
                              serialized_data = encode_json( ipHashes ),
                             )
      rdbObjectTree.save
      
      ////// Attach isotopic patterns to the feature
      Pairs::Lcms::RDBO::FeatureObjectTreeMap.new(
                          feature_id = rdbFtId,
                          object_tree_id = rdbObjectTree.id,
                          schema_name = 'feature.isotopic_patterns',
                          db = lcmsRdb
                        ).save
                        
      // TODO store a link using map_object_tree_mapping
      
    }
    
    */

    ()

  }

  protected def insertMap(ezDBC: EasyDBC, lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date): Long = {

    // FIXME: scoring should not be null
    val ftScoringId = lcmsMap.featureScoring.map(_.id).getOrElse(1L)
    val lcmsMapType = if (lcmsMap.isProcessed) 1 else 0
    
    var newMapId:Long = 0L
    ezDBC.executePrepared(LcmsDbMapTable.mkInsertQuery, true) { statement =>
      val mapDesc = if (lcmsMap.description == null) None else Some(lcmsMap.description)

      statement.executeWith(
        Option.empty[Int],
        lcmsMap.name,
        mapDesc,
        lcmsMapType,
        lcmsMap.creationTimestamp,
        modificationTimestamp,
        lcmsMap.properties.map( generate(_) ),
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
    
    stmt.executeWith(
      Option.empty[Int],
      ft.moz,
      ft.intensity,
      ft.charge,
      ft.elutionTime,
      qualityScore,
      ft.ms1Count,
      ft.ms2Count,
      ft.isCluster,
      ft.isOverlapping,
      ft.properties.map(generate(_)),
      ftRelations.firstScanId,
      ftRelations.lastScanId,
      ftRelations.apexScanId,
      theoFtId,
      compoundId,
      mapLayerId,
      ftRelations.mapId
    )

    stmt.generatedLong
  }

}