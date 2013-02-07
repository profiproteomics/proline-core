package fr.proline.core.algo.msi.filter

import fr.proline.core.om.model.msi.PeptideMatch

object FilterUtils {
  
   final val THRESHOLD_PROP_NAME : String = "threshold_value"
}


trait IPeptideMatchFilter {

  val filterName : String
  
  /**
   * Validate each PeptideMatch by setting their isValidated attribute.
   * Validation criteria will depend on implementation.
   * 
   * @param pepMatches : All PeptiMatches for a single query.
   * @param incrementalValidation : if incrementalValidation is set to false, 
   * all PeptideMatch's isValidated property will explicitly be set to true and false. 
   * Otherwise, only excluded PeptideMatch will be changed to isValidated = false   
   * @param traceability : specify if filter could saved information in peptideMatch properties 
   *  
   */
   def filterPSM( pepMatches : Seq[PeptideMatch], incrementalValidation: Boolean, traceability : Boolean) : Unit
  
   /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it. 
    *  
    */
   def getFilterProperties() : Option[Map[String, Any]]
  
//def updatePeptideMatchProperties(pepMatch : PeptideMatch){
//    
//      var pepMatchValProps = pepMatch.validationProperties.orElse( Some( new PeptideMatchValidationProperties() ) ).get 
//      var filtersPropByRank = pepMatchValProps.getValidationFiltersProperties.getOrElse( Map.empty[Int, FilterProperties])
//	  
//      //Read last filter Rank to add new one
//       val lastRank = filtersPropByRank.maxBy(_._1 )._1
//              
//       val filterProp = new FilterProperties(name = filterName)
//            
//	filterProp.propeties = getFilterProperties()
//	filtersPropByRank += (lastRank+1 -> filterProp)
//	pepMatchValProps.validationFiltersProperties = Some(filtersPropByRank)
//        pepMatch.validationProperties = Some( pepMatchValProps )
//   }
}

trait IComputablePeptideMatchFilter  extends IPeptideMatchFilter {

  /**
   * Get the higher or lowest (depending on the filter type) threshold value for this filter.  
   */
  def getThresholdStartValue(): Any
  
  /**
   * Given a current Threshold value, return the next possible value. This 
   * is useful for ComputedValidationPSMFilter in order to determine 
   * best threshold value to reach specified FDR 
   */
  def getNextValue( currentVal : Any ): Any
  
    
  /**
   * Given a current Threshold value, return the next possible value. This 
   * is useful for ComputedValidationPSMFilter in order to determine 
   * best threshold value to reach specified FDR 
   */
  def setThresholdValue( currentVal : Any )
   
}


trait ComputedFDRPeptideMatchFilter {
 
  val expectedFdr : Float
  val fdrValidationFilter : IComputablePeptideMatchFilter
 
}

trait IProteinSetFilter {
    val filterName : String
  
    /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it. 
    *  
    */
   def getFilterProperties() : Option[Map[String, Any]]
}

//VDS TODO: replace with real filter 
class ParamProteinSetFilter( val filterName: String, val minPepSeqLength : Int,val expectedFdr : Float ) extends IProteinSetFilter  {
   
 var pValueThreshold : Float = 0.00f
 
 def getFilterProperties() : Option[Map[String, Any]] = {
   None
 }
  
}

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val separated = Value("separated")
  val concatenated = Value("concatenated")
}

