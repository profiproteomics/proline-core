package fr.proline.core.om.msi

package MsQueryClasses {

  import org.apache.commons.lang3.StringUtils  
  import scala.collection.mutable.HashMap  
  
  class MsQuery( // Required fields
                 val id: Int,
                 val initialId: Int,
                 val mz: Double,
                 val charge: Int,
                 
                 // Mutable optional fields
                 var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                 ) {
    
    // Requirements
    require( mz > 0 )
    
  }
  
  class Ms2Query( // Required fields
                 override val id: Int,
                 override val initialId: Int,
                 override val mz: Double,
                 override val charge: Int,
                 val spectrumTitle: String,
                                  
                 // Mutable optional fields
                 var spectrumId: Int = 0,
                 properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                 ) 
    extends MsQuery(id,initialId, mz, charge, properties ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( spectrumTitle )  )
    
  }
  

}


