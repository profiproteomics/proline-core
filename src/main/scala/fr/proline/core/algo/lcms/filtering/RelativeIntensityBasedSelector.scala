package fr.proline.core.algo.lcms.filtering

class RelativeIntensityBasedSelector extends IFeatureSelector {

  import fr.proline.core.om.model.lcms._
  import fr.proline.core.utils.misc.median
  
  /** Select features according to a threshold relative to the map median intensity */
  def selectFeatures( processedMap: ProcessedMap, filterTree: FilterNode ): Unit = {
    
    val relIntensityFilter = filterTree.filter
    val relIntensityThreshold: Double = relIntensityFilter.value
    val operator = relIntensityFilter.operator
    if( relIntensityThreshold < 0 || relIntensityThreshold > 100 ) {
      throw new IllegalArgumentException("relative intensity threshold must be a number between 0 and 100" )
    }

    // Compute processed map median intensity
    val ftIntensities = processedMap.features.map(_.intensity)
    val medianFtIntensity = median(ftIntensities)
    
    // Filter features according to relative intensity threshold
    val intensityFilter = relIntensityFilter.copy( value = medianFtIntensity*relIntensityThreshold/100)
    new IntensityBasedSelector().selectFeatures( processedMap, FilterNode( filter = intensityFilter ) )
    
  }

}