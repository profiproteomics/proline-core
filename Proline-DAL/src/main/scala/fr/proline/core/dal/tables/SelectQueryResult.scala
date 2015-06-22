package fr.proline.core.dal.tables

import java.util.Date

import java.sql.ResultSet
import java.sql.Timestamp

import fr.profi.jdbc.ResultSetRow
import fr.profi.util.primitives.IValueContainer

import fr.proline.core.dal.tables.{ColumnEnumeration => ColEnum}

class SelectQueryResult private[tables](
  val resultSetRow: ResultSetRow,
  val colIdxByCol: Map[ColumnEnumeration#Column, Int]
) {
  
  val rs = resultSetRow.rs

  def getBoolean(column: ColEnum#Column): Boolean = _getValue(column,rs.getBoolean)
  def getInt(column: ColEnum#Column): Int = _getValue(column,rs.getInt)
  def getLong(column: ColEnum#Column): Long = _getValue(column,rs.getLong)
  def getFloat(column: ColEnum#Column): Float = _getValue(column,rs.getFloat)
  def getDouble(column: ColEnum#Column): Double = _getValue(column,rs.getDouble)
  def getBytes(column: ColEnum#Column): Array[Byte] = _getValue(column,rs.getBytes )
  def getString(column: ColEnum#Column): String = _getValue(column,rs.getString)
  def getTimestamp(column: ColEnum#Column): Timestamp = _getValue(column,rs.getTimestamp)
  def getDate(column: ColEnum#Column): Date = _getValue(column,rs.getTimestamp)
  def getAnyRef(column: ColEnum#Column): AnyRef = _getValue(column,rs.getObject)
  def getAny(column: ColEnum#Column): Any = _getValue(column,rs.getObject)
  def getAnyVal(column: ColEnum#Column): AnyVal = _getValue(column,rs.getObject).asInstanceOf[AnyVal]
  
  def getBooleanOption(column: ColEnum#Column): Option[Boolean] = _getValueOption(column,rs.getBoolean)
  def getIntOption(column: ColEnum#Column): Option[Int] = _getValueOption(column,rs.getInt)
  def getLongOption(column: ColEnum#Column): Option[Long] = _getValueOption(column,rs.getLong)
  def getFloatOption(column: ColEnum#Column): Option[Float] = _getValueOption(column,rs.getFloat)
  def getDoubleOption(column: ColEnum#Column): Option[Double] = _getValueOption(column,rs.getDouble)
  def getBytesOption(column: ColEnum#Column): Option[Array[Byte]] = _getValueOption(column,rs.getBytes )
  def getStringOption(column: ColEnum#Column): Option[String] = _getValueOption(column,rs.getString)
  def getTimestampOption(column: ColEnum#Column): Option[Timestamp] = _getValueOption(column,rs.getTimestamp)
  def getDateOption(column: ColEnum#Column): Option[Date] = _getValueOption(column,rs.getTimestamp)
  def getAnyRefOption(column: ColEnum#Column): Option[AnyRef] = _getValueOption(column,rs.getObject)
  def getAnyOption(column: ColEnum#Column): Option[Any] = _getValueOption(column,rs.getObject)
  def getAnyValOption(column: ColEnum#Column): Option[AnyVal] = _getValueOption(column,rs.getObject).map( _.asInstanceOf[AnyVal] )
  
  def getBooleanOrElse( column: ColEnum#Column, value: Boolean ): Boolean = _getValueOrElse(column,rs.getBoolean,value)
  def getIntOrElse( column: ColEnum#Column, value: Int ): Int = _getValueOrElse(column,rs.getInt,value)
  def getLongOrElse( column: ColEnum#Column, value: Long ): Long = _getValueOrElse(column,rs.getLong,value)
  def getFloatOrElse( column: ColEnum#Column, value: Float ): Float = _getValueOrElse(column,rs.getFloat,value)
  def getDoubleOrElse( column: ColEnum#Column, value: Double ): Double = _getValueOrElse(column,rs.getDouble,value)
  def getBytesOrElse( column: ColEnum#Column, value: Array[Byte] ): Array[Byte] = _getValueOrElse(column,rs.getBytes,value)
  def getStringOrElse( column: ColEnum#Column, value: String ): String = _getValueOrElse(column,rs.getString,value)
  def getTimestampOrElse( column: ColEnum#Column, value: Timestamp ): Timestamp = _getValueOrElse(column,rs.getTimestamp,value)
  def getDateOrElse( column: ColEnum#Column, value: Date ): Date = _getValueOrElse(column,rs.getTimestamp,value)
  def getAnyRefOrElse( column: ColEnum#Column, value: AnyRef ): AnyRef = _getValueOrElse(column,rs.getObject,value)
  def getAnyOrElse( column: ColEnum#Column, value: Any ): Any = _getValueOrElse(column,rs.getObject,value)
  def getAnyValOrElse( column: ColEnum#Column, value: AnyVal ): AnyVal = _getValueOrElse(column,rs.getObject,value).asInstanceOf[AnyVal]
  
  private def _getValue[T](column: ColEnum#Column, f: (Int) => T): T = f(colIdxByCol(column))
  
  def _getValueOption[T](column: ColEnum#Column, f: (Int) => T): Option[T] = {
    val value = f(colIdxByCol(column))
    if (rs.wasNull) None
    else Option(value)
  }
  
  private def _getValueOrElse[T](column: ColEnum#Column, f: (String) => T, defaultValue: T): T = {    
    val value = f(column)
    if (rs.wasNull) defaultValue
    else value
  }
}
