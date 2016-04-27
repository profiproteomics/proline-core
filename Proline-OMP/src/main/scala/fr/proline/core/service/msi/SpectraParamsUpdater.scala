package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.context._
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.SpectrumTitleFields.RAW_FILE_IDENTIFIER
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import fr.proline.core.orm.uds.{ SpectrumTitleParsingRule => UdsSpectrumTitleParsingRule }
import fr.profi.util.regex.RegexUtils._
import fr.profi.util.primitives._

object SpectraParamsUpdater extends LazyLogging {
  
  def updateSpectraParams(
    execCtx: IExecutionContext,
    projectId: Long,
    peaklistIds: Array[Long],
    specTitleRuleId: Long
  ): Int = {
    
    var updatedSpectraCount = 0

    for( peaklistId <- peaklistIds ) {
      logger.info("Updating spectra params of peaklist with id="+peaklistId+"...")
      
      val spectraParamsUpdater = new SpectraParamsUpdater( execCtx, projectId, peaklistId, specTitleRuleId )
      spectraParamsUpdater.run()
      
      updatedSpectraCount += spectraParamsUpdater.updatedSpectraCount
    }

    updatedSpectraCount
  }
  
}

class SpectraParamsUpdater(
  execCtx: IExecutionContext,
  projectId: Long,
  peaklistId: Long,
  specTitleRuleId: Long
) extends IService with LazyLogging {
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
    
    val parsingRule = new SpectrumTitleParsingRule(
      id = specTitleRuleId,
      rawFileIdentifierRegex = Option(udsSpecTitleParsingRule.getRawFileIdentifier),
      firstCycleRegex = Option(udsSpecTitleParsingRule.getFirstCycle),
      lastCycleRegex = Option(udsSpecTitleParsingRule.getLastCycle),
      firstScanRegex = Option(udsSpecTitleParsingRule.getFirstScan),
      lastScanRegex = Option(udsSpecTitleParsingRule.getLastScan),
      firstTimeRegex = Option(udsSpecTitleParsingRule.getFirstTime),
      lastTimeRegex = Option(udsSpecTitleParsingRule.getLastTime)
    )
    
    this.logger.debug("Use parsing rule: " + parsingRule)
    
    // Do JDBC work in a managed transaction (rolled back if necessary)
    DoJDBCWork.tryTransactionWithEzDBC(execCtx.getMSIDbConnectionContext()) { ezDBC =>
      val sqlQuery = "SELECT id, title FROM spectrum WHERE peaklist_id = " + peaklistId
      this.logger.debug("Executing SQL query: \""+sqlQuery+"\"")
      
      ezDBC.selectAndProcess( sqlQuery ) { r =>
        
        val spectrumId = r.nextLong
        val spectrumTitle = r.nextString

        val extractedAttrs = parsingRule.parseTitle(spectrumTitle)
        
        // Update spectrum if attributes have been extracted
        if( extractedAttrs.size > 0 ) {
          val attrsToUpdate = extractedAttrs.withFilter(_._1 != RAW_FILE_IDENTIFIER).map { case (k,v) =>
            k.toString().toLowerCase() + "=" + v
          }
          ezDBC.execute( "UPDATE spectrum SET " + attrsToUpdate.mkString(",") + " WHERE id = " + spectrumId )
          updatedSpectraCount += 1
        } else {
          this.logger.trace(s"Can't use parsing rule #$specTitleRuleId to parse information from spectrum title: " + spectrumTitle)
        }
        
        ()
      }
    }

    true
  }
  
}