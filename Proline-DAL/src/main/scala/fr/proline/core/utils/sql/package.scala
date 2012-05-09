package fr.proline.core.utils

package object sql {
  
  class SQLBool( value: Boolean ) {
    
    def this(intValue:Int) = this( intValue match {
                                     case 1 => true
                                     case 0 => false
                                  })
    override def toString = {
       value match {
        case true => "t"
        case false => "f"
      }
    }
    
    def toIntString = {
       value match {
        case true => "1"
        case false => "0"
      }
    }
    
  }
  
  def BoolToSQLStr( value: Boolean, asInt: Boolean = false ): String = {
    val sqlBool = new SQLBool(value)
    if( asInt ) sqlBool.toIntString else sqlBool.toString()
  }
  
  def SQLStrToBool( sqlStr: String ): Boolean = {
    sqlStr match {
      case "true" => true
      case "false" => false
      case "t" => true
      case "f" => false
      case "1" => true
      case "0" => false
    }
  }
  
  import java.text.{DecimalFormat,DecimalFormatSymbols}
  private val decimalSymbols = new DecimalFormatSymbols()
  decimalSymbols.setDecimalSeparator('.')
  decimalSymbols.setGroupingSeparator('\0')
  
  def newDecimalFormat( template: String ): DecimalFormat = new DecimalFormat(template: String , decimalSymbols)  

}
