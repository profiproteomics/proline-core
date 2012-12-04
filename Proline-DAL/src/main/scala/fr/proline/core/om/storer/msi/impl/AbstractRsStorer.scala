package fr.proline.core.om.storer.msi.impl

import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.{MsiDb, DatabaseManagement}
import fr.proline.core.om.model.msi.{ResultSet, Peaklist, MsQuery, InstrumentConfig, IPeaklistContainer}
import fr.proline.core.om.storer.msi.{IRsStorer, IPeaklistWriter}
import fr.proline.repository.DatabaseConnector
import net.noerd.prequel.SQLFormatterImplicits._
import net.noerd.prequel.ReusableStatement
import javax.transaction.Transaction
import javax.persistence.EntityTransaction

abstract class AbstractRsStorer(val dbManagement : DatabaseManagement, val msiDbConnector : DatabaseConnector, val plWriter : IPeaklistWriter = null) extends IRsStorer {
  
  // IPeaklistWriter to use to store PeakList and Spectrum 
  val localPlWriter = if (plWriter == null) IPeaklistWriter.apply(msiDbConnector.getProperty(DatabaseConnector.PROPERTY_DRIVERCLASSNAME))else plWriter
  
  //Other constructor
  def this (dbManagement : DatabaseManagement, projectID : Int){
    this(dbManagement, dbManagement.getMSIDatabaseConnector(projectID,true))
  }
  
  type MsiResultSet = fr.proline.core.orm.msi.ResultSet

  /**
   * Store specified ResultSet in persistence repository, using  storerContext for context and mapping information
   * 
   * This default implementation of IRsStorer will call  final def storeResultSet(...) method without 
   * specifying spectra or MsQueries
   * 
   */
  def storeResultSet( resultSet: ResultSet, storerContext: StorerContext ): Int = {
    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }
        
    val omResultSetId = resultSet.id
    
    var localContext: StorerContext = if (storerContext == null) {
      new StorerContext(dbManagement, msiDbConnector)
    } else {
      storerContext
    }
    
    storeResultSet( resultSet = resultSet,
      msQueries = null,
      peakListContainer = null,
      storerContext = storerContext )

  }
   
  /**
   * This method will first save spectra and queries related data specified by peakListContainer and msQueries. This will be done using
   * IRsStorer storePeaklist, storeMsiSearch,  storeMsQueries and storeSpectra methods
   * 
   * Then the implementation of the abstract createResultSet method will be executed to save other data. 
   * TODO : use other Storer / Writer : peptideStorer / ProteinMatch 
   * 
   * 
   */
  final def storeResultSet(resultSet : ResultSet, msQueries : Seq [MsQuery], peakListContainer : IPeaklistContainer, storerContext : StorerContext) : Int = {
   
    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }
    
    logger.info(" Storing ResultSet "+resultSet.name)

    //Create a StorerContext if none was specified
    var localContext: StorerContext = if (storerContext == null) {
      new StorerContext(dbManagement, msiDbConnector)
    } else {
      storerContext
    }    
        
    if (resultSet.id > 0) 
       throw new UnsupportedOperationException("Updating a ResultSet is not supported yet !")
    
    var msiTransaction : EntityTransaction = null	//TODO : Remove when shared transaction !!!
		var msiTransacOk : Boolean = false	//TODO : Remove when shared transaction !!!
		var plID : Int = -1
		
		try { //TODO : Remove when shared transaction !!!
		  
	      // Save Spectra and Queries information (MSISearch should  be defined)
				if(resultSet.msiSearch != null) {

				  // Save Peaklist information
				  plID = this.storePeaklist(resultSet.msiSearch.peakList, localContext)
				  //update Peaklist ID in MSISearch
				  resultSet.msiSearch.peakList.id = plID 				  

				  // Save spectra retrieve by peakListContainer 
				  if(peakListContainer != null)	  
				  	this.storeSpectra(plID, peakListContainer, localContext) 
			  
				  //TODO : Remove when shared transaction !!! : Close msiDB connection if used by previous store methods 
			  	if(localContext.msiDB.isInTransaction)
			  		localContext.msiDB.commitTransaction
				
			    //START EM Transaction TODO : Remove when shared transaction !!!
		  		msiTransaction =  localContext.msiEm.getTransaction
		  		msiTransaction.begin()
				
					
		  		/* Store MsiSearch and retrieve persisted ORM entity */
		  		val tmpMsiSearchID = resultSet.msiSearch.id
		  		val newMsiSearchID =  storeMsiSearch(resultSet.msiSearch, localContext)
 
		  		// Save MSQueries 
		  		if(msQueries!=null && !msQueries.isEmpty)
		  			localContext = storeMsQueries(newMsiSearchID, msQueries, localContext)
				}
  
				resultSet.id =  createResultSet(resultSet, localContext)
				
				//Commit EM Transaction TODO : Remove when shared transaction !!!
      	msiTransaction.commit
      	msiTransacOk = true
			} catch {
			  case e: Throwable => {
			   e.printStackTrace
			   logger.debug ("Error "+e.getMessage)
			  }
			    
			} finally {
			  	logger.debug ("FINALLY Rollback if necessary ")
    	  /* Check msiTransaction integrity */
			  if(!msiTransacOk){
			    if (msiTransaction != null){
      			try {
      				logger.debug ("Rollbacking EM Msi Db transaction")
      				msiTransaction.rollback()    				    				
      			} catch {
          		case ex => logger.error("Error rollbacking Msi Db transaction", ex)
      			}			   
			    }
			    if(plID>0){
			      logger.debug ("Rollbacking EM Msi Db transaction")			    
			    	//	ROLLBACK SQM MODIFICATION TODO : Remove when shared transaction !!!
			    	plWriter.rollBackInfo(plID, localContext)
			    }
			  }
    	}
			
     resultSet.id
  }
  
  def createResultSet( resultSet : ResultSet, context : StorerContext) : Int
  
  /**
   * Use Constructor specified IPeaklistWriter 
   * 
   */
  def storeSpectra( peaklistId: Int, peaklistContainer: IPeaklistContainer, context : StorerContext ): StorerContext = {
    localPlWriter.storeSpectra(peaklistId, peaklistContainer, context)
  }
  
  /**
   * Use Constructor specified IPeaklistWriter 
   * 
   */
  def storePeaklist(peaklist: Peaklist, context : StorerContext):Int = {
    localPlWriter.storePeaklist(peaklist, context)
  }
  
  def insertInstrumentConfig(instrumCfg : InstrumentConfig, context : StorerContext) = {
     require( instrumCfg.id > 0, "instrument configuration must have a strictly positive identifier" )
    
    // Check if the instrument config exists in the MSIdb
    val count = context.msiDB.getOrCreateTransaction.selectInt( "SELECT count(*) FROM instrument_config WHERE id=" + instrumCfg.id )
    
    // If the instrument config doesn't exist in the MSIdb
    if( count == 0 ) {
      context.msiDB.getOrCreateTransaction.executeBatch("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
        stmt.executeWith( instrumCfg.id,
                          instrumCfg.name,
                          instrumCfg.ms1Analyzer,
                          Option(instrumCfg.msnAnalyzer),
                          Option.empty[String]
                         )
      }
    }
  }
}