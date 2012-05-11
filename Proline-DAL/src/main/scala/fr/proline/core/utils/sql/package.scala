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
  
  // TODO: put these definitions in an other package (msi.table_definitions)
  trait TableDefinition {
    
    val tableName: String
    val columns: Enumeration
    
    def getColumnsAsStrList(): List[String] = {
      List() ++ this.columns.values map { _.toString }
    }
    
    // TODO: implicit conversion
    def _getColumnsAsStrList[A <: Enumeration]( f: A => List[Enumeration#Value] ): List[String] = {
      List() ++ f(this.columns.asInstanceOf[A]) map { _.toString }
    }
    
    def getInsertQuery(): String = {
      this.buildInsertQuery( this.getColumnsAsStrList )
    }
    
    // TODO: implicit conversion
    def _getInsertQuery[A <: Enumeration]( f: A => List[Enumeration#Value] ): String = {
      this.buildInsertQuery( this._getColumnsAsStrList[A]( f ) )    
    }
    
    def buildInsertQuery( colsAsStrList: List[String] ): String = {
      val valuesStr = List.fill(colsAsStrList.length)("?").mkString(",")
  
      "INSERT INTO "+ this.tableName+" ("+ colsAsStrList.mkString(",") +") VALUES ("+valuesStr+")"
    }
    
  }
}
