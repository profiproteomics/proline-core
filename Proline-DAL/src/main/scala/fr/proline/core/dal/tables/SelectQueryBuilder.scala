package fr.proline.core.dal.tables

import fr.proline.core.dal.tables.{ColumnEnumeration => ColEnum}

trait CanBuildSelectQuery {
  
  //implicit def any2ClauseAdd(x: Any) = new ClauseAdd(x)
  //implicit def string2ClauseAdd(s: String) = new ClauseAdd(s)
  
  def colsListToStrList( colsList: List[ColEnum#Column] ): List[String] = {
    colsList.map { col => col.toFullString }
  }
  
  protected def _makeSelectQuery( colsAsStrList: List[String], tblsAsStrList: List[String], clauses: Option[String] = None ): String = {
    var query = "SELECT "+ colsAsStrList.mkString(",") +" FROM "+ tblsAsStrList.mkString(",")
    if( clauses != None ) query += " " + clauses.get
    query
  }
  
  implicit def columnToString(col: ColumnEnumeration#Column): String = col.toFullString
}

object SelectQueryBuilder {
  
  class ClauseAdd(self: String) {
    def ~(other: Any): String = {
      val otherStr = other match {
        case col: ColEnum#Column => col.toFullString
        case _ => other.toString
      }
      self + otherStr
    }
  }
  implicit def any2ClauseAdd(x: String) = new ClauseAdd(x)
}
    
class SelectQueryBuilder1[A<:ColEnum]( table: TableDefinition[A] ) extends CanBuildSelectQuery {
  
  /*def mkClause( f: (A) => String ): String = {
    f( table.columns )
  }*/
  
  def mkSelectQuery( fn: (A,List[A#Column]) => Tuple2[List[ColEnum#Column],String] ): String = {
    val( colsList, clauses ) = fn(table.columns,table.columnsAsList)
    val tblsList = List( table.name )
    this._makeSelectQuery( colsList.map( _.toString ), tblsList, Option(clauses) )
  }
  
  /*def mkSelectQuery( tc: (A,List[A#Column]) => Tuple1[List[ColEnum#Column]] ): String = {
    val colsList = tc( table.columns,table.columnsAsList )._1
    val tblsList = List( table.name )
    this._makeSelectQuery( colsList.map( _.toString ), tblsList, Option.empty[String] )
  }*/
  
  /*def mkSelectQuery( s: (A,List[A#Column]) => List[ColEnum#Column], cb: (A) => String ): String = {
    this.mkSelectQuery( s, this.mkClause(cb) )
  }*/
  
  /*def selectCols( s: (A,List[A#Column]) => List[ColEnum#Column] ): List[ColEnum#Column] = {
    s( table.columns,table.columnsAsList )
  }*/
  
}

class SelectQueryBuilder2[A<:ColEnum,B<:ColEnum](
  val tables: Tuple2[TableDefinition[A],TableDefinition[B]]
)extends CanBuildSelectQuery {
  
  def mkSelectQuery( fn: (A,List[A#Column],B,List[B#Column]) => Tuple2[List[ColEnum#Column],String] ): String = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList
    )
    
    val tblsList = List( tables._1.name, tables._2.name )
    
    this._makeSelectQuery( colsList.map( _.toString ), tblsList, Option(clauses) )
  }

  /*def mkClause( f: (A,B) => String ): String = {
    f( cols1, cols2 )
  }
  
  def mkSelectQuery( s: (A,List[A#Column],B,List[B#Column]) => List[ColEnum#Column], clauses: String = null ): String = {
    val colsListAsStrList = this.colsListToStrList( selectCols( s ) )  
    val tblsList = List( tables._1.name, tables._2.name )
    this._makeSelectQuery( colsListAsStrList, tblsList, Option(clauses) )
  }
 
  def selectCols( s: (A,List[A#Column],B,List[B#Column]) => List[ColEnum#Column] ): List[ColEnum#Column] = {
    s( cols1,tables._1.columnsAsList,
       cols2,tables._2.columnsAsList
      )
  }*/
  
}

class SelectQueryBuilder3[A<:ColEnum,B<:ColEnum,C<:ColEnum](
  val tables: Tuple3[TableDefinition[A],TableDefinition[B],TableDefinition[C]]
) extends CanBuildSelectQuery {

  val cols1 = tables._1.columns
  val cols2 = tables._2.columns
  val cols3 = tables._3.columns

  def mkClause( f: (A,B,C) => String ): String = {
    f( cols1, cols2, cols3 )
  }
  
  def mkSelectQuery( s: (A,List[A#Column],
                         B,List[B#Column],
                         C,List[C#Column]) => List[ColEnum#Column],
                     clauses: String = null ): String = {
    val colsListAsStrList = this.colsListToStrList( selectCols( s ) )    
    val tblsList = List( tables._1.name, tables._2.name, tables._3.name  )
    this._makeSelectQuery( colsListAsStrList, tblsList, Option(clauses) )
  }
 
  def selectCols( s: (A,List[A#Column],
                      B,List[B#Column],
                      C,List[C#Column]) => List[ColEnum#Column] ): List[ColEnum#Column] = {
    s( cols1,tables._1.columnsAsList,
       cols2,tables._2.columnsAsList,
       cols3,tables._3.columnsAsList
      )
  }
  
}

// TODO: implement this class
class SelectQueryBuilder4[A<:ColEnum,B<:ColEnum,C<:ColEnum,D<:ColEnum](
  val tables: Tuple4[TableDefinition[A],TableDefinition[B],TableDefinition[C],TableDefinition[D]]
) extends CanBuildSelectQuery {

  val cols1 = tables._1.columns
  val cols2 = tables._2.columns
  val cols3 = tables._3.columns
  val cols4 = tables._4.columns

  def mkClause( f: (A,B,C,D) => String ): String = {
    f( cols1, cols2, cols3, cols4 )
  }
  
  def mkSelectQuery( s: (A,List[A#Column],
                         B,List[B#Column],
                         C,List[C#Column],
                         D,List[D#Column]) => List[ColEnum#Column],
                     clauses: String = null,
                     excludedTables: Set[String] = null ): String = {
    val colsListAsStrList = this.colsListToStrList( selectCols( s ) )    
    var tblsList = List( tables._1.name, tables._2.name, tables._3.name, tables._4.name )
    if( excludedTables != null ) tblsList = tblsList.filter( excludedTables.contains(_) == false )
    
    this._makeSelectQuery( colsListAsStrList, tblsList, Option(clauses) )
  }
 
  def selectCols( s: (A,List[A#Column],
                      B,List[B#Column],
                      C,List[C#Column],
                      D,List[D#Column]) => List[ColEnum#Column] ): List[ColEnum#Column] = {
    s( cols1,tables._1.columnsAsList,
       cols2,tables._2.columnsAsList,
       cols3,tables._3.columnsAsList,
       cols4,tables._4.columnsAsList
      )
  }
  
}

// TODO: implement this class
class SelectQueryBuilder5[A<:ColEnum,B<:ColEnum,C<:ColEnum,D<:ColEnum,E<:ColEnum] extends CanBuildSelectQuery {  
  private val tables: Tuple5[TableDefinition[A],
                             TableDefinition[B],
                             TableDefinition[C],
                             TableDefinition[D],
                             TableDefinition[E]] = Tuple5(null,null,null,null,null)
}

// TODO: implement this class
class SelectQueryBuilder6[A<:ColEnum,B<:ColEnum,C<:ColEnum,D<:ColEnum,E<:ColEnum,F<:ColEnum] extends CanBuildSelectQuery {  
  private val tables: Tuple6[TableDefinition[A],
                             TableDefinition[B],
                             TableDefinition[C],
                             TableDefinition[D],
                             TableDefinition[E],
                             TableDefinition[F]] = Tuple6(null,null,null,null,null,null)
}