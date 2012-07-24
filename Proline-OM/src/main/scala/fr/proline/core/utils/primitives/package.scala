package fr.proline.core.utils

package object primitives {

  object LongOrIntAsInt {
    
    def asInt(num: AnyVal): Int = num match {
      case i:Int => i
      case l: Long => l.toInt
      case _ => throw new IllegalArgumentException("can't only take a Int or a Long as input")
    }
    implicit def anyVal2Int( num: AnyVal ): Int = asInt( num )
    
  }
  
}