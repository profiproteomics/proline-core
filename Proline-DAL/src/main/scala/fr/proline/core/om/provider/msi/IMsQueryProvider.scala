package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.MsQuery
import fr.proline.context.DatabaseConnectionContext

trait IMsQueryProvider {
  
  val msiDbCtx: DatabaseConnectionContext
  
  // TODO: remove comments when JPA implementation is done
  
  //def getMsQueriesAsOptions( msQueryIds: Seq[Int] ): Array[Option[MsQuery]]
  
  //def getMsQueries( msQueryIds: Seq[Int] ): Array[MsQuery]
  
  def getMsiSearchesMsQueries( msiSearchIds: Seq[Int] ): Array[MsQuery]
  
  //def getMsQuery( initialQueryId: Int, msiSearchId: Int ) : Option[MsQuery]
  
  //def getMsQuery( msQueryId: Int ): Option[MsQuery] = { getMsQueriesAsOptions( Array(msQueryId) )(0) }
  
  def getMsiSearchMsQueries( msiSearchId: Int ): Array[MsQuery] = {
    getMsiSearchesMsQueries( Array(msiSearchId) )
  }
  
}