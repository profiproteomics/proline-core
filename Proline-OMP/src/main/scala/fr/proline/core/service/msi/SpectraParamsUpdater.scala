package fr.proline.core.service.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.context._
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.orm.uds.{ SpectrumTitleParsingRule => UdsSpectrumTitleParsingRule }
import fr.proline.util.regex.RegexUtils._
import fr.proline.util.primitives._

class SpectraParamsUpdater(
  execCtx: IExecutionContext,
  projectId: Long,
  peaklistId: Long,
  specTitleRuleId: Long
) extends IService with Logging {
  require(execCtx.isJPA, "Invalid execution context for this service: JPA support is needed")
  require(projectId > 0L , "Invalid projectId value: it must be greater than zero")
  require(peaklistId > 0L , "Invalid peaklistId value: it must be greater than zero")
  require(specTitleRuleId > 0L , "Invalid specTitleRuleId value: it must be greater than zero")
  
  // This var helps to track the number of updated spectra
  var updatedSpectraCount = 0
  
  def runService(): Boolean = {
    
    updatedSpectraCount = 0
    
    // Retrieve udsEM and msiDbCtx
    val udsEM = execCtx.getUDSDbConnectionContext().getEntityManager()
    
    // Retrieve the specTitleParsingRule
    val udsSpecTitleParsingRule = udsEM.find(classOf[UdsSpectrumTitleParsingRule], specTitleRuleId) 
    require(udsSpecTitleParsingRule != null, "no spectrum title parsing rule in UDSdb with id=" + specTitleRuleId)
    
    // Retrieve spectrum attributes
    val specCols = MsiDbSpectrumTable.columns
    val spectrumAttributes = MsiDbSpectrumTable.selectColsAsStrList( t =>
      List(t.FIRST_SCAN,t.LAST_SCAN, t.FIRST_CYCLE, t.LAST_CYCLE, t.FIRST_TIME, t.LAST_TIME )
    )
    
    // Map parsing rule by spectrum attributes
    val parsingRuleBySpecAttr: Map[String,String] = Some(udsSpecTitleParsingRule).map { stpr =>
      Map(
        specCols.FIRST_SCAN.toString -> stpr.getFirstScan,
        specCols.LAST_SCAN.toString -> stpr.getLastScan,
        specCols.FIRST_CYCLE.toString -> stpr.getFirstCycle,
        specCols.LAST_CYCLE.toString -> stpr.getLastCycle,
        specCols.FIRST_TIME.toString -> stpr.getFirstTime,
        specCols.LAST_TIME.toString -> stpr.getLastTime
      )
    } get
    
    // Do JDBC work in a managed transaction (rolled back if necessary)
    DoJDBCWork.tryTransactionWithEzDBC(execCtx.getMSIDbConnectionContext(), { ezDBC =>
      val sqlQuery = "SELECT id, title FROM spectrum WHERE peaklist_id = " + peaklistId
      this.logger.debug("executing SQL query: \""+sqlQuery+"\"")
      
      ezDBC.selectAndProcess( sqlQuery ) { r =>
        
        val spectrumId = toLong(r.nextAny)
        val spectrumTitle = r.nextString
        
        // Extract attributes from spectrum title
        val extractedAttrs = new collection.mutable.HashMap[String,String]
        for( specAttr <- spectrumAttributes ) {
          val parsingRule = parsingRuleBySpecAttr(specAttr)
          if( parsingRule != null ) {
            val parsingRuleMatch = spectrumTitle =# parsingRule
            if( parsingRuleMatch != None ) {
              extractedAttrs(specAttr) = parsingRuleMatch.get.group(1)
            }
          }
        }
        
        // Update spectrum if attributes have been extracted
        if( extractedAttrs.size > 0 ) {
          val attrsToUpdate = extractedAttrs.map { case (k,v) => k + "=" + v }
          ezDBC.execute( "UPDATE spectrum SET " + attrsToUpdate.mkString(",") + " WHERE id = " + spectrumId )
          updatedSpectraCount += 1
        }
        
        ()
      }
    
    })

    true
  }
  
}