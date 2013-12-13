package fr.proline.core.utils

import fr.profi.util.serialization.CustomDoubleJacksonSerializer

package object serialization {
  
  object ProlineJson extends com.codahale.jerkson.Json with CustomDoubleJacksonSerializer {
    def getObjectMapper = this.mapper
  }
  

  /*
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
    
  }*/

}