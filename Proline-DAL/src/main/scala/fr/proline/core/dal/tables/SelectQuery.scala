package fr.proline.core.dal.tables

import fr.profi.jdbc._
import scala.collection.mutable.ArrayBuffer

class SelectQuery private[tables](
  val queryString: String,
  val columns: Seq[ColumnEnumeration#Column],
  val colsAsStrList: Seq[String]
) {
  
  //val colStrByCol = columns.view.zip( colsAsStrList ).toMap
  val colIdxByCol = columns.view.zipWithIndex.toMap
  
  /**
   * Returns all records returned by the query after being converted by the
   * given block. All objects are kept in memory to this method is no suited
   * for very big result sets. Use selectAndProcess if you need to process
   * bigger datasets.
   *
   * @param sql query that should return records
   * @param params are the optional parameters used in the query
   * @param block is a function converting the row to something else
   */
  def select[T](queryExecutor: SQLQueryExecution, params: ISQLFormattable*)(block: SelectQueryResult => T): Seq[T] = {
    queryExecutor.select(queryString, params: _*) { resultSetRow =>
      block( new SelectQueryResult(resultSetRow, colIdxByCol) )
    }
  }

  /**
   * Executes the query and passes each row to the given block. This method
   * does not keep the objects in memory and returns Unit so the row needs to
   * be fully processed in the block.
   *
   * @param sql query that should return records
   * @param params are the optional parameters used in the query
   * @param block is a function fully processing each row
   */
  def selectAndProcess(queryExecutor: SQLQueryExecution, params: ISQLFormattable*)(block: SelectQueryResult => Unit): Unit = {
    queryExecutor.selectAndProcess(queryString, params: _*) { resultSetRow =>
      block( new SelectQueryResult(resultSetRow, colIdxByCol) )
    }
  }

}