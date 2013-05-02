package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.PeaklistSoftware

/**
 * @author David Bouyssie
 *
 */
trait IPeaklistSoftwareProvider {
  
  def getPeaklistSoftwareListAsOptions( pklSoftIds: Seq[Int] ): Array[Option[PeaklistSoftware]]
  
  def getPeaklistSoftwareList( pklSoftIds: Seq[Int] ): Array[PeaklistSoftware]
  
  def getPeaklistSoftware( pklSoftId: Int ): Option[PeaklistSoftware] = { getPeaklistSoftwareListAsOptions( Seq(pklSoftId) )(0) }
  
  def getPeaklistSoftware( softName: String, softVersion: String ): Option[PeaklistSoftware]
  
}