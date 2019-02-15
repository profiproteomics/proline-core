package fr.proline.core.service.lcms

import fr.profi.jdbc.easy._
import fr.profi.api.service.IService
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.om.model.lcms._
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.algo.lcms.ClusterizeFeatures
import fr.proline.repository.IDatabaseConnector

object CleanMaps {
  
  def apply(
    lcmsDbCtx: LcMsDbConnectionContext,
    lcmsMap: ProcessedMap,
    scans: Seq[LcMsScan],
    clusteringParams: Option[ClusteringParams]
  ): ProcessedMap = {
    
    val mapCleaningService = new CleanMaps( lcmsDbCtx, lcmsMap, scans, clusteringParams )
    mapCleaningService.runService()
    mapCleaningService.cleanedMap
    
  }
  
}

class CleanMaps(
  val lcmsDbCtx: LcMsDbConnectionContext,
  lcmsMap: ProcessedMap,
  scans: Seq[LcMsScan],
  clusteringParams: Option[ClusteringParams]
) extends ILcMsService {
  
  var cleanedMap: ProcessedMap = null
  
  def runService (): Boolean = {
    
    this.cleanedMap = lcmsMap
    
    // Retrieve database connection
    //val lcmsRdb = this.lcmsRdb
  
    // Filter features if requested
    /*if( this.hasFeatureFilters ) {
      
      print "filter features\n"
      
      // TODO: retrieve from attributes
      val ftSelectionFilters = { ms1_count = { '>=' = 5 }, peakels_count = { '>=' = 2 }, quality_score = { '>=' = 0.3 },
                                   overlap = { factor = { '<=' = 0.5 }, correlation = { '<=' = 0.3 } } }
      
      // Use feature selector module to format selection filters
      val ftSelector = new Pairs::Lcms::Module::MapFeatureSelector( unformatted_filters = ftSelectionFilters )
      val formattedFilters = ftSelector.formattedFilters
      val ftSelectionAlgo = ftSelector.featureSelectionAlgo
      
      // Perform the feature filtering        
      ftSelectionAlgo.selectFeatures( map, ftSelector.formattedFilters )
      
      // Retrieve selected features
      val selectedFeatures = grep { _.selectionLevel >=2 } @{map.features}
      
      map.features( selectedFeatures )
    
    }*/
    
    // Clusterize features if requested
    if( clusteringParams.isDefined ) {
      
      //print "clusterize features\n" if this.verbose
      
      // Perform the feature clustering
      val clusterizedMap = ClusterizeFeatures( lcmsMap, scans, clusteringParams.get )
      
      // Set clusterized map id as the id of the provided map
      clusterizedMap.id = lcmsMap.id
      
      this.cleanedMap = clusterizedMap
      
    }
    
    true
    
  }

}