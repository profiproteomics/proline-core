package fr.proline.core.dal.tables

// TODO: put these definitions in an other sub-package (i.e. table)
trait TableDefinition[A  <: Enumeration] {
  
  val tableName: String
  val columns: ColumnEnumeration
  
  def getColumnsAsStrList(): List[String] = {
    List() ++ this.columns.values map { _.toString }
  }
  
  def getColumnsAsStrList( f: A => List[A#Value] ): List[String] = {
    List() ++ f(this.columns.asInstanceOf[A]) map { _.toString }
  }
  
  def makeInsertQuery(): String = {
    this.makeInsertQuery( this.getColumnsAsStrList )
  }
  
  // TODO: rename to _composeInsertQuery
  def makeInsertQuery( f: A => List[A#Value] ): String = {
    this.makeInsertQuery( this.getColumnsAsStrList( f ) )    
  }
  
  // TODO: create a this
  // def _makeInsertQuery[ A <: Enumeration]( f: List[Enumeration#Value] => List[Enumeration#Value] )
  
  def makeInsertQuery( colsAsStrList: List[String] ): String = {
    val valuesStr = List.fill(colsAsStrList.length)("?").mkString(",")

    "INSERT INTO "+ this.tableName+" ("+ colsAsStrList.mkString(",") +") VALUES ("+valuesStr+")"
  }
  
}

trait ColumnEnumeration extends Enumeration {
  implicit def enumValueToString(v: Enumeration#Value): String = v.toString
}