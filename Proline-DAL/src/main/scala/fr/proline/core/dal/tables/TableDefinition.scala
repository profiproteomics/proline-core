package fr.proline.core.dal.tables

/**
 * The Trait TableDefinition.
 *
 * @param <A> the generic type corresponding to the column Enumeration
 * @author David Bouyssie
 */
trait TableDefinition[A  <: ColumnEnumeration] {
  
  val name: String
  val columns: A
  
  override def toString = this.name
  
  /**
   * Gets the table columns enumeration as a List.
   * 
   * @return a List of columns as column enumeration members
   */
  lazy val columnsAsList: List[A#Column] = {
    this.columns.values.toList.map( _.asInstanceOf[A#Column] ).filter( _ != this.columns.* )
  }
  
  /**
   * Gets the table columns enumeration as a List of String.
   * 
   * @return a List of columns as String objects
   */
  lazy val columnsAsStrList: List[String] = {
    this.columnsAsList.map { _.toString }
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
  def mkInsertQuery( f: A => List[A#Column] ): String = {
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
  def mkInsertQuery( f: (A,List[A#Column]) => List[A#Column] ): String = {
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
    "INSERT INTO "+ this.name+" ("+ colsAsStrList.mkString(",") +") VALUES ("+valuesStr+")"
  }
  
  def mkSelectQuery( whereClause: Option[String] = None ): String = {
    this._makeSelectQuery( List("*"), List(this.name), whereClause )
  }
  
  private def _makeSelectQuery( colsAsStrList: List[String], tblsAsStrList: List[String], whereClause: Option[String] = None ): String = {
    var query = "SELECT "+ colsAsStrList.mkString(",")+" FROM "+ tblsAsStrList.mkString(",")
    if( whereClause.isDefined ) query += " WHERE " + whereClause.get
    query
  }

  /**
   * Select columns as a List of String.
   * 
   * @param f A function which select a list of columns using a column enumeration object
   * @return a List of columns as String objects
   */
  def selectColsAsStrList( f: A => List[A#Column] ): List[String] = {
    f(this.columns) map { _.toString }
  }
  
  /**
   * Select columns as a List of String.
   * 
   * @param f A function which select a list of columns using a column enumeration object
   * @return a List of columns as String objects
   */
  def selectColsAsStrList( f: (A,List[A#Column]) => List[A#Column] ): List[String] = {
    f( this.columns,this.columnsAsList ) map { _.toString }
  }
  
}

object ColumnEnumeration {
  implicit def columnToString(col: ColumnEnumeration#Column): String = col.toString
}

trait ColumnEnumeration extends Enumeration {
  thisenum =>
  
  val $tableName: String
  val * = Column("*")
    
  protected final def Column(name: String): Column = new Column(this.nextId, name)  
  class Column(i: Int, name: String) extends Val(i: Int, name: String) {
    val $columns: ColumnEnumeration = thisenum
    
    def toAliasedString() = $tableName + "_" + this.toString()
    def toFullString() = $tableName + "." + this.toString()
    
  }
  
}
