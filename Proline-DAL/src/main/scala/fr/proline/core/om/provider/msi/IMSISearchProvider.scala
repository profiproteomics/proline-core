package fr.proline.core.om.provider.msi

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.MSISearch

trait IMSISearchProvider {

  val udsDbCtx: DatabaseConnectionContext
  val msiDbCtx: DatabaseConnectionContext
  val psDbCtx: DatabaseConnectionContext

  def getMSISearchesAsOptions( msiSearchIds: Seq[Int] ): Array[Option[MSISearch]]
  
  def getMSISearches( msiSearchIds: Seq[Int] ): Array[MSISearch]
  
  def getMSISearch( msiSearchId:Int ): Option[MSISearch] = { getMSISearchesAsOptions( Array(msiSearchId) )(0) }
 
  
}