package fr.proline.core.om.helper

package SqlUtils {
  
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
  
  object BoolToSQLStr {
    def apply(value: Boolean, asInt: Boolean = false ): String = {
      val sqlBool = new SQLBool(value)
      if( asInt ) sqlBool.toIntString else sqlBool.toString()
    }
  }
  
  object SQLStrToBool {
    def apply(sqlStr: String ): Boolean = {
      sqlStr match {
        case "true" => true
        case "false" => false
        case "1" => true
        case "0" => false
      }
    }
  }

}
