package fr.proline.core.om.storer.msi.impl

import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.IPeaklistContainer
import fr.proline.core.om.model.msi.MsQuery
import fr.proline.core.dal.DatabaseManagement
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.core.dal.MsiDbSpectrumTable
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.utils.lzma.EasyLzma
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.orm.msi.MsiSearch
import fr.proline.repository.DatabaseConnector

abstract class AbstractRsStorer(val dbManagement : DatabaseManagement, val msiDbConnector : DatabaseConnector = null ) extends IRsStorer {
  
  def this (dbManagement : DatabaseManagement, projectID : Int = -1){
    this(dbManagement, dbManagement.getMSIDatabaseConnector(projectID,true))
  }
  
  type MsiResultSet = fr.proline.core.orm.msi.ResultSet
  import fr.proline.core.utils.sql.newDecimalFormat
  
  protected val doubleFormatter = newDecimalFormat("0.000000")
  protected val floatFormatter = newDecimalFormat("0.00")
  
  object TrailingZerosStripper {
    
    private val decimalParser = """(\d+\.\d*?)0*$""".r
    
    def apply( decimalAsStr: String ): String = {
      val decimalParser(compactDecimal) = decimalAsStr
      compactDecimal
    }
  }
  
  def storeResultSet( resultSet: ResultSet, context: StorerContext ): Int = {
    val RsTempId = resultSet.id
    val retContext: StorerContext = storeResultSet( resultSet = resultSet,
      msQueries = null,
      peakListContainer = null,
      context = context )

    val createdMsiResultSet = retContext.getEntityCache( classOf[MsiResultSet] ).get( RsTempId )
    if ( createdMsiResultSet.isDefined )
      createdMsiResultSet.get.getId
    else
      throw new Exception("ResultSet Storer : Created ResultSet not found !! ")
  }
    
  final def storeResultSet(resultSet : ResultSet, msQueries : Seq [MsQuery], peakListContainer : IPeaklistContainer, context : StorerContext) : StorerContext = {
    
    if (resultSet == null) {
      throw new IllegalArgumentException("ResultSet is null")
    }    
        
    var retCtxt = if(context == null) new StorerContext(dbManagement, msiDbConnector) else  context
    
	if(resultSet.msiSearch != null) {
	  //Save Peaklist information
	  val plID = storePeaklist(resultSet.msiSearch.peakList, retCtxt)
	  resultSet.msiSearch.peakList.id = plID //update Peaklist ID in MSISearch
	  if(peakListContainer != null)	  
		  storeSpectra(plID, peakListContainer, context)//Save spectra retrieve by peakListContainer 
	  
      val tmpMsiSearchID = resultSet.msiSearch.id
	  retCtxt =  storeMsiSearch(resultSet.msiSearch, context)//Save MSISearch and related information

      val createdMsiSearch = retCtxt.getEntityCache( classOf[MsiSearch] ).get( tmpMsiSearchID )
      if(createdMsiSearch.isDefined)
        resultSet.msiSearch.id = createdMsiSearch.get.getId
      else
        throw new Exception("ResultSet Storer : Created MsiSearch not found !! ")
	  
      if(msQueries!=null && !msQueries.isEmpty)
	    storeMsQueries(resultSet.msiSearch.id, msQueries, context)

	}
  
    
    retCtxt
  }
  
  def storeSpectra( peaklistId: Int, peaklistContainer: IPeaklistContainer, context : StorerContext ): StorerContext = {
    logger.info( "storing spectra..." )
 
    val spectrumColsList = MsiDbSpectrumTable.getColumnsAsStrList().filter { _ != "id" }
    val spectrumInsertQuery = MsiDbSpectrumTable.makeInsertQuery( spectrumColsList )
    
    // Insert corresponding spectra
    val spectrumIdByTitle = collection.immutable.Map.newBuilder[String,Int]
    context.msiDB.getOrCreateTransaction.executeBatch( spectrumInsertQuery ) { stmt =>
      peaklistContainer.eachSpectrum { spectrum => 
        this._insertSpectrum( stmt, spectrum, peaklistId, context)
        spectrumIdByTitle += ( spectrum.title -> spectrum.id )
      }
    }    
    
    context.spectrumIdByTitle =  spectrumIdByTitle.result()
    context
  }
   
   private def _insertSpectrum( stmt: ReusableStatement, spectrum: Spectrum, peaklistId: Int , context : StorerContext): Unit = {
    
    // Define some vars
    val precursorIntensity = if( !spectrum.precursorIntensity.isNaN ) Some(spectrum.precursorIntensity) else Option.empty[Float]
    val firstCycle = if( spectrum.firstCycle > 0 ) Some(spectrum.firstCycle) else Option.empty[Int]
    val lastCycle = if( spectrum.lastCycle > 0 ) Some(spectrum.lastCycle) else Option.empty[Int]
    val firstScan = if( spectrum.firstScan > 0 ) Some(spectrum.firstScan) else Option.empty[Int]
    val lastScan = if( spectrum.lastScan > 0 ) Some(spectrum.lastScan) else Option.empty[Int]
    val firstTime = if( spectrum.firstTime > 0 ) Some(spectrum.firstTime) else Option.empty[Float]
    val lastTime = if( spectrum.lastTime > 0 ) Some(spectrum.lastTime) else Option.empty[Float]
    
    // moz and intensity lists are formatted as numbers separated by spaces
    val tzs = TrailingZerosStripper
    val mozList = spectrum.mozList.getOrElse(Array.empty[Double]).map { m => tzs(this.doubleFormatter.format( m )) } mkString(" ")
    val intList = spectrum.intensityList.getOrElse(Array.empty[Float]).map { i => tzs(this.floatFormatter.format( i )) } mkString(" ")
    
    // Compress peaks
    val compressedMozList = EasyLzma.compress( mozList.getBytes )
    val compressedIntList = EasyLzma.compress( intList.getBytes )
    
    stmt <<
      spectrum.title <<
      spectrum.precursorMoz <<
      precursorIntensity <<
      spectrum.precursorCharge <<
      spectrum.isSummed <<
      firstCycle <<
      lastCycle <<
      firstScan <<
      lastScan <<
      firstTime <<
      lastTime <<
      Option(null) <<
      Option(null) <<
      spectrum.peaksCount <<
      Option(null) <<
      peaklistId <<
      spectrum.instrumentConfigId
    
    // Override BLOB values using JDBC
    stmt.wrapped.setBytes(12,compressedMozList)
    stmt.wrapped.setBytes(13,compressedIntList)
    
    // Execute statement
    stmt.execute()

    spectrum.id = context.msiDB.extractGeneratedInt( stmt.wrapped )
    
    ()
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