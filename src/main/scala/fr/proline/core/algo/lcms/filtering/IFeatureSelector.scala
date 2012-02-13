package fr.proline.core.algo.lcms.filtering

case class Filter( val name: String, val value: Double, val operator: String ) {
  require( name != null )
}

case class FilterNode( val filter: Filter = null,
                       val subFilters: List[FilterNode] = null,
                       val logicalOperator: String = null
                      ) {
  def isNode(): Boolean = if( subFilters != null ) true else false
}

trait IFeatureSelector {
  
  import fr.proline.core.om.lcms.MapClasses._
  
  def selectFeatures( processedMap: ProcessedMap, filterTree: FilterNode ): Unit

}