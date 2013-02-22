package fr.proline.core.utils

package object serialization {
  
  import com.fasterxml.jackson.core.json.JsonWriteContext
  import com.fasterxml.jackson.core.JsonGenerator
  import com.fasterxml.jackson.core.Version
  import com.fasterxml.jackson.databind.jsontype.TypeSerializer
  import com.fasterxml.jackson.databind.module.SimpleModule
  import com.fasterxml.jackson.databind.ser.std.ArraySerializerBase
  import com.fasterxml.jackson.databind.ser.std.NonTypedScalarSerializerBase
  import com.fasterxml.jackson.databind.ser.ContainerSerializer
  import com.fasterxml.jackson.databind.`type`.TypeFactory
  import com.fasterxml.jackson.databind.JsonSerializer
  import com.fasterxml.jackson.databind.JavaType
  import com.fasterxml.jackson.databind.JsonNode
  import com.fasterxml.jackson.databind.SerializerProvider  
  
  object ProlineJson extends com.codahale.jerkson.Json {
    val module = new SimpleModule("ProlineJson", Version.unknownVersion())
    module.addSerializer(classOf[java.lang.Double], new CustomDoubleSerializer)
    module.addSerializer(classOf[Array[Double]], new CustomArrayOfDoubleSerializer)
    mapper.registerModule(module)
  }
  
  object CustomDoubleSerializer {
    
    import java.text.{DecimalFormat,DecimalFormatSymbols}
    private val decimalSymbols = new DecimalFormatSymbols()
    decimalSymbols.setDecimalSeparator('.')
    decimalSymbols.setGroupingSeparator('\0')
    
    def newDecimalFormat( template: String ): DecimalFormat = new DecimalFormat(template: String , decimalSymbols)
    
    val df = newDecimalFormat( "#.#######" )
    
    val instance = new CustomDoubleSerializer()
    
  }
  
  class CustomDoubleSerializer() extends NonTypedScalarSerializerBase[java.lang.Double]( classOf[java.lang.Double] ) {
   
    /*override def serializeWithType( value: Double, jgen: JsonGenerator, provider: SerializerProvider, typeSer: TypeSerializer ) {
      this.serialize( value, jgen, provider )
    }*/
    
    override def serialize( value: java.lang.Double, jgen: JsonGenerator, provider: SerializerProvider ) {
      
      val outputCtx = jgen.getOutputContext.asInstanceOf[JsonWriteContext]
      outputCtx.writeValue()    
      outputCtx.inArray()
      
      var output = if ( outputCtx.inObject ) ":"
                   else if( outputCtx.inArray && outputCtx.getCurrentIndex > 0 ) ","
                   else  ""
      
      if ( java.lang.Double.isNaN(value) || java.lang.Double.isInfinite(value) ) {
        output += "null"
        //jgen.writeNumber( 0 ); // For lack of a better alternative in JSON
        //return;
      } else {
        output += CustomDoubleSerializer.df.format( value )
      }
      
      jgen.writeRaw( output )
    }
  
  }
  
  class CustomArrayOfDoubleSerializer() extends ArraySerializerBase[Array[Double]](classOf[Array[Double]], null) {
    
    override def _withValueTypeSerializer( vts: TypeSerializer): ContainerSerializer[_] = {
      this.asInstanceOf[ContainerSerializer[_]]
    }
  
    override def getContentType(): JavaType = {
      TypeFactory.defaultInstance().uncheckedSimpleType(java.lang.Double.TYPE)
    }
    
    override def getContentSerializer(): JsonSerializer[_] = null
    
    override def isEmpty( value: Array[Double] ): Boolean = {
      (value == null) || (value.length == 0)
    }
    
    override def hasSingleElement( value: Array[Double] ): Boolean = {
      (value.length == 1)
    }
  
    override def serializeContents( values: Array[Double], jgen: JsonGenerator, provider: SerializerProvider ) {
      for ( value <- values ) {
        CustomDoubleSerializer.instance.serialize(value,jgen,provider)
      }
    }
  
    override def getSchema( provider: SerializerProvider, typeHint: java.lang.reflect.Type): JsonNode = {
      val node = createSchemaNode("array", true)
      node.put("items", createSchemaNode("number"))
      node
    }
    
  }

  /*import org.apache.avro.specific.SpecificRecordBase
  import com.google.gson._
  
  trait SerializerProducer[A <: org.apache.avro.specific.SpecificRecordBase] {
    
    def toSerializer(): A
    def toJson(): String = {
      val gson = new Gson()
      gson.toJson( this.toSerializer )
    }
    
  }
  
  private class CharSequenceDeserializer extends JsonDeserializer[java.lang.CharSequence] {
    def deserialize( json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext ): java.lang.CharSequence = {
      json.getAsString()
    }
  }
  
  trait SerializerConsumer[A <: org.apache.avro.specific.SpecificRecordBase, B] {
    
    def fromSerializer( serializer: A ): B
    def fromJson( jsonString: String )(implicit m : Manifest[A]) : B = {    
      val gson = new GsonBuilder().registerTypeAdapter(classOf[java.lang.CharSequence], new CharSequenceDeserializer() ).create();
      val jsonSerializer = gson.fromJson(jsonString, m.erasure.newInstance.asInstanceOf[A].getClass() )
      this.fromSerializer( jsonSerializer )
    }
    
  }*/

}