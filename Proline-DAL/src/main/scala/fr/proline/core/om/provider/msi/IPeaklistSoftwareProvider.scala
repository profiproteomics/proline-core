package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeaklistSoftware

/**
 * @author David Bouyssie
 *
 */
trait IPeaklistSoftwareProvider {
  
  def getPeaklistSoftwareListAsOptions( pklSoftIds: Seq[Long] ): Array[Option[PeaklistSoftware]]
  
  def getPeaklistSoftwareList( pklSoftIds: Seq[Long] ): Array[PeaklistSoftware]
  
  def getPeaklistSoftware( pklSoftId: Long ): Option[PeaklistSoftware] = { getPeaklistSoftwareListAsOptions( Seq(pklSoftId) )(0) }
  
  def getPeaklistSoftware( softName: String, softVersion: String ): Option[PeaklistSoftware]
  
}