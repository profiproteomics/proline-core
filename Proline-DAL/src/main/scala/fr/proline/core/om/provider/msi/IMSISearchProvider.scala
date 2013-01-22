package fr.proline.core.om.provider.msi

import fr.proline.repository.DatabaseContext
import fr.proline.core.om.model.msi.MSISearch

trait IMSISearchProvider {

  val udsDbCtx: DatabaseContext
  val msiDbCtx: DatabaseContext
  val psDbCtx: DatabaseContext

  def getMSISearchesAsOptions( msiSearchIds: Seq[Int] ): Array[Option[MSISearch]]
  
  def getMSISearches( msiSearchIds: Seq[Int] ): Array[MSISearch]
  
  def getMSISearch( msiSearchId:Int ): Option[MSISearch] = { getMSISearchesAsOptions( Array(msiSearchId) )(0) }
 
  
}