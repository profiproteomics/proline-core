package fr.proline.core.om.provider.msi.impl

import fr.profi.jdbc.easy._
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.uds.UdsDbFragmentationRuleSetColumns
import fr.proline.core.om.builder.InstrumentConfigBuilder
import fr.proline.core.om.model.msi.FragmentationRuleSet
import fr.proline.core.om.provider.msi.IFragmentationRuleProvider


class SQLFragmentationRuleProvider(val udsDbCtx: UdsDbConnectionContext) extends IFragmentationRuleProvider {

  override def getFragmentationRuleSetsAsOptions(fragRuleSetIds: Seq[Long]): Array[Option[FragmentationRuleSet]]  = {
    val fragRuleSetById = Map() ++ this.getFragmentationRuleSets(fragRuleSetIds).map( frs => frs.id -> frs )
    fragRuleSetIds.toArray.map( fragRuleSetById.get(_) )
  }

  override def getFragmentationRuleSets(fragRuleSetIds: Seq[Long]): Array[FragmentationRuleSet] = {
    if(fragRuleSetIds.isEmpty) return Array()

    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>

      InstrumentConfigBuilder.buildFragmentationRuleSets(
        SQLInstrumentConfigProvider.selectFragRuleSetsRecords(udsEzDBC,fragRuleSetIds),
        frsIds => SQLInstrumentConfigProvider.selectFragmentationSeriesRecords(udsEzDBC,frsIds)
      )
    }

  }


  /**
    * Search for a FragmentationRuleSet with specified name. A unique FragmentationRuleSet should be found
    */
  override def getFragmentationRuleSet( name: String): Option[FragmentationRuleSet] = {
    if (name== null) return None
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>

      val frsArray =   InstrumentConfigBuilder.buildFragmentationRuleSets(
        SQLInstrumentConfigProvider.selectFragRuleSetsRecordsWithName(udsEzDBC,name),
        frsIds => SQLInstrumentConfigProvider.selectFragmentationSeriesRecords(udsEzDBC,frsIds)
      )

      if( frsArray != null && frsArray.length>1)
        throw new RuntimeException("Multiple Fragmentation Rule Set with "+name)
      if(frsArray==null ||frsArray.isEmpty)
        None
      else
        Some(frsArray.apply(0))
    }

  }

}
