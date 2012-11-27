package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import scala.reflect.BeanProperty

import org.apache.commons.lang3.StringUtils

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

import fr.proline.util.misc.InMemoryIdGen

trait MsQuery {
  
  // Required fields
  var id: Int
  val initialId: Int
  val moz: Double
  val charge: Int
  val msLevel: Int
  
  var properties: Option[MsQueryProperties]
  def newProperties: Option[MsQueryProperties] = {
    this.properties = Some(new MsQueryProperties() )
    this.properties
  }
  
  /*def toSerializer(): serializer.MsQuery = {
    
    val msqSerializer = new serializer.MsQuery();
    msqSerializer.setId( this.id )
    msqSerializer.setInitialId( this.initialId )
    msqSerializer.setMoz( this.moz )
    msqSerializer.setCharge( this.charge )
    msqSerializer.setMsLevel( this.msLevel )
    if( this.properties != None ) {
      msqSerializer.setProperties( this.properties.get )
    }
    
    msqSerializer    
  }*/

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MsQueryProperties(
  @BeanProperty var targetDbSearch: Option[MsQueryDbSearchProperties] = None,
  @BeanProperty var decoyDbSearch: Option[MsQueryDbSearchProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MsQueryDbSearchProperties(
  @BeanProperty var candidatePeptidesCount: Int,
  @BeanProperty var mascotIdentityThreshold: Option[Float] = None,
  @BeanProperty var mascotHomologyThreshold: Option[Float] = None
)


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Ms1Query (
  // Required fields
  var id: Int,
  val initialId: Int,
  val moz: Double,
  val charge: Int,
 
  // Mutable optional fields
  var properties: Option[MsQueryProperties] = None

) extends MsQuery {
    
  // Requirements
  require( moz > 0 )
  
  val msLevel = 1
    
}

object Ms2Query extends InMemoryIdGen {
  // with SerializerConsumer[serializer.MsQuery, Ms2Query]
  /*def fromSerializer( msqSerializer: serializer.MsQuery ): Ms2Query = {
    
    new Ms2Query( id = msqSerializer.getId(),
                  initialId = msqSerializer.getInitialId(),
                  moz = msqSerializer.getMoz(),
                  charge = msqSerializer.getCharge(),
                  spectrumTitle = msqSerializer.getSpectrumTitle().toString(),
                  spectrumId = msqSerializer.getSpectrumId(),
                  properties = Option( msqSerializer.getProperties )
                )
  }*/
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Ms2Query(
  // Required fields
  var id: Int,
  val initialId: Int,
  val moz: Double,
  val charge: Int,
  val spectrumTitle: String,
                  
  // Mutable optional fields
  var spectrumId: Int = 0,                 
  var properties: Option[MsQueryProperties] = None
 
) extends MsQuery {
  
  // Requirements
  require( StringUtils.isNotEmpty( spectrumTitle )  )
  
  val msLevel = 2
  
  /*override def toSerializer(): serializer.MsQuery = {
    
    val msqSerializer = super.toSerializer()
    msqSerializer.setSpectrumTitle( this.spectrumTitle )
    msqSerializer.setSpectrumId( this.spectrumId )
    
    msqSerializer
    
  }*/
    
}
  



