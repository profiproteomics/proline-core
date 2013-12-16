package fr.proline.core.om.provider.uds.impl

import scala.util.matching.Regex
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.uds.UdsDbPeaklistSoftwareTable
import fr.proline.core.dal.tables.uds.UdsDbSpecTitleParsingRuleTable    
import fr.proline.core.om.model.msi.{PeaklistSoftware,SpectrumTitleParsingRule}
import fr.proline.core.om.provider.uds.IPeaklistSoftwareProvider
import fr.proline.util.primitives._
 
// TODO: use Select Query builder
class SQLPeaklistSoftwareProvider(val udsDbCtx: DatabaseConnectionContext) extends IPeaklistSoftwareProvider {
  
  val PklSoftCols = UdsDbPeaklistSoftwareTable.columns
  val SpecTitleCols = UdsDbSpecTitleParsingRuleTable.columns
  
  def getPeaklistSoftwareListAsOptions( pklSoftIds: Seq[Long] ): Array[Option[PeaklistSoftware]] = {
    val pklSoftById = Map() ++ this.getPeaklistSoftwareList(pklSoftIds).map( ps => ps.id -> ps )
    pklSoftIds.toArray.map( pklSoftById.get(_) )
  }
  
  def getPeaklistSoftwareList( pklSoftIds: Seq[Long] ): Array[PeaklistSoftware] = {
    
    val specRuleById = _getSpectrumTitleParsingRuleById()

    DoJDBCReturningWork.withEzDBC(udsDbCtx, { ezDBC =>

      ezDBC.select("SELECT * FROM peaklist_software WHERE id IN(" + pklSoftIds.mkString(",") +")") { r =>
        _buildNewPeaklistSoftware( r, specRuleById )
      } toArray
      
    })

  }
  
  def getPeaklistSoftware( softName: String, softVersion: String ): Option[PeaklistSoftware] = {
    
    val specRuleById = _getSpectrumTitleParsingRuleById()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { udsEzDBC =>
      udsEzDBC.selectHeadOption(
        "SELECT * FROM peaklist_software WHERE name= ? and version= ? ", softName, softVersion) { r =>
          _buildNewPeaklistSoftware( r, specRuleById )
        }
    })
  }
  
  private def _buildNewPeaklistSoftware(
    r: fr.profi.jdbc.ResultSetRow,
    specRuleById: Map[Long,SpectrumTitleParsingRule]
  ): PeaklistSoftware = {
    val pklSoft = new PeaklistSoftware(id = toLong(r.nextAny), name = r.nextString, version = r.nextStringOrElse(""))
    
    r.getLongOption(PklSoftCols.SPEC_TITLE_PARSING_RULE_ID).map { ruleId =>
      pklSoft.specTitleParsingRule = specRuleById.get(ruleId)
    }
  
    pklSoft
  }
  
  private def _getSpectrumTitleParsingRuleById(): Map[Long,SpectrumTitleParsingRule] = {

    val specTitleRules = DoJDBCReturningWork.withEzDBC(udsDbCtx, { ezDBC =>
      
      ezDBC.select("SELECT * FROM spec_title_parsing_rule") { r =>
        new SpectrumTitleParsingRule(
          id = toLong(r.nextAny),
          rawFileNameRegex = r.getStringOption(SpecTitleCols.RAW_FILE_NAME),
          firstCycleRegex = r.getStringOption(SpecTitleCols.FIRST_CYCLE),
          lastCycleRegex = r.getStringOption(SpecTitleCols.LAST_CYCLE),
          firstScanRegex = r.getStringOption(SpecTitleCols.FIRST_SCAN),
          lastScanRegex = r.getStringOption(SpecTitleCols.LAST_SCAN),
          firstTimeRegex = r.getStringOption(SpecTitleCols.FIRST_TIME),
          lastTimeRegex = r.getStringOption(SpecTitleCols.LAST_TIME)
        )
        
      } toArray
      
    })
    
    Map() ++ specTitleRules.map( rule => rule.id -> rule )
    
  }
  

}

