package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.FragmentationRuleSet

trait IFragmentationRuleProvider {

  def getFragmentationRuleSetsAsOptions( fragRuleSetIds: Seq[Long] ): Array[Option[FragmentationRuleSet]]

  def getFragmentationRuleSets( fragRuleSetIds: Seq[Long] ): Array[FragmentationRuleSet]

  def getFragmentationRuleSet( fragRuleSetIds: Long ): Option[FragmentationRuleSet] = { getFragmentationRuleSetsAsOptions( Seq(fragRuleSetIds) )(0) }

  /**
    * Search for a FragmentationRuleSet with specified name. A unique FragmentationRuleSet should be found
    */
  def getFragmentationRuleSet( name: String): Option[FragmentationRuleSet]

}
