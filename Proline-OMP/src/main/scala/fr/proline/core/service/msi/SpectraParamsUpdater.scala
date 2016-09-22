package fr.proline.core.service.msi

import javax.persistence.EntityManager
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.regex.RegexUtils._
import fr.profi.util.primitives._
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal._
import fr.proline.core.dal.context._
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.SpectrumTitleFields.RAW_FILE_IDENTIFIER
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import fr.proline.core.orm.uds.{ PeaklistSoftware => UdsPeaklistSoftware }
import fr.proline.core.orm.uds.{ SpectrumTitleParsingRule => UdsSpectrumTitleParsingRule }

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
    val msiDbCtx = execCtx.getMSIDbConnectionContext()
    val udsDbCtx = execCtx.getUDSDbConnectionContext()
    val udsEM = execCtx.getUDSDbConnectionContext().getEntityManager()
    
    val peaklistSoftId = DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      udsEzDBC.selectLong("SELECT id FROM peaklist_software WHERE spec_title_parsing_rule_id = " + specTitleRuleId)
    }
    this.logger.debug("Peaklist software ID = " + peaklistSoftId)
    
    // Retrieve the specTitleParsingRule
    val udsSpecTitleParsingRule = udsEM.find(classOf[UdsSpectrumTitleParsingRule], specTitleRuleId) 
    require(udsSpecTitleParsingRule != null, "no spectrum title parsing rule in UDSdb with ID=" + specTitleRuleId)
    
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
    
    // Do JDBC work in a managed transaction (rolled back if necessary)*
    DoJDBCWork.tryTransactionWithEzDBC(msiDbCtx) { ezDBC =>
      
      val sqlQuery = "SELECT id, title FROM spectrum WHERE peaklist_id = " + peaklistId
      this.logger.debug(s"Executing SQL query: $sqlQuery")
      
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
          this.logger.trace(s"Can't use parsing rule #$specTitleRuleId to parse information from spectrum title: #$spectrumTitle ")
        }
        
        ()
      }
      
      this.logger.debug("Updating peaklist software information...")
      this._updatePeaklistSoftware(udsEM, msiDbCtx, peaklistSoftId, peaklistId )
      
    }

    true
  }
  
  private def _updatePeaklistSoftware(
    udsEM: EntityManager,
    msiDbCtx: MsiDbConnectionContext,
    softwareId: Long,
    peaklistId: Long
  ) {
    
    val msiEM = msiDbCtx.getEntityManager
    val existingMsiPklSoft = msiEM.find(classOf[fr.proline.core.orm.msi.PeaklistSoftware], softwareId)
    
    if (existingMsiPklSoft == null) {
      
      // Retrieve the Peaklist Software from the UDSdb
      val udsPklSoft = udsEM.find(classOf[UdsPeaklistSoftware], softwareId) 
      require(udsPklSoft != null, "no peaklist software in UDSdb with ID=" + softwareId)
    
      val newMsiPklSoft = new fr.proline.core.orm.msi.PeaklistSoftware()
      newMsiPklSoft.setId(softwareId)
      newMsiPklSoft.setName(udsPklSoft.getName)
      newMsiPklSoft.setVersion(udsPklSoft.getVersion)
      newMsiPklSoft.setSerializedProperties(udsPklSoft.getSerializedProperties)

      msiEM.persist(newMsiPklSoft)
    }
    
    // Synchronize the persistence context to the underlying database.
    msiEM.flush()
    
    // Update the peaklist_software_id in the peaklist table
    DoJDBCWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      msiEzDBC.execute( s"UPDATE peaklist SET peaklist_software_id = $softwareId WHERE id = $peaklistId" )
    }
  }
  
}