package fr.proline.core.algo.lcms.filtering

class NoneSelector extends IFeatureSelector {

  import fr.proline.core.om.model.lcms._

  def selectFeatures(processedMap: ProcessedMap, filterTree: FilterNode): Unit = {
    val features = processedMap.features
    features.foreach { ft =>
      ft.selectionLevel = 2
    }

  }

}