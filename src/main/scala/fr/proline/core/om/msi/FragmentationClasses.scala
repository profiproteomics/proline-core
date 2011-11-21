package fr.proline.core.om.msi

package FragmentationClasses {

  class ObservableMs2Ion(   
                 // Required fields
                 val ionType: String,
                 
                 // Immutable optional fields
                 val neutralLoss: String = null
                 ) {
    
    override def toString():String = { 
      if( neutralLoss != null && neutralLoss.length() > 0 ) { 
        return ionType + "-" + neutralLoss
        }
      else { return ionType }
    
    }
  }
  
  class FragmentationRule(   
                 // Required fields
                 val description: String
                 
                 ) {
    

  }
  
  class ChargeConstraint  (   
                 // Required fields
                 override val description: String,
                 val fragmentCharge: Int
                 
                 ) 
    extends FragmentationRule( description ) {
    

  }
  
  class RequiredSerie  (   
                 // Required fields
                 override val description: String,
                 val requiredSerie: ObservableMs2Ion,
                 val requiredSerieQualityLevel: String
                 
                 ) 
    extends FragmentationRule( description ) {
    
   // Requirements
    require( requiredSerieQualityLevel == "significant" || 
             requiredSerieQualityLevel == "highest_scoring" )

  }
  
  
  class TheoreticalMs2Ion (   
                 // Required fields
                 override val description: String,
                 override val requiredSerie: ObservableMs2Ion,
                 override val requiredSerieQualityLevel: String,
                 
                 // Immutable optional fields
                 val fragment_max_moz: Double = 0.0,
                 val bestChildId: String = null,
                 val msQuery: ObservableMs2Ion = null
                 )
    extends RequiredSerie( description, requiredSerie, requiredSerieQualityLevel ) {
    


  }
  
}