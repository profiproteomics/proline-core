package fr.proline.core.algo.lcms.filtering

class IntensityBasedSelector extends IFeatureSelector {

  import fr.proline.core.om.model.lcms._
  
  def selectFeatures( processedMap: ProcessedMap, filterTree: FilterNode ): Unit = {
    
    val intensityFilter = filterTree.filter
    val intensityThreshold = intensityFilter.value
    val operator = intensityFilter.parsedOperator
    
    // Filter features according to global intensity threshold
    val features = processedMap.features
    
    operator match {
      case FilterOperator.GT => {
        features.foreach { ft =>
          if( ft.intensity > intensityThreshold ) ft.selectionLevel = 2 else ft.selectionLevel = 1
        }
      }
      case FilterOperator.LT => {
        features.foreach { ft => 
          if( ft.intensity < intensityThreshold ) ft.selectionLevel = 2 else ft.selectionLevel = 1
        }
      }
    }

  }

}