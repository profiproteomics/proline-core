package fr.proline.core.service.lcms

import fr.proline.core.LcmsDb
import fr.proline.core.service.IService
import fr.proline.core.om.lcms._
import fr.proline.core.algo.lcms.ClusteringParams
import fr.proline.core.algo.lcms.FeatureClusterer

object CleanMaps {
  
  def apply( lcmsDb: LcmsDb, lcmsMap: ProcessedMap, scans: Seq[LcmsScan],
             clusteringParams: Option[ClusteringParams] ): ProcessedMap = {
    
    val mapCleaningService = new CleanMaps( lcmsDb, lcmsMap, scans, clusteringParams )
    mapCleaningService.runService()
    mapCleaningService.cleanedMap
    
  }
  
}

class CleanMaps( lcmsDb: LcmsDb, lcmsMap: ProcessedMap, scans: Seq[LcmsScan],
                 clusteringParams: Option[ClusteringParams] ) extends IService {
  
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
    if( clusteringParams != None ) {
      
      //print "clusterize features\n" if this.verbose
      
      // Perform the feature clustering
      val clusterizedMap = FeatureClusterer.clusterizeFeatures( lcmsMap, scans, clusteringParams.get )
      
      // Set clusterized map id as the id of the provided map
      clusterizedMap.id = lcmsMap.id
      
      this.cleanedMap = clusterizedMap
      
    }
    
    true
    
  }

}