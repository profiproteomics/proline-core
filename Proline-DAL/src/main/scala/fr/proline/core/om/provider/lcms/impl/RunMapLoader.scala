package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.SQLQueryExecution

class RunMapLoader( val sqlExec: SQLQueryExecution,
                    val loadPeaks: Boolean = false ) extends IMapLoader {
  
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.util.sql._
  import fr.proline.core.om.model.lcms._
  
  val peakPickingSoftwareById = lcmsDbHelper.getPeakPickingSoftwareById()
  val peakelFittingModelById = lcmsDbHelper.getPeakelFittingModelById()
  
  /** Returns a list of features corresponding to a given list of run map ids */
  def getMaps( runMapIds: Seq[Int] ): Array[RunMap] = {
    
    val features = this.getFeatures( runMapIds )
    // Group features by map id
    val featuresByMapId = features.groupBy(_.relations.mapId)
    
    val runMaps = new Array[RunMap](runMapIds.length)
    var colNames: Seq[String] = null
    var lcmsMapIdx = 0
    
    // Load processed map features
    sqlExec.selectAndProcess( 
      "SELECT map.*, run_map.run_id, run_map.peak_picking_software_id, run_map.peakel_fitting_model_id FROM run_map, map " +
      "WHERE run_map.id IN (" + runMapIds.mkString(",")+") " +
      "AND map.id = run_map.id " ) { r =>
        
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the map record
      val mapRecord = colNames.map( colName => ( colName -> r.nextObjectOrElse(null) ) ).toMap
      
      val mapId = mapRecord("id").asInstanceOf[Int]
      val featureScoringId = mapRecord("feature_scoring_id").asInstanceOf[Int]
      val peakPickingSoftwareId = mapRecord("peak_picking_software_id").asInstanceOf[Int]
      val peakelFittingModelId = mapRecord("peakel_fitting_model_id").asInstanceOf[Int]
      
      val mapFeatures = featuresByMapId( mapId )
      val featureScoring = featureScoringById.getOrElse(featureScoringId,null)
      val peakPickingSoftware = peakPickingSoftwareById(peakPickingSoftwareId)
      val peakelFittingModel = peakelFittingModelById.getOrElse(peakelFittingModelId,null)        
      
      // Build the map
      runMaps(lcmsMapIdx) = buildRunMap(mapRecord, mapFeatures, featureScoring, peakPickingSoftware, peakelFittingModel )
      lcmsMapIdx += 1
      
      ()
    }
    
    runMaps
    //val features = mapFeatureLoader.asInstanceOf[RunMapFeatureLoader].getFeatures( runMapIds )
    
    //super.getMaps( mapRecords, features)
    
  }
  
  def buildRunMap( mapRecord: Map[String,Any], features: Array[Feature], featureScoring: FeatureScoring,
                   peakPickingSoftware: PeakPickingSoftware, peakelFittingModel: PeakelFittingModel ): RunMap = {
    
    import java.util.Date
    
    new RunMap(  id = mapRecord("id").asInstanceOf[Int],
                 name = mapRecord("name").asInstanceOf[String],
                 isProcessed = false,
                 creationTimestamp = new Date(), // TODO: parse date
                 features = features,
                 runId = mapRecord("run_id").asInstanceOf[Int],
                 peakPickingSoftware = peakPickingSoftware,
                 description = mapRecord("description").asInstanceOf[String],
                 featureScoring = featureScoring,
                 peakelFittingModel = peakelFittingModel
               )
  }
  
  /** Returns a list of features corresponding to a given list of run map ids */
  def getFeatures( mapIds: Seq[Int] ): Array[Feature] = {
    
    // Check that provided map ids correspond to run maps
    val nbMaps = sqlExec.selectInt( "SELECT count(*) FROM run_map WHERE id IN (" + mapIds.mkString(",") + ")" )  
    if( nbMaps < mapIds.length ) {
      throw new Exception("map ids must correspond to existing run maps");
    }
    
    // Load run ids
    val runIds = sqlExec.selectInts( "SELECT run_id FROM run_map WHERE id IN (" + mapIds.mkString(",") + ")" )
    
    // Load mapping between scan ids and scan initial ids
    val scanInitialIdById = lcmsDbHelper.getScanInitialIdById( runIds ) 
    
    // Retrieve mapping between features and MS2 scans
    val ms2EventIdsByFtId = lcmsDbHelper.getMs2EventIdsByFtId( mapIds )
    
    // TODO: load isotopic patterns if needed
    //val ipsByFtId = if( loadPeaks ) getIsotopicPatternsByFtId( mapIds ) else null
    
    // Retrieve mapping between overlapping features
    val olpFtIdsByFtId = getOverlappingFtIdsByFtId( mapIds )

    var olpFeatureById: Map[Int,Feature] = null
    if( olpFtIdsByFtId.size > 0 ) {
      olpFeatureById = getOverlappingFeatureById( mapIds, scanInitialIdById, ms2EventIdsByFtId )
    }

    val ftBuffer = new ArrayBuffer[Feature]
    
    eachFeatureRecord( mapIds, ftRecord => {
      val ftId = ftRecord("id").asInstanceOf[Int]
        
      // Try to retrieve overlapping features
      var olpFeatures: Array[Feature] = null
      if( olpFtIdsByFtId.contains(ftId) ) {
        olpFeatures = olpFtIdsByFtId(ftId) map { olpFtId => olpFeatureById(olpFtId) }
      }
      
      // TODO: load isotopic patterns if needed
      
      val feature = buildRunMapFeature( ftRecord, scanInitialIdById, ms2EventIdsByFtId, null, olpFeatures )
      
      ftBuffer += feature
    })

    ftBuffer.toArray
    
  }
  
  /** Builds a feature object coming from a run map */
  def buildRunMapFeature( featureRecord: Map[String,Any],
                          scanInitialIdById: Map[Int,Int],
                          ms2EventIdsByFtId: Map[Int,Array[Int]],
                          isotopicPatterns: Option[Array[IsotopicPattern]],
                          overlappingFeatures: Array[Feature]
                          ): Feature = {
    
    val mapId = featureRecord("map_id").asInstanceOf[Int]
    buildFeature( featureRecord, scanInitialIdById, ms2EventIdsByFtId, isotopicPatterns, overlappingFeatures, mapId )

  }


}
