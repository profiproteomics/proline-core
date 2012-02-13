package fr.proline.core.algo.lcms.filtering

class IntensityBasedSelector extends IFeatureSelector {

  import fr.proline.core.om.lcms.MapClasses._
  
  def selectFeatures( processedMap: ProcessedMap, filterTree: FilterNode ): Unit = {
    
    val intensityFilter = filterTree.filter
    val intensityThreshold = intensityFilter.value
    val operator = intensityFilter.operator
    
    // Filter features according to global intensity threshold
    val features = processedMap.features
    
    if( operator == "gt" ) {
      features.foreach { ft => 
        if( ft.intensity > intensityThreshold ) ft.selectionLevel = 2 else ft.selectionLevel = 1
      }
    } else if( operator == "lt" ) {
      features.foreach { ft => 
        if( ft.intensity < intensityThreshold ) ft.selectionLevel = 2 else ft.selectionLevel = 1
      }
    } else { 
      throw new Exception( "unsupported filtering operator 'operator'")
    }

  }

}