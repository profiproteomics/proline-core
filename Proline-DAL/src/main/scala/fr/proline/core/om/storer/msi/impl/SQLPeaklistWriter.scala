package fr.proline.core.om.storer.msi.impl

import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.IPeaklistContainer
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.utils.lzma.EasyLzma
import fr.proline.util.sql._
import net.noerd.prequel.SQLFormatterImplicits._
import net.noerd.prequel.ReusableStatement
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.core.dal.MsiDbPeaklistTable
import scala.collection.mutable.ArrayBuilder

class SQLPeaklistWriter extends IPeaklistWriter with Logging {
   
  protected val doubleFormatter = newDecimalFormat("#.######")
  protected val floatFormatter = newDecimalFormat("#.##")
  
  //TODO: GET/CREATE Peaklist SOFT
  def storePeaklist(peaklist: Peaklist, context : StorerContext):Int = {
   if (peaklist == null) {
      throw new IllegalArgumentException("peaklist is null")
    }
  	val peaklistColsList = MsiDbPeaklistTable.getColumnsAsStrList().filter { _ != "id" } 
    val peaklistInsertQuery = MsiDbPeaklistTable.makeInsertQuery( peaklistColsList )
    
    val msiDbTx = context.msiDB.getOrCreateTransaction()
    msiDbTx.executeBatch( peaklistInsertQuery, true ) { stmt =>
      stmt.executeWith(
            peaklist.fileType,
            peaklist.path,
            peaklist.rawFileName,
            peaklist.msLevel,
            "xz", // none | lzma | xz TODO: create an enumeration near to the Peaklist class
            Option(null),
            1 // peaklist.peaklistSoftware.id TODO !
          )
          
      peaklist.id = context.msiDB.extractGeneratedInt( stmt.wrapped )
    }
    peaklist.id
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
    val mozList = spectrum.mozList.getOrElse(Array.empty[Double]).map { m => this.doubleFormatter.format( m ) } mkString(" ")
    val intList = spectrum.intensityList.getOrElse(Array.empty[Float]).map { i => this.floatFormatter.format( i ) } mkString(" ")
    
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

   def rollBackInfo(peaklistId: Int, context : StorerContext) =  {
      
     if(peaklistId<0)
       throw new IllegalArgumentException("Peaklist (id <= 0) is not in repository ")     
         
     val stmt = context.msiDB.connection.createStatement()
     
     // Retrieve generated protein match ids
     var idListBulder = new StringBuilder("(")
     context.msiDB.getOrCreateTransaction.select(
                           "id FROM spectrum WHERE spectrum.peaklist_id = "+peaklistId) { r => idListBulder.append(r.nextInt.get)}       
     		      		 
     idListBulder.append(")")     		 
     stmt.executeUpdate("DELETE FROM spectrum WHERE spectrum.id IN "+idListBulder.toString)
     stmt.executeUpdate("DELETE FROM peaklist WHERE peaklist.id = "+peaklistId)
  }
}

class PgSQLSpectraWriter extends SQLPeaklistWriter with Logging {


  override def storeSpectra( peaklistId: Int, peaklistContainer: IPeaklistContainer, context: StorerContext ): StorerContext = {

    val bulkCopyManager = new CopyManager( context.msiDB.connection.asInstanceOf[BaseConnection] )
    
    // Create TMP table
    val tmpSpectrumTableName = "tmp_spectrum_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpSpectrumTableName + "'..." )
    
    val stmt = context.msiDB.connection.createStatement()
    stmt.executeUpdate( "CREATE TEMP TABLE " + tmpSpectrumTableName + " (LIKE spectrum)" )

    // Bulk insert of spectra
    logger.info( "BULK insert of spectra" )

    val spectrumTableCols = MsiDbSpectrumTable.getColumnsAsStrList().filter( _ != "id" ).mkString( "," )

    val pgBulkLoader = bulkCopyManager.copyIn( "COPY " + tmpSpectrumTableName + " ( id, " + spectrumTableCols + " ) FROM STDIN" )

    // Iterate over spectra to store them
    peaklistContainer.eachSpectrum { spectrum =>
      logger.debug( "get spectrum "+ spectrum.title)
      
      // Define some vars
      val precursorIntensity = if ( !spectrum.precursorIntensity.isNaN ) spectrum.precursorIntensity else ""
      val firstCycle = if ( spectrum.firstCycle > 0 ) spectrum.firstCycle else ""
      val lastCycle = if ( spectrum.lastCycle > 0 ) spectrum.lastCycle else ""
      val firstScan = if ( spectrum.firstScan > 0 ) spectrum.firstScan else ""
      val lastScan = if ( spectrum.lastScan > 0 ) spectrum.lastScan else ""
      val firstTime = if ( spectrum.firstTime > 0 ) spectrum.firstTime else ""
      val lastTime = if ( spectrum.lastTime > 0 ) spectrum.lastTime else ""
      //val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) generate(peptideMatch.properties.get) else ""

      // moz and intensity lists are formatted as numbers separated by spaces      
      val mozList = spectrum.mozList.getOrElse( Array.empty[Double] ).map { m => this.doubleFormatter.format( m ) } mkString ( " " )
      val intList = spectrum.intensityList.getOrElse( Array.empty[Float] ).map { i => this.floatFormatter.format( i ) } mkString ( " " )

      // Compress peaks
      val compressedMozList = EasyLzma.compress( mozList.getBytes )
      val compressedIntList = EasyLzma.compress( intList.getBytes )

      // Build a row containing spectrum values
      val spectrumValues = List( spectrum.id,
        escapeStringForPgCopy( spectrum.title ),
        spectrum.precursorMoz,
        precursorIntensity,
        spectrum.precursorCharge,
        spectrum.isSummed,
        firstCycle,
        lastCycle,
        firstScan,
        lastScan,
        firstTime,
        lastTime,
        """\\x""" + bytes2Hex( compressedMozList ),
        """\\x""" + bytes2Hex( compressedIntList ),
        spectrum.peaksCount,
        "",
        peaklistId,
        spectrum.instrumentConfigId )

      // Store spectrum
      val spectrumBytes = encodeRecordForPgCopy( spectrumValues, false )
      pgBulkLoader.writeToCopy( spectrumBytes, 0, spectrumBytes.length )

    }

    // End of BULK copy
    val nbInsertedSpectra = pgBulkLoader.endCopy()

    // Move TMP table content to MAIN table
    logger.info( "move TMP table " + tmpSpectrumTableName + " into MAIN spectrum table" )
    stmt.executeUpdate( "INSERT into spectrum (" + spectrumTableCols + ") " +
      "SELECT " + spectrumTableCols + " FROM " + tmpSpectrumTableName )

    // Retrieve generated spectrum ids
    val spectrumIdByTitle = context.msiDB.getOrCreateTransaction.select(
      "SELECT title, id FROM spectrum WHERE peaklist_id = " + peaklistId ) { r =>
        ( r.nextString.get -> r.nextInt.get )
      } toMap

    context.spectrumIdByTitle =  spectrumIdByTitle
    context

  }

  private val HEX_CHARS = "0123456789abcdef".toCharArray()

  def bytes2Hex( bytes: Array[Byte] ): String = {
    val chars = new Array[Char]( 2 * bytes.length )
    for ( i <- 0 until bytes.length ) {
      val b = bytes( i )
      chars( 2 * i ) = HEX_CHARS( ( b & 0xF0 ) >>> 4 )
      chars( 2 * i + 1 ) = HEX_CHARS( b & 0x0F )
    }
    new String( chars )
  }

}