package fr.proline.core.om.factory

import fr.proline.core.om.msi.ResultSetClasses.ResultSet

trait IResultSetLoader {

 def getResultSet(resultSetId:Int):ResultSet
 
 def getResultSetByIds( resultSetIds: Seq[Int] ):ResultSet
 
}