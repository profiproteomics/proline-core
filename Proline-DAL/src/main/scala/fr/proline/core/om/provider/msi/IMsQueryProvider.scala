package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.MsQuery
import fr.proline.context.DatabaseConnectionContext

trait IMsQueryProvider {
  
  // TODO: remove comments when JPA implementation is done
  
  //def getMsQueriesAsOptions( msQueryIds: Seq[Int] ): Array[Option[MsQuery]]
  
  def getMsQueries( msQueryIds: Seq[Long] ): Array[MsQuery]
  
  def getMsiSearchesMsQueries( msiSearchIds: Seq[Long] ): Array[MsQuery]
  
  //def getMsQuery( initialQueryId: Int, msiSearchId: Int ) : Option[MsQuery]
  
  //def getMsQuery( msQueryId: Int ): Option[MsQuery] = { getMsQueriesAsOptions( Array(msQueryId) )(0) }
  
  def getMsiSearchMsQueries( msiSearchId: Long ): Array[MsQuery] = {
    getMsiSearchesMsQueries( Array(msiSearchId) )
  }
  
}