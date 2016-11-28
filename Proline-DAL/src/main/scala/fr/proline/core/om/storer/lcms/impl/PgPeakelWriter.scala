package fr.proline.core.om.storer.lcms.impl

import com.typesafe.scalalogging.StrictLogging

import org.postgresql.copy.CopyIn
import org.postgresql.core.Utils

import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.MathUtils
import fr.profi.util.bytes._
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.sql._
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IPeakelWriter
import fr.proline.repository.util.PostgresUtils
//import rx.lang.scala.Observable

object PgConstants {
  val DOUBLE_PRECISION = 1e-11 // note: this is the precision we observe when using PgCopy (maybe toString is involved)
}

class PgPeakelWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IPeakelWriter with StrictLogging {
  
  private val allPeakelTableCols = LcmsDbPeakelTable.columnsAsStrList.mkString(",")
  private val peakelTableColsWithoutPK = LcmsDbPeakelTable.columnsAsStrList.filter(_ != "id").mkString(",")
  
  /*def insertPeakels2(peakels: Seq[Peakel], rawMapId: Long) {
    this.insertPeakelStream( peakels.foreach _, rawMapId )
  }
  
  def insertPeakelFlow(peakelFlow: Observable[Peakel], rawMapId: Long): Unit = {
    val peakelStream = peakelFlow.toBlocking.toIterable
    this.insertPeakelStream( peakelStream.foreach _, rawMapId )
  }
  
  def insertPeakelStream(peakelStream: (Peakel => Unit) => Unit, rawMapId: Long): Unit = {
    peakelStream { peakel =>
      
    }
  }*/
  
  def insertPeakels(peakels: Seq[Peakel], rawMapId: Long): Unit = {
    
    DoJDBCWork.withConnection(lcmsDbCtx) { con =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(con)

      // Create TMP table
      val tmpPeakelTableName = "tmp_peakel_" + (scala.math.random * 1000000).toInt
      logger.info(s"creating temporary table '$tmpPeakelTableName'...")
      
      val lcmsEzDBC = ProlineEzDBC(con, lcmsDbCtx.getDriverType)
      lcmsEzDBC.execute(
        s"CREATE TEMP TABLE $tmpPeakelTableName (LIKE ${LcmsDbPeakelTable.name}) ON COMMIT DROP"
      )

      // Bulk insert of features
      logger.info("BULK insert of peakels")
      
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $tmpPeakelTableName ( $allPeakelTableCols ) FROM STDIN")
      
      // Iterate the peakels to store them
      for( peakel <- peakels ) {
        
        // Update peakel raw map id
        peakel.rawMapId = rawMapId
        
        this.insertPeakelUsingCopyManager(peakel, pgBulkLoader)
      }
      
      // End of BULK copy
      val nbInsertedPeakels = pgBulkLoader.endCopy()
      
      logger.info(s"BULK insert of $nbInsertedPeakels peakels completed !")

      // Move TMP table content to MAIN table
      logger.info(s"move TMP table $tmpPeakelTableName into MAIN ${LcmsDbPeakelTable.name} table")
      
      lcmsEzDBC.execute(
        s"INSERT INTO ${LcmsDbPeakelTable.name} ($peakelTableColsWithoutPK) " +
        s"SELECT $peakelTableColsWithoutPK FROM $tmpPeakelTableName"
      )

      // Retrieve generated peakel ids
      logger.info(s"Retrieving generated peakel ids...")
      
      val idMzPairs = lcmsEzDBC.select(
        s"SELECT id, moz FROM ${LcmsDbPeakelTable.name} WHERE map_id = $rawMapId"
      ) { r => Tuple2( r.nextLong, r.nextDouble ) }
      
      val peakelsCount = peakels.length
      assert(
        idMzPairs.length == peakelsCount,
        s"invalid number of retrieved peakel ids: got ${idMzPairs.length} but expected $peakelsCount"
      )
      
      val sortedIdMzPairs = idMzPairs.sortBy(_._1)
      
      // Update peakel ids
      var peakelIdx = 0
      while( peakelIdx < peakelsCount ) {
        val peakel = peakels(peakelIdx)
        val idMzPair = sortedIdMzPairs(peakelIdx)
        // Check we retrieve records in the same order
        assert(
          MathUtils.nearlyEquals(peakel.moz, idMzPair._2, PgConstants.DOUBLE_PRECISION),
          s"error while trying to update peakel id, m/z values are different: was ${peakel.moz} and is now ${idMzPair._2}"
        )
        
        peakel.id = idMzPair._1
        
        peakelIdx += 1
      }
    
    }
    
    ()
  }

  @inline
  protected def insertPeakelUsingCopyManager(peakel: Peakel, pgBulkLoader: CopyIn): Unit = {
    
    // Serialize the peakel data matrix
    val peakelAsBytes = PeakelDataMatrix.pack(peakel.dataMatrix)
    
    val peakelValues = List(
      peakel.id,
      peakel.moz,
      peakel.elutionTime,
      peakel.apexIntensity,
      peakel.area,
      peakel.duration,
      0f, // peakel.fwhm,
      peakel.isOverlapping,
      peakel.featuresCount,
      peakel.dataMatrix.peaksCount,
      // TODO: handle this conversion in encodeRecordForPgCopy
      """\\x""" + Utils.toHexString(peakelAsBytes),
      peakel.properties.map( ProfiJson.serialize(_) ),
      peakel.firstScanId,
      peakel.lastScanId,
      peakel.apexScanId,
      peakel.rawMapId
    )
    
    // Store the peakel
    val peakelBytes = encodeRecordForPgCopy(peakelValues, false)
    pgBulkLoader.writeToCopy(peakelBytes, 0, peakelBytes.length)
  }
  
}
