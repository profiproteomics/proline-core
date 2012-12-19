package fr.proline.core.dal

// TODO: put these definitions in an other sub-package (i.e. table)
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
  
  def makeInsertQuery(): String = {
    this.makeInsertQuery( this.getColumnsAsStrList )
  }
  
  // TODO: implicit conversion
  // TODO: rename to _composeInsertQuery
  def _makeInsertQuery[A <: Enumeration]( f: A => List[Enumeration#Value] ): String = {
    this.makeInsertQuery( this._getColumnsAsStrList[A]( f ) )    
  }
  
  // TODO: create a this
  // def _makeInsertQuery[ A <: Enumeration]( f: List[Enumeration#Value] => List[Enumeration#Value] )
  
  def makeInsertQuery( colsAsStrList: List[String] ): String = {
    val valuesStr = List.fill(colsAsStrList.length)("?").mkString(",")

    "INSERT INTO "+ this.tableName+" ("+ colsAsStrList.mkString(",") +") VALUES ("+valuesStr+")"
  }
  
  implicit def enumValueToString(v: Enumeration#Value): String = v.toString
  
}