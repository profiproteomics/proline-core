package fr.proline.core.dal.tables

import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.tables.{ColumnEnumeration => ColEnum}

trait CanBuildSelectQuery extends Logging {
  
  def colsListToStrList( colsList: List[ColEnum#Column] ): List[String] = {
    colsList.map { col => col.toFullString }
  }
  
  protected def _makeSelectQuery( colsAsStrList: List[String], tblsList: List[TableDefinition[_]], clauses: Option[String] = None ): String = {
    
    // Retrieve table names as strings
    val tblsAsStrList = tblsList.map(_.name)
    // Map columns names by the table name
    val colNamesByTblName = tblsList.map( tbl => tbl.name -> tbl.columnsAsStrList ) toMap
    
    // Define some vars
    val colCountByName = new collection.mutable.HashMap[String,Int]
    val tblAndColNames = new collection.mutable.ArrayBuffer[Tuple2[String,String]]
    
    // Count columns and unroll stars
    colsAsStrList.foreach { colAsStr =>
      
      // Split table and column names
      val strParts = colAsStr.split("\\.")
      val(tblName, colName) = Tuple2( strParts(0), strParts(1) )
      
      // Unroll column names if special star character has been used
      if( colName == "*" ) {
        val unrolledColNames = colNamesByTblName(tblName)
        for( unrolledColName <- unrolledColNames ) {
          colCountByName(unrolledColName) = colCountByName.getOrElse(unrolledColName, 0) + 1
          tblAndColNames += tblName -> unrolledColName
        }
      // Else just count the column and append it to the list as is
      } else {
        colCountByName(colName) = colCountByName.getOrElse(colName, 0) + 1
        tblAndColNames += tblName -> colName
      }
      
    }
    
    // Alias duplicated columns in the current select query
    val aliasedColsAsStrList = new collection.mutable.ArrayBuffer[String]
    for( tblAndColName <- tblAndColNames ) {
      val( tblName, colName ) = tblAndColName
      
      if( colCountByName(colName) > 1 ) {
        
        val colAlias = tblName +"_"+ colName
        this.logger.debug("duplicated column ("+colName+") detected and automatically aliased as " + colAlias)
        
        // Update column count for this alias
        colCountByName(colAlias) = colCountByName.getOrElse(colAlias, 0) + 1
        
        aliasedColsAsStrList += (tblName +"."+ colName +" AS "+ colAlias )
      } else {
        aliasedColsAsStrList += (tblName +"."+ colName)
      }
    }
    
    var query = "SELECT "+ aliasedColsAsStrList.mkString(",") +" FROM "+ tblsAsStrList.mkString(",")
    if( clauses.isDefined ) query += " " + clauses.get
    
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

  def mkSelectQuery( fn: (A,List[A#Column]) => Tuple2[List[ColEnum#Column],String] ): String = {
    val( colsList, clauses ) = fn(table.columns,table.columnsAsList)
    val tblsList = List( table )
    this._makeSelectQuery( this.colsListToStrList( colsList ), tblsList, Option(clauses) )
  }

}

class SelectQueryBuilder2[A<:ColEnum,B<:ColEnum](
  val tables: Tuple2[TableDefinition[A],TableDefinition[B]]
)extends CanBuildSelectQuery {
  
  def mkSelectQuery( fn: (A,List[A#Column],B,List[B#Column]) => Tuple2[List[ColEnum#Column],String] ): String = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2 )
    
    this._makeSelectQuery( this.colsListToStrList( colsList ), tblsList, Option(clauses) )
  }
  
}

class SelectQueryBuilder3[A<:ColEnum,B<:ColEnum,C<:ColEnum](
  val tables: Tuple3[TableDefinition[A],TableDefinition[B],TableDefinition[C]]
) extends CanBuildSelectQuery {
  
  def mkSelectQuery(
    fn: (
      A,List[A#Column],
      B,List[B#Column],
      C,List[C#Column]
    ) => Tuple2[List[ColEnum#Column],String] ): String = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList,
      tables._3.columns,tables._3.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2, tables._3 )
    
    this._makeSelectQuery( this.colsListToStrList( colsList ), tblsList, Option(clauses) )
  }

  /*val cols1 = tables._1.columns
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
    val tblsList = List( tables._1, tables._2, tables._3  )
    this._makeSelectQuery( colsListAsStrList, tblsList, Option(clauses) )
  }
 
  def selectCols( s: (A,List[A#Column],
                      B,List[B#Column],
                      C,List[C#Column]) => List[ColEnum#Column] ): List[ColEnum#Column] = {
    s( cols1,tables._1.columnsAsList,
       cols2,tables._2.columnsAsList,
       cols3,tables._3.columnsAsList
      )
  }*/
  
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
    var tblsList = List( tables._1, tables._2, tables._3, tables._4 )
    if( excludedTables != null ) tblsList = tblsList.filter( t => excludedTables.contains(t.name) == false )
    
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