package fr.proline.core.dal.tables

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.dal.tables.{ColumnEnumeration => ColEnum}

trait CanBuildSelectQuery extends LazyLogging {
  
  def colsListToStrList( colsList: List[ColEnum#Column] ): List[String] = {
    colsList.map { col => col.toFullString }
  }
  
  // TODO: add a forceAliasing boolean ???
  protected def _buildSelectQuery(
    colsList: List[ColEnum#Column],
    tblsList: List[TableDefinition[_]],
    clauses: Option[String] = None
  ): SelectQuery = {
    
    val colsAsStrList = this.colsListToStrList( colsList )
    
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
        this.logger.trace("duplicated column ("+colName+") detected and automatically aliased as " + colAlias)
        
        // Update column count for this alias
        colCountByName(colAlias) = colCountByName.getOrElse(colAlias, 0) + 1
        
        aliasedColsAsStrList += (s"$tblName.$colName AS $colAlias" )
      } else {
        aliasedColsAsStrList += (s"$tblName.$colName")
      }
    }
    
    val sb = new StringBuilder("SELECT "+ aliasedColsAsStrList.mkString(",") +" FROM "+ tblsAsStrList.mkString(","))
    if( clauses.isDefined ) sb.append(' ').append( clauses.get )
    
    new SelectQuery(sb.result(), colsList, aliasedColsAsStrList)
  }
  
  protected def _makeSelectQuery( colsList: List[ColEnum#Column], tblsList: List[TableDefinition[_]], clauses: Option[String] = None ): String = {
    this._buildSelectQuery(colsList, tblsList, clauses).queryString
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

  def buildSelectQuery( fn: (A,List[A#Column]) => Tuple2[List[ColEnum#Column],String] ): SelectQuery = {
    val( colsList, clauses ) = fn(table.columns,table.columnsAsList)
    val tblsList = List( table )
    this._buildSelectQuery( colsList, tblsList, Option(clauses) )
  }
  
  def mkSelectQuery( fn: (A,List[A#Column]) => Tuple2[List[ColEnum#Column],String] ): String = {
    val( colsList, clauses ) = fn(table.columns,table.columnsAsList)
    val tblsList = List( table )
    this._makeSelectQuery( colsList, tblsList, Option(clauses) )
  }

}

class SelectQueryBuilder2[A<:ColEnum,B<:ColEnum](
  val tables: Tuple2[TableDefinition[A],TableDefinition[B]]
) extends CanBuildSelectQuery {
  
  def buildSelectQuery( fn: (A,List[A#Column],B,List[B#Column]) => Tuple2[List[ColEnum#Column],String] ): SelectQuery = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2 )
    
    this._buildSelectQuery( colsList, tblsList, Option(clauses) )
  }
  
  def mkSelectQuery( fn: (A,List[A#Column],B,List[B#Column]) => Tuple2[List[ColEnum#Column],String] ): String = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2 )
    
    this._makeSelectQuery( colsList, tblsList, Option(clauses) )
  }
  
}

class SelectQueryBuilder3[A<:ColEnum,B<:ColEnum,C<:ColEnum](
  val tables: Tuple3[TableDefinition[A],TableDefinition[B],TableDefinition[C]]
) extends CanBuildSelectQuery {
  
  def buildSelectQuery(
    fn: (
      A,List[A#Column],
      B,List[B#Column],
      C,List[C#Column]
    ) => Tuple2[List[ColEnum#Column],String] ): SelectQuery = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList,
      tables._3.columns,tables._3.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2, tables._3 )
    
    this._buildSelectQuery( colsList, tblsList, Option(clauses) )
  }
  
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
    
    this._makeSelectQuery( colsList, tblsList, Option(clauses) )
  }
  
}

class SelectQueryBuilder4[A<:ColEnum,B<:ColEnum,C<:ColEnum,D<:ColEnum](
  val tables: Tuple4[TableDefinition[A],TableDefinition[B],TableDefinition[C],TableDefinition[D]]
) extends CanBuildSelectQuery {
  
  def buildSelectQuery(
    fn: (
      A,List[A#Column],
      B,List[B#Column],
      C,List[C#Column],
      D,List[D#Column]
    ) => Tuple2[List[ColEnum#Column],String] ): SelectQuery = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList,
      tables._3.columns,tables._3.columnsAsList,
      tables._4.columns,tables._4.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2, tables._3, tables._4 )
    
    this._buildSelectQuery( colsList, tblsList, Option(clauses) )
  }

  def mkSelectQuery(
    fn: (
      A,List[A#Column],
      B,List[B#Column],
      C,List[C#Column],
      D,List[D#Column]
    ) => Tuple2[List[ColEnum#Column],String] ): String = {
    
    val( colsList, clauses ) = fn(
      tables._1.columns,tables._1.columnsAsList,
      tables._2.columns,tables._2.columnsAsList,
      tables._3.columns,tables._3.columnsAsList,
      tables._4.columns,tables._4.columnsAsList
    )
    
    val tblsList = List( tables._1, tables._2, tables._3, tables._4 )
    
    this._makeSelectQuery( colsList, tblsList, Option(clauses) )
  }
  
}

/*
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
}*/