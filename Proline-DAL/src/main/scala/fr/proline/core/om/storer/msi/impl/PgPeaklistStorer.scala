package fr.proline.core.om.storer.msi.impl

import com.weiglewilczek.slf4s.Logging
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbPeaklistTable,MsiDbSpectrumTable}
import fr.proline.core.om.model.msi.{IPeaklistContainer,Peaklist,Spectrum}
import fr.proline.core.utils.sql._
import fr.proline.core.utils.lzma.EasyLzma

private[msi] class PgPeaklistStorer( val msiDb: MsiDb ) extends SQLitePeaklistStorer( msiDb ) with Logging {
  
  val bulkCopyManager = new CopyManager( msiDb.getOrCreateConnection().asInstanceOf[BaseConnection] )
  
  override def storeSpectra( peaklist: Peaklist, peaklistContainer: IPeaklistContainer ): Map[String,Int] = {
    
    val peaklistId = peaklist.id
    val tzs = TrailingZerosStripper
    
    // Create TMP table
    val tmpSpectrumTableName = "tmp_spectrum_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpSpectrumTableName +"'..." )
    
    val stmt = this.msiDb.connection.createStatement()
    stmt.executeUpdate("CREATE TEMP TABLE "+tmpSpectrumTableName+" (LIKE spectrum)")    
    
    // Bulk insert of spectra
    logger.info( "BULK insert of spectra" )
    
    val spectrumTableCols = MsiDbSpectrumTable.getColumnsAsStrList().filter( _ != "id" ).mkString(",")
    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY "+ tmpSpectrumTableName +" ( id, "+ spectrumTableCols + " ) FROM STDIN" )
    
    // Iterate over spectra to store them
    peaklistContainer.eachSpectrum { spectrum =>
      
      // Define some vars
      val precursorIntensity = if( !spectrum.precursorIntensity.isNaN ) spectrum.precursorIntensity else ""
      val firstCycle = if( spectrum.firstCycle > 0 ) spectrum.firstCycle else ""
      val lastCycle = if( spectrum.lastCycle > 0 ) spectrum.lastCycle else ""
      val firstScan = if( spectrum.firstScan > 0 ) spectrum.firstScan else ""
      val lastScan = if( spectrum.lastScan > 0 ) spectrum.lastScan else ""
      val firstTime = if( spectrum.firstTime > 0 ) spectrum.firstTime else ""
      val lastTime = if( spectrum.lastTime > 0 ) spectrum.lastTime else ""
      //val pepMatchPropsAsJSON = if( peptideMatch.properties != None ) generate(peptideMatch.properties.get) else ""
      
      // moz and intensity lists are formatted as numbers separated by spaces      
      val mozList = spectrum.mozList.getOrElse(Array.empty[Double]).map { m => tzs(this.doubleFormatter.format( m )) } mkString(" ")
      val intList = spectrum.intensityList.getOrElse(Array.empty[Float]).map { i => tzs(this.floatFormatter.format( i )) } mkString(" ")
      
      // Compress peaks
      val compressedMozList = EasyLzma.compress( mozList.getBytes )
      val compressedIntList = EasyLzma.compress( intList.getBytes )      
      
      // Build a row containing spectrum values
      val spectrumValues = List(  spectrum.id,
                                  escapeStringForPgCopy(spectrum.title),
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
                                  """\\x""" + bytes2Hex(compressedMozList),
                                  """\\x""" + bytes2Hex(compressedIntList),
                                  spectrum.peaksCount,
                                  "",
                                  peaklistId,
                                  spectrum.instrumentConfigId
                                )
      
      // Store spectrum
      val spectrumBytes = encodeRecordForPgCopy( spectrumValues, false )
      pgBulkLoader.writeToCopy( spectrumBytes, 0, spectrumBytes.length )  
      
    }
    
    // End of BULK copy
    val nbInsertedSpectra = pgBulkLoader.endCopy()
    
    // Move TMP table content to MAIN table
    logger.info( "move TMP table "+ tmpSpectrumTableName +" into MAIN spectrum table" )
    stmt.executeUpdate("INSERT into spectrum ("+spectrumTableCols+") "+
                       "SELECT "+spectrumTableCols+" FROM "+tmpSpectrumTableName )
    
    // Retrieve generated spectrum ids
    val spectrumIdByTitle = this.msiDb.getOrCreateTransaction.select(
                           "SELECT title, id FROM spectrum WHERE peaklist_id = " + peaklistId ) { r => 
                             (r.nextString.get -> r.nextInt.get)
                           } toMap
    
    spectrumIdByTitle
  }
  
  private val HEX_CHARS = "0123456789abcdef".toCharArray()

  def bytes2Hex(bytes: Array[Byte]): String = {
    val chars = new Array[Char](2 * bytes.length)
    for( i <- 0 until bytes.length ) {
      val b = bytes(i)
      chars(2 * i) = HEX_CHARS( ( b & 0xF0 ) >>> 4 )
      chars(2 * i + 1) = HEX_CHARS( b & 0x0F )
    }
    new String(chars)
  }
    
}


