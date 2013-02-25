package fr.proline.core.om.storer.msi.impl

import java.sql.Connection
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import org.xerial.snappy.Snappy
import com.codahale.jerkson.Json.generate
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.MsiDbPeaklistTable
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.IPeaklistContainer
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.repository.util.JDBCWork
import fr.proline.util.bytes._
import fr.proline.util.sql._
import fr.proline.repository.util.PostgresUtils

class SQLPeaklistWriter extends IPeaklistWriter with Logging {

  val compressionAlgo = "none" //none | lzma | xz | snappy => TODO: create an enumeration near to the Peaklist class

  //protected val doubleFormatter = newDecimalFormat("#.######")
  //protected val floatFormatter = newDecimalFormat("#.##")

  //TODO: GET/CREATE Peaklist SOFT
  def storePeaklist(peaklist: Peaklist, context: StorerContext): Int = {

    require(peaklist != null, "peaklist is null")

    DoJDBCWork.withEzDBC( context.getMSIDbConnectionContext, { msiEzDBC =>

      val peaklistInsertQuery = MsiDbPeaklistTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared(peaklistInsertQuery, true) { stmt =>
        stmt.executeWith(
          peaklist.fileType,
          peaklist.path,
          peaklist.rawFileName,
          peaklist.msLevel,
          compressionAlgo,
          peaklist.properties.map(generate(_)),
          Option(peaklist.peaklistSoftware).map(_.id)
        )

        peaklist.id = stmt.generatedInt
      }

    },true)

    peaklist.id
  }

  def storeSpectra(peaklistId: Int, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext = {
    
    logger.info("storing spectra...")

    DoJDBCWork.withEzDBC( context.getMSIDbConnectionContext, { msiEzDBC =>
      
      val spectrumInsertQuery = MsiDbSpectrumTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }

      // Insert corresponding spectra
      val spectrumIdByTitle = collection.immutable.Map.newBuilder[String, Int]
      
      msiEzDBC.executePrepared(spectrumInsertQuery) { stmt =>
        peaklistContainer.eachSpectrum { spectrum =>
          this._insertSpectrum(stmt, spectrum, peaklistId)
          spectrumIdByTitle += (spectrum.title -> spectrum.id)
        }
      }

      // TODO: use id cache
      context.spectrumIdByTitle = spectrumIdByTitle.result()
      
    },true)

    context
  }

  private def _insertSpectrum(stmt: PreparedStatementWrapper, spectrum: Spectrum, peaklistId: Int): Unit = {

    // Define some vars
    val precursorIntensity = if (!spectrum.precursorIntensity.isNaN) Some(spectrum.precursorIntensity) else Option.empty[Float]
    val firstCycle = if (spectrum.firstCycle > 0) Some(spectrum.firstCycle) else Option.empty[Int]
    val lastCycle = if (spectrum.lastCycle > 0) Some(spectrum.lastCycle) else Option.empty[Int]
    val firstScan = if (spectrum.firstScan > 0) Some(spectrum.firstScan) else Option.empty[Int]
    val lastScan = if (spectrum.lastScan > 0) Some(spectrum.lastScan) else Option.empty[Int]
    val firstTime = if (spectrum.firstTime > 0) Some(spectrum.firstTime) else Option.empty[Float]
    val lastTime = if (spectrum.lastTime > 0) Some(spectrum.lastTime) else Option.empty[Float]

    // moz and intensity lists are formatted as numbers separated by spaces
    //val mozList = spectrum.mozList.getOrElse(Array.empty[Double]).map { m => this.doubleFormatter.format( m ) } mkString(" ")
    //val intList = spectrum.intensityList.getOrElse(Array.empty[Float]).map { i => this.floatFormatter.format( i ) } mkString(" ")

    // Compress peaks
    //val compressedMozList = EasyLzma.compress( mozList.getBytes )
    //val compressedIntList = EasyLzma.compress( intList.getBytes )

    stmt.executeWith(
      spectrum.title,
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
      doublesToBytes(spectrum.mozList.get), // Snappy.compress(
      floatsToBytes(spectrum.intensityList.get), // Snappy.compress(
      spectrum.peaksCount,
      spectrum.properties.map(generate(_)),
      peaklistId,
      spectrum.instrumentConfigId
    )

    spectrum.id = stmt.generatedInt

  }

  // TODO: is this really useful ???
  def rollBackInfo(peaklistId: Int, context: StorerContext) = {

    require (peaklistId > 0, "peaklist must first be persisted in the MSIdb")
    
    val jdbcWork = new JDBCWork() {

      override def execute(con: Connection) {
        val stmt = con.createStatement()

        // Retrieve generated protein match ids
        //val specIds = context.msiDB.selectInts("SELECT id FROM spectrum WHERE spectrum.peaklist_id = "+peaklistId)
        //stmt.executeUpdate("DELETE FROM spectrum WHERE spectrum.id IN ("+specIds.mkString(",")+")" )

        stmt.executeUpdate("DELETE FROM spectrum WHERE peaklist_id = " + peaklistId)
        stmt.executeUpdate("DELETE FROM peaklist WHERE peaklist.id = " + peaklistId)

        stmt.close()
      }

    } // End of jdbcWork anonymous inner class

    context.getMSIDbConnectionContext.doWork(jdbcWork, true)
  }

}

class PgSQLSpectraWriter extends SQLPeaklistWriter with Logging {
  
  import org.postgresql.core.Utils

  override def storeSpectra(peaklistId: Int, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext = {

    DoJDBCWork.withConnection(context.getMSIDbConnectionContext, { con =>

      val bulkCopyManager = PostgresUtils.getCopyManager(con)

      // Create TMP table
      val tmpSpectrumTableName = "tmp_spectrum_" + (scala.math.random * 1000000).toInt
      logger.info("creating temporary table '" + tmpSpectrumTableName + "'...")

      val msiEzDBC = ProlineEzDBC(con, context.getMSIDbConnectionContext.getDriverType)

      msiEzDBC.execute("CREATE TEMP TABLE " + tmpSpectrumTableName + " (LIKE spectrum)")

      // Bulk insert of spectra
      logger.info("BULK insert of spectra")

      val spectrumTableCols = MsiDbSpectrumTable.columnsAsStrList.filter(_ != "id").mkString(",")
      val pgBulkLoader = bulkCopyManager.copyIn("COPY " + tmpSpectrumTableName + " ( id, " + spectrumTableCols + " ) FROM STDIN")
      
      // Iterate over spectra to store them
      peaklistContainer.eachSpectrum { spectrum =>
        
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
          spectrum.id,
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
          """\\x""" + Utils.toHexString(doublesToBytes(spectrum.mozList.get)), // Snappy.compress(
          """\\x""" + Utils.toHexString(floatsToBytes(spectrum.intensityList.get)), // Snappy.compress(
          spectrum.peaksCount,
          spectrum.properties.map(generate(_)),
          peaklistId,
          spectrum.instrumentConfigId
        )
        
        // Store spectrum
        val spectrumBytes = encodeRecordForPgCopy(spectrumValues, false)
        pgBulkLoader.writeToCopy(spectrumBytes, 0, spectrumBytes.length)

      }

      // End of BULK copy
      val nbInsertedSpectra = pgBulkLoader.endCopy()

      // Move TMP table content to MAIN table
      logger.info("move TMP table " + tmpSpectrumTableName + " into MAIN spectrum table")
      msiEzDBC.execute("INSERT into spectrum (" + spectrumTableCols + ") " +
        "SELECT " + spectrumTableCols + " FROM " + tmpSpectrumTableName)

      // Retrieve generated spectrum ids
      val spectrumIdByTitle = msiEzDBC.select(
        "SELECT title, id FROM spectrum WHERE peaklist_id = " + peaklistId) { r =>
          (r.nextString -> r.nextInt)
        } toMap

      context.spectrumIdByTitle = spectrumIdByTitle

    }, true) // End of jdbcWork

    context
  }

}