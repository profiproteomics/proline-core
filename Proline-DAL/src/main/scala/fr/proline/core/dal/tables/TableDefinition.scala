package fr.proline.core.dal.tables

/**
 * The Trait TableDefinition.
 *
 * @param <A> the generic type corresponding to the column Enumeration
 * @author David Bouyssie
 */
trait TableDefinition[A  <: Enumeration] {
  
  val tableName: String
  val columns: ColumnEnumeration
  
  /**
   * Gets the table columns enumeration as a List.
   * 
   * @return a List of columns as column enumeration members
   */
  lazy val columnsAsList: List[A#Value] = {
    List() ++ this.columns.asInstanceOf[A].values
  }
  
  /**
   * Gets the table columns enumeration as a List of String.
   * 
   * @return a List of columns as String objects
   */
  lazy val columnsAsStrList: List[String] = {
    List() ++ this.columns.values map { _.toString }
  }
  
  /**
   * Make an INSERT query String using all table columns.
   * The query string is built in order to perform prepared statements.
   * Thus placeholders (using the '?' char) have been put in the query string.
   * The order of the columns in the query correspond to the order defined in the column enumeration.
   * 
   * @param f The table columns enumeration
   * @return the INSERT query as a String
   */
  def mkInsertQuery(): String = {
    this._makeInsertQuery( this.columnsAsStrList )
  }
  
  /**
   * Compose an INSERT query String using some selected table columns.
   * 
   * @param f A function which selects a list of columns using a column enumeration object
   * @return the INSERT query as a String
   * @see TableDefinition#makeInsertQuery()
   * @see TableDefinition#selectColumns()
   */
  def mkInsertQuery( f: A => List[A#Value] ): String = {
    this._makeInsertQuery( this.selectColsAsStrList( f ) )
  }
  
  /**
   * Make an INSERT query String using a filtered list of table columns.
   * 
   * @param f A function which filters the full list of table columns
   * @return the INSERT query as a String
   * @see TableDefinition#makeInsertQuery()
   * @see TableDefinition#selectColumns()
   */
  def mkInsertQuery( f: (A,List[A#Value]) => List[A#Value] ): String = {
    this._makeInsertQuery( this.selectColsAsStrList( f ) )
  }
  
  /**
   * Make an INSERT query using a List of column Strings.
   * The order of the columns in the query correspond to the one in the provided list.
   *
   * @param colsAsStrList the columns as a List of String objects
   * @return the INSERT query as a String
   */
  private def _makeInsertQuery( colsAsStrList: List[String] ): String = {
    val valuesStr = List.fill(colsAsStrList.length)("?").mkString(",")
    "INSERT INTO "+ this.tableName+" ("+ colsAsStrList.mkString(",") +") VALUES ("+valuesStr+")"
  }

  /**
   * Select columns as a List of String.
   * 
   * @param f A function which select a list of columns using a column enumeration object
   * @return a List of columns as String objects
   */
  def selectColsAsStrList( f: A => List[A#Value] ): List[String] = {
    f(this.columns.asInstanceOf[A]) map { _.toString }
  }
  
  /**
   * Select columns as a List of String.
   * 
   * @param f A function which select a list of columns using a column enumeration object
   * @return a List of columns as String objects
   */
  def selectColsAsStrList( f: (A,List[A#Value]) => List[A#Value] ): List[String] = {
    f( this.columns.asInstanceOf[A],this.columnsAsList ) map { _.toString }
  }
  
}

trait ColumnEnumeration extends Enumeration {
  
  
  implicit def enumValueToString(v: Enumeration#Value): String = v.toString
}