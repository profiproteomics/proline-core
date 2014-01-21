package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.proline.util.misc.InMemoryIdGen
import fr.proline.util.StringUtils

trait MsQuery {
  
  // Required fields
  var id: Long
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
    if( this.properties.isDefined ) {
      msqSerializer.setProperties( this.properties.get )
    }
    
    msqSerializer    
  }*/

}

case class MsQueryProperties(
  @BeanProperty var targetDbSearch: Option[MsQueryDbSearchProperties] = None,
  @BeanProperty var decoyDbSearch: Option[MsQueryDbSearchProperties] = None
)

case class MsQueryDbSearchProperties(
  @BeanProperty var candidatePeptidesCount: Int,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var mascotIdentityThreshold: Option[Float] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var mascotHomologyThreshold: Option[Float] = None
)

case class Ms1Query (
  // Required fields
  var id: Long,
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
  
  // Needed for Jacks deserializer
  @JsonCreator
  def createFromJSON(
    @JsonProperty("id") id: Long,
    @JsonProperty("initial_id") initialId: Int,
    @JsonProperty("moz") moz: Double,
    @JsonProperty("charge") charge: Int,
    @JsonProperty("spectrum_title") spectrumTitle: String,
    @JsonProperty("spectrum_id") spectrumId: Long = 0,
    @JsonProperty("properties") properties: Option[MsQueryProperties] = None
  ): Ms2Query = Ms2Query(id,initialId,moz,charge,spectrumTitle,spectrumId,properties)
  
}

case class Ms2Query(
  // Required fields
  var id: Long,
  val initialId: Int,
  val moz: Double,
  val charge: Int,
  val spectrumTitle: String,
  
  // Mutable optional fields
  var spectrumId: Long = 0,
  var properties: Option[MsQueryProperties] = None
 
) extends MsQuery {
  
  // Requirements
  require( StringUtils.isNotEmpty( spectrumTitle ) )
  
  val msLevel = 2
  
  /*override def toSerializer(): serializer.MsQuery = {
    
    val msqSerializer = super.toSerializer()
    msqSerializer.setSpectrumTitle( this.spectrumTitle )
    msqSerializer.setSpectrumId( this.spectrumId )
    
    msqSerializer
    
  }*/
    
}
  



