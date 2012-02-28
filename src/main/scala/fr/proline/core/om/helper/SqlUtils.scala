package fr.proline.core.om.helper

package object SqlUtils {
  
  class SQLBool( value: Boolean ) {
    
    def this(intValue:Int) = this( intValue match {
                                     case 1 => true
                                     case 0 => false
                                  })
    override def toString = {
       value match {
        case true => "true"
        case false => "false"
      }
    }
    
    def toIntString = {
       value match {
        case true => "1"
        case false => "0"
      }
    }
    
  }
  
  def BoolToSQLStr (value: Boolean, asInt: Boolean = false ): String = {
    val sqlBool = new SQLBool(value)
    if( asInt ) sqlBool.toIntString else sqlBool.toString()
  }
  
  def SQLStrToBool(sqlStr: String ): Boolean = {
    sqlStr match {
      case "true" => true
      case "false" => false
      case "1" => true
      case "0" => false
    }
  }

}
