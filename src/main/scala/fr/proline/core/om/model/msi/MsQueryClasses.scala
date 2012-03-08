package fr.proline.core.om.model.msi

import org.apache.commons.lang3.StringUtils
import scala.collection.mutable.HashMap  
import fr.proline.core.utils.misc.InMemoryIdGen
  
  class MsQuery( // Required fields
                 val id: Int,
                 val initialId: Int,
                 val moz: Double,
                 val charge: Int,
                 
                 // Mutable optional fields
                 var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                 ) {
    
    // Requirements
    require( moz > 0 )
    
  }
  
  object Ms2Query extends InMemoryIdGen {
    
  }
  
  class Ms2Query( // Required fields
                 override val id: Int,
                 override val initialId: Int,
                 override val moz: Double,
                 override val charge: Int,
                 val spectrumTitle: String,
                                  
                 // Mutable optional fields
                 var spectrumId: Int = 0,
                 properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                 ) 
    extends MsQuery(id,initialId, moz, charge, properties ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( spectrumTitle )  )
    
  }
  



