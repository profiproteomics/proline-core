package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.MSISearch

trait IMSISearchProvider {

  def getMSISearchesAsOptions( msiSearchIds: Seq[Long] ): Array[Option[MSISearch]]
  
  def getMSISearches( msiSearchIds: Seq[Long] ): Array[MSISearch]
  
  def getMSISearch( msiSearchId:Long ): Option[MSISearch] = { getMSISearchesAsOptions( Array(msiSearchId) )(0) }

}