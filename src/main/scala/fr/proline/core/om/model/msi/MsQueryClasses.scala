package fr.proline.core.om.model.msi

import org.apache.commons.lang3.StringUtils
import scala.collection.mutable.HashMap  
import fr.proline.core.utils.misc.InMemoryIdGen
  
trait MsQuery{
  
  // Required fields
  val id: Int
  val initialId: Int
  val moz: Double
  val charge: Int
  var properties: HashMap[String, Any]
  
}

case class Ms1Query ( // Required fields
                     val id: Int,
                     val initialId: Int,
                     val moz: Double,
                     val charge: Int,
                     
                     // Mutable optional fields
                     var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     
                     ) extends MsQuery {
    
    // Requirements
    require( moz > 0 )
    
}
  
object Ms2Query extends InMemoryIdGen
  
case class Ms2Query(  // Required fields
                 val id: Int,
                 val initialId: Int,
                 val moz: Double,
                 val charge: Int,
                 val spectrumTitle: String,
                                  
                 // Mutable optional fields
                 var spectrumId: Int = 0,
                 var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                 
                 ) extends MsQuery {
    
    // Requirements
    require( StringUtils.isNotEmpty( spectrumTitle )  )
    
}
  



