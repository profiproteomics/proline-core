package fr.proline.core.service.msi

import scala.annotation.migration
import com.typesafe.scalalogging.LazyLogging
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.SelectQueryBuilder.any2ClauseAdd
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.SpectrumTitleFields
import fr.proline.core.om.model.msi.SpectrumTitleFields.RAW_FILE_IDENTIFIER
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import javax.persistence.EntityManager
import fr.proline.core.orm.uds.{ PeaklistSoftware => UdsPeaklistSoftware }
import fr.proline.core.orm.uds.{ SpectrumTitleParsingRule => UdsSpectrumTitleParsingRule }
import fr.proline.core.om.model.msi.SpectrumProperties
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.dal.tables.uds.UdsDbPeaklistSoftwareTable
import fr.profi.util.StringUtils

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
    
    val getPeaklistSoftwareQuery = new SelectQueryBuilder1(UdsDbPeaklistSoftwareTable).mkSelectQuery( (t,c) =>
					List(t.ID) -> "WHERE "~ t.SPEC_TITLE_PARSING_RULE_ID ~" = "~ specTitleRuleId)
					
    val peaklistSoftId = DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>      
      udsEzDBC.selectLong(getPeaklistSoftwareQuery)
    }
    this.logger.debug("Peaklist software ID used = " + peaklistSoftId)
    
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
    executeOnProgress() //execute registered action during progress
    this.logger.debug("Use parsing rule: " + parsingRule)
    
    // Do JDBC work in a managed transaction (rolled back if necessary)*
    DoJDBCWork.tryTransactionWithEzDBC(msiDbCtx) { ezDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
					List(t.ID,t.TITLE, t.SERIALIZED_PROPERTIES) -> "WHERE "~ t.PEAKLIST_ID~" = "~ peaklistId)
      
      ezDBC.selectAndProcess( sqlQuery ) { r =>
        
        val spectrumId = r.nextLong
        val spectrumTitle = r.nextString
        val spectrumSerializedProperties = r.nextString      
      
        val extractedAttrs = parsingRule.parseTitle(spectrumTitle)
        val fullExtractAttrs = collection.mutable.Map() ++ extractedAttrs
        for(nextSpectrumField <- SpectrumTitleFields.values){
          //Go through all Field to reset unparsed field
          if(!extractedAttrs.contains(nextSpectrumField)) {
            fullExtractAttrs.put(nextSpectrumField, null)
            if( (SpectrumTitleFields.LAST_TIME.equals(nextSpectrumField) || SpectrumTitleFields.FIRST_TIME.equals(nextSpectrumField))
                  && StringUtils.isNotEmpty(spectrumSerializedProperties) ) {
               //For First_Time, set to RT if none specified 
               val spectrumProperties  = ProfiJson.deserialize[SpectrumProperties](spectrumSerializedProperties)
               logger.trace(" USE RT"+ spectrumSerializedProperties+" for spectrum "+spectrumId)
               if(spectrumProperties.rtInSeconds.isDefined){
                 fullExtractAttrs.put(nextSpectrumField, (spectrumProperties.rtInSeconds.get/60.0f).toString())
               }
            }
          }
        }
        
         // Update spectrum with attributes that have been extracted or null if none found
          val attrsToUpdate = fullExtractAttrs.withFilter(_._1 != RAW_FILE_IDENTIFIER).map { case (k,v) =>
            k.toString().toLowerCase() + "=" + v
          }
          
          ezDBC.execute( "UPDATE spectrum SET " + attrsToUpdate.mkString(",") + " WHERE id = " + spectrumId )
          updatedSpectraCount += 1

        
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