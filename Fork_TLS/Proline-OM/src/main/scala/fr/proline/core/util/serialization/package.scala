package fr.proline.core.util

import fr.profi.util.serialization._

package object serialization {
  
  object ProlineJson extends ProfiJSMSerialization with CustomDoubleJacksonSerializer
  


}