package fr.proline.core.orm.uds

import com.codahale.jerkson.Json.parse
import com.codahale.jerkson.Json.generate
import fr.proline.core.om.model.uds.ExternalDbProperties
import fr.proline.repository.DriverType

object ExternalDbPropertiesSerializer {
  
  def deserialize( extDb: ExternalDb ) {
    
    // Parse properties
    val extDbProps = parse[ExternalDbProperties]( extDb.getSerializedProperties() )
    
    // Populate properties
    val driverType = extDbProps.getJdbcDriverClassName.get
    extDb.setDriverType( DriverType.valueOf(extDbProps.getJdbcDriverClassName.get) )
  }
  
  def serialize( extDb: ExternalDb ) {
    
    // Build properties object    
    if( extDb.getDriverType != null ) {
      
      val extDbProps = new ExternalDbProperties()
      extDbProps.setJdbcDriverClassName( Some(extDb.getDriverType.toString) )
      
      // Generate JSON string
      extDb.setSerializedProperties( generate( extDbProps ) )
    }

  }

}