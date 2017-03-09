package fr.proline.core.om.storer.msi.impl

import java.sql.Connection

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.msi.MsiDbPeaklistTable
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.dal.tables.uds.UdsDbRawFileTable
import fr.proline.core.om.model.msi.IPeaklistContainer
import fr.proline.core.om.model.msi.Peaklist
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.repository.util.JDBCWork
import fr.profi.util.bytes._

object SQLPeaklistWriter extends AbstractSQLPeaklistWriter

abstract class AbstractSQLPeaklistWriter extends IPeaklistWriter with LazyLogging {

  private val compressionAlgo = "none" //none | lzma | xz | snappy => TODO: create an enumeration near to the Peaklist class
  private val rawFileIdCol = UdsDbRawFileTable.columns.IDENTIFIER

  //protected val doubleFormatter = newDecimalFormat("#.######")
  //protected val floatFormatter = newDecimalFormat("#.##")
  
  def insertPeaklist(peaklist: Peaklist, context: StorerContext): Long = {
    require(peaklist != null, "peaklist is null")

    // Check if raw file is registered in the UDSdb
    val rawFileIdentifier = peaklist.rawFileIdentifier
    val isRawFileRegistered = DoJDBCReturningWork.withEzDBC(context.getUDSDbConnectionContext) { udsEzDBC =>
      val sqlQuery = s"SELECT count($rawFileIdCol) FROM ${UdsDbRawFileTable.name} WHERE $rawFileIdCol = '${rawFileIdentifier}'"
      val rawFileCount = udsEzDBC.selectInt(sqlQuery)
      rawFileCount == 1
    }
    val rawFileIdentOpt = if (isRawFileRegistered) Some(rawFileIdentifier) else None
    this.logger.info(
      s"Peaklist raw file $isRawFileRegistered ${if (isRawFileRegistered) "is" else "is not"} registered in the UDSdb !"
    )

    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>

      val peaklistInsertQuery = MsiDbPeaklistTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }
      
      msiEzDBC.executePrepared(peaklistInsertQuery, true) { stmt =>
        stmt.executeWith(
          peaklist.fileType,
          peaklist.path,
          rawFileIdentOpt,
          peaklist.msLevel,
          compressionAlgo,
          peaklist.properties.map(ProfiJson.serialize(_)),
          Option(peaklist.peaklistSoftware).map(_.id)
        )

        peaklist.id = stmt.generatedLong
      }

    }

    peaklist.id
  }

  def insertSpectra(peaklistId: Long, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext = {
    
    logger.info("storing spectra...")

    DoJDBCWork.withEzDBC( context.getMSIDbConnectionContext) { msiEzDBC =>
      
      val spectrumInsertQuery = MsiDbSpectrumTable.mkInsertQuery{ (c,colsList) => 
        colsList.filter( _ != c.ID)
      }

      // Insert corresponding spectra
      val spectrumIdByTitle = collection.immutable.Map.newBuilder[String, Long]
      
      msiEzDBC.executePrepared(spectrumInsertQuery, true) { stmt =>
        
        var spectrumIdx = 0
        peaklistContainer.eachSpectrum { spectrum =>
          spectrumIdx += 1
          
          this._insertSpectrum(stmt, spectrum, peaklistId, spectrumIdx)
          spectrumIdByTitle += (spectrum.title -> spectrum.id)
        }
        
      }

      // TODO: use id cache
      context.spectrumIdByTitle = spectrumIdByTitle.result()
      
    }

    context
  }

  private def _insertSpectrum(stmt: PreparedStatementWrapper, spectrum: Spectrum, peaklistId: Long, spectrumIdx: Int): Unit = {

    // Define some vars
    val precursorIntensity = if (!spectrum.precursorIntensity.isNaN) Some(spectrum.precursorIntensity) else Option.empty[Float]
    val firstCycle = if (spectrum.firstCycle > 0) Some(spectrum.firstCycle) else Option.empty[Int]
    val lastCycle = if (spectrum.lastCycle > 0) Some(spectrum.lastCycle) else Option.empty[Int]
    val firstScan = if (spectrum.firstScan > 0) Some(spectrum.firstScan) else Option.empty[Int]
    val lastScan = if (spectrum.lastScan > 0) Some(spectrum.lastScan) else Option.empty[Int]
    val firstTime = if (spectrum.firstTime >= 0) Some(spectrum.firstTime) else Option.empty[Float]
    val lastTime = if (spectrum.lastTime >= 0) Some(spectrum.lastTime) else Option.empty[Float]

    // moz and intensity lists are formatted as numbers separated by spaces
    //val mozList = spectrum.mozList.getOrElse(Array.empty[Double]).map { m => this.doubleFormatter.format( m ) } mkString(" ")
    //val intList = spectrum.intensityList.getOrElse(Array.empty[Float]).map { i => this.floatFormatter.format( i ) } mkString(" ")

    // Compress peaks
    //val compressedMozList = EasyLzma.compress( mozList.getBytes )
    //val compressedIntList = EasyLzma.compress( intList.getBytes )

    stmt.executeWith(
      spectrumIdx,
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
      doublesToBytes(spectrum.mozList.getOrElse(Array())), // Snappy.compress(
      floatsToBytes(spectrum.intensityList.getOrElse(Array())), // Snappy.compress(
      spectrum.peaksCount,
      spectrum.properties.map(ProfiJson.serialize(_)),
      peaklistId,
      spectrum.instrumentConfigId
    )

    spectrum.id = stmt.generatedLong

  }

}

