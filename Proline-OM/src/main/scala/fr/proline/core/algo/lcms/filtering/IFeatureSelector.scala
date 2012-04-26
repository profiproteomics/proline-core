package fr.proline.core.algo.lcms.filtering

case class Filter( val name: String, val operator: String, val value: Double ) {
  require( name != null )
}

case class FilterNode( val filter: Filter = null,
                       val subFilters: List[FilterNode] = null,
                       val logicalOperator: String = null
                      ) {
  def isNode(): Boolean = if( subFilters != null ) true else false
}

trait IFeatureSelector {
  
  import fr.proline.core.om.model.lcms._
  
  def selectFeatures( processedMap: ProcessedMap, filterTree: FilterNode ): Unit

}