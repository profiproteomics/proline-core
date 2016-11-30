package fr.proline.core.om.storer.msi.impl

import org.postgresql.core.Utils

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.IPeaklistContainer
import fr.proline.repository.util.PostgresUtils
import fr.profi.util.bytes._
import fr.profi.util.sql._
import fr.profi.util.primitives._

/**
 * @author David Bouyssie
 *
 */
object PgPeaklistWriter extends AbstractSQLPeaklistWriter with LazyLogging {
  
  private val spectrumTableColsWithoutPK = MsiDbSpectrumTable.columnsAsStrList.filter(_ != "id").mkString(",")
 
  override def insertSpectra(peaklistId: Long, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext = {

    DoJDBCWork.withConnection(context.getMSIDbConnectionContext) { con =>

      val bulkCopyManager = PostgresUtils.getCopyManager(con)
      val msiEzDBC = ProlineEzDBC(con, context.getMSIDbConnectionContext.getDriverType)

      /*
      // Create TMP table
      val tmpSpectrumTableName = "tmp_spectrum_" + (scala.math.random * 1000000).toInt
      logger.info(s"creating temporary table '$tmpSpectrumTableName'...")
      
      msiEzDBC.execute(
        s"CREATE TEMP TABLE $tmpSpectrumTableName (LIKE ${MsiDbSpectrumTable.name}) ON COMMIT DROP"
      )*/

      // Bulk insert of spectra
      logger.info("BULK insert of spectra")
      
      //val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $tmpSpectrumTableName ( $allSpectrumTableCols ) FROM STDIN")
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${MsiDbSpectrumTable.name} ($spectrumTableColsWithoutPK) FROM STDIN")
      
      var spectrumIdx = 0
      
      // Iterate over spectra to store them
      peaklistContainer.eachSpectrum { spectrum =>
        
        spectrumIdx += 1
        
        // Define some vars
        val precursorIntensity = if (!spectrum.precursorIntensity.isNaN) Some(spectrum.precursorIntensity) else None
        val firstCycle = if (spectrum.firstCycle > 0) Some(spectrum.firstCycle) else None
        val lastCycle = if (spectrum.lastCycle > 0) Some(spectrum.lastCycle) else None
        val firstScan = if (spectrum.firstScan > 0) Some(spectrum.firstScan) else None
        val lastScan = if (spectrum.lastScan > 0) Some(spectrum.lastScan) else None
        val firstTime = if (spectrum.firstTime > 0) Some(spectrum.firstTime) else None
        val lastTime = if (spectrum.lastTime > 0) Some(spectrum.lastTime) else None

        // moz and intensity lists are formatted as numbers separated by spaces      
        //val mozList = spectrum.mozList.getOrElse( Array.empty[Double] ).map { m => this.doubleFormatter.format( m ) } mkString ( " " )
        //val intList = spectrum.intensityList.getOrElse( Array.empty[Float] ).map { i => this.floatFormatter.format( i ) } mkString ( " " )

        // Compress peaks
        //val compressedMozList = EasyLzma.compress( mozList.getBytes )
        //val compressedIntList = EasyLzma.compress( intList.getBytes )
        
        // Build a row containing spectrum values
        val spectrumValues = List(
          //spectrum.id,
          spectrumIdx,
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
          """\\x""" + Utils.toHexString(doublesToBytes(spectrum.mozList.getOrElse(Array()))), // Snappy.compress(
          """\\x""" + Utils.toHexString(floatsToBytes(spectrum.intensityList.getOrElse(Array()))), // Snappy.compress(
          spectrum.peaksCount,
          spectrum.properties.map(ProfiJson.serialize(_)),
          peaklistId,
          spectrum.instrumentConfigId
        )
        
        // Store spectrum
        val spectrumBytes = encodeRecordForPgCopy(spectrumValues, false)
        pgBulkLoader.writeToCopy(spectrumBytes, 0, spectrumBytes.length)

      }

      // End of BULK copy
      val nbInsertedSpectra = pgBulkLoader.endCopy()

      /*// Move TMP table content to MAIN table
      logger.info(s"move TMP table $tmpSpectrumTableName into MAIN spectrum table")
      
      val spectrumTableColsWithoutPK = MsiDbSpectrumTable.columnsAsStrList.filter(_ != "id").mkString(",")      
      msiEzDBC.execute(
        s"INSERT into spectrum ($spectrumTableColsWithoutPK) " +
        s"SELECT $spectrumTableColsWithoutPK FROM $tmpSpectrumTableName"
      )*/

      // Retrieve generated spectrum ids
      val spectrumIdByTitle = msiEzDBC.select(
        "SELECT title, id FROM spectrum WHERE peaklist_id = " + peaklistId
      ) { r => ( r.nextString -> r.nextLong ) } toMap

      context.spectrumIdByTitle = spectrumIdByTitle

    }// End of jdbcWork

    context
  }

}