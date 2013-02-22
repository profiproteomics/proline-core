package fr.proline.core.service.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.orm.uds.{ SpectrumTitleParsingRule => UdsSpectrumTitleParsingRule }
import fr.proline.repository.IDatabaseConnector
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.util.regex.RegexUtils._

class UpdateSpectraParams( dbManager: IDataStoreConnectorFactory,
                           projectId: Int,
                           peaklistId: Int,
                           specTitleRuleId: Int
                          ) extends IService with Logging {
  
  def runService(): Boolean = {
    
    // Open database connections
    val udsEM = dbManager.getUdsDbConnector.getEntityManagerFactory.createEntityManager()
    val msiSqlHelper = new SQLQueryHelper(dbManager.getMsiDbConnector(projectId))
    val ezDBC = msiSqlHelper.ezDBC
    
    val udsSpecTitleParsingRule = udsEM.find(classOf[UdsSpectrumTitleParsingRule], specTitleRuleId)    
    if( udsSpecTitleParsingRule == null ) {
      throw new Exception("undefined parsing rule with id=" + specTitleRuleId )
    }
    
    // Retrieve spectrum attributes
    val specCols = MsiDbSpectrumTable.columns
    val spectrumAttributes = MsiDbSpectrumTable.selectColsAsStrList( t =>
                               List(t.FIRST_SCAN,t.LAST_SCAN, t.FIRST_CYCLE, t.LAST_CYCLE, t.FIRST_TIME, t.LAST_TIME )
                             )
    // Map parsing rule by spectrum attributes
    val parsingRuleBySpecAttr: Map[String,String] = Some(udsSpecTitleParsingRule).map { stpr =>
      Map(  specCols.FIRST_SCAN.toString -> stpr.getFirstScan,
            specCols.LAST_SCAN.toString -> stpr.getLastScan,
            specCols.FIRST_CYCLE.toString -> stpr.getFirstCycle,
            specCols.LAST_CYCLE.toString -> stpr.getLastCycle,
            specCols.FIRST_TIME.toString -> stpr.getFirstTime,
            specCols.LAST_TIME.toString -> stpr.getLastTime
      )
    } get
    
    // Check if a transaction is already initiated
    val wasInTransaction = ezDBC.isInTransaction()
    if( !wasInTransaction ) ezDBC.beginTransaction()
    
    val sqlQuery = "SELECT id, title FROM spectrum WHERE peaklist_id = " + peaklistId
    this.logger.debug("executing SQL query: \""+sqlQuery+"\"")
    
    ezDBC.selectAndProcess( sqlQuery ) { r =>
      
      val spectrumId = r.nextInt
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
      }
      
      ()
    }
    
    // Commit transaction if it was initiated locally
    if( !wasInTransaction ) ezDBC.commitTransaction()
    
    true
  }
  
}