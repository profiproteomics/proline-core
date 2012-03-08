package fr.proline.core.om.model.msi

 
  object FragmentIonType {
    
    import scala.collection.mutable.ArrayBuffer
    
    lazy val defaultIonTypes: Array[FragmentIonType] = {
  
      // Create a map of theoretical fragments
      val ionTypesAsStr = "a a-NH3 a-H2O b b-NH3 b-H2O c d v w x y y-NH3 y-H2O z z+1 z+2 ya yb immonium precursor".split(" ")
      
      val ionTypes = new ArrayBuffer[FragmentIonType](ionTypesAsStr.length)   
      for( val ionTypeAsStr <- ionTypesAsStr ) {
        
        var( fragmentIonSeries, neutralLoss ) = ("", None.asInstanceOf[Option[String]] )
        if( ionTypeAsStr matches ".*-.*"  ) {
          val ionTypeAttrs = ionTypeAsStr.split("-")
          fragmentIonSeries = ionTypeAttrs(0)
          neutralLoss = Some(ionTypeAttrs(1))
        }
        else { fragmentIonSeries = ionTypeAsStr }
        
        // Create new fragment ion type
        ionTypes += new FragmentIonType(
                                        ionSeries = fragmentIonSeries,
                                        neutralLoss = neutralLoss
                                      )
      }
        
      ionTypes.toArray
    }
    
  }

  class FragmentIonType(   
                 // Required fields
                 val ionSeries: String,
                 
                 // Immutable optional fields
                 val neutralLoss: Option[String] = None
                 ) {
    
    override def toString():String = { 
      if( neutralLoss != None && neutralLoss.get.length() > 0 ) { 
        return ionSeries + "-" + neutralLoss
      }
      else { return ionSeries }
    
    }
  }
  
  class FragmentationRule(   
                 // Required fields
                 val description: String
                 
                 ) {
    

  }
  
  class ChargeConstraint(   
                 // Required fields
                 override val description: String,
                 val fragmentCharge: Int
                 
                 ) 
    extends FragmentationRule( description ) {
    

  }
  
  class RequiredSerie(
                 // Required fields
                 override val description: String,
                 val requiredSerie: FragmentIonType,
                 val requiredSerieQualityLevel: String
                 
                 ) 
    extends FragmentationRule( description ) {
    
   // Requirements
    require( requiredSerieQualityLevel == "significant" || 
             requiredSerieQualityLevel == "highest_scoring" )

  }
  
  
  class TheoreticalFragmentIon(   
                 // Required fields
                 override val description: String,
                 override val requiredSerie: FragmentIonType,
                 override val requiredSerieQualityLevel: String,
                 
                 // Immutable optional fields
                 val fragmentMaxMoz: Double = 0.0,
                 val residueConstraint: String = null,
                 val ionType: FragmentIonType = null
                 )
    extends RequiredSerie( description, requiredSerie, requiredSerieQualityLevel ) {
      
	  
  }