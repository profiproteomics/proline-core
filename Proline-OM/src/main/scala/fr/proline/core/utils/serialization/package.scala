package fr.proline.core.utils
/*
package object serialization {

  import org.apache.avro.specific.SpecificRecordBase
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
    
  }

}*/