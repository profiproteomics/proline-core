package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

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
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IPeakelWriter
import fr.proline.repository.util.PostgresUtils

object PgConstants {
  val DOUBLE_PRECISION = 1e-11 // note: this is the precision we observe when using PgCopy (maybe toString is involved)
}

class PgPeakelWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IPeakelWriter with StrictLogging {
  
  private val allPeakelTableCols = LcmsDbPeakelTable.columnsAsStrList.mkString(",")
  private val peakelTableColsWithoutPK = LcmsDbPeakelTable.columnsAsStrList.filter(_ != "id").mkString(",")
  
  def insertPeakels(peakels: Seq[Peakel], rawMapId: Long) {
    val newIdByTmpId = this.insertPeakelStream( peakels.foreach _, Some(rawMapId), peakels.length).head._2
    
    // Update peakel ids
    for (peakel <- peakels) {
      peakel.id = newIdByTmpId(peakel.id)
    }
  }
  
  // This method is able to stream the insertion of peakels coming from different raw maps
  def insertPeakelStream(forEachPeakel: (Peakel => Unit) => Unit, rawMapId: Option[Long], sizeHint: Int = 10): LongMap[LongMap[Long]] = {
    
    DoJDBCReturningWork.withConnection(lcmsDbCtx) { con =>
      
      val bulkCopyManager = PostgresUtils.getCopyManager(con)
      val lcmsEzDBC = ProlineEzDBC(con, lcmsDbCtx.getDriverType)

      // Create TMP table
      /*val tmpPeakelTableName = "tmp_peakel_" + (scala.math.random * 1000000).toInt
      logger.info(s"creating temporary table '$tmpPeakelTableName'...")
      
      lcmsEzDBC.execute(
        s"CREATE TEMP TABLE $tmpPeakelTableName (LIKE ${LcmsDbPeakelTable.name}) ON COMMIT DROP"
      )*/

      // Bulk insert of features
      logger.info("BULK insert of peakels")
      
      //val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $tmpPeakelTableName ( $allPeakelTableCols ) FROM STDIN")
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY ${LcmsDbPeakelTable.name} ($peakelTableColsWithoutPK) FROM STDIN")
      
      // Iterate the peakels to store them
      val peakelMozList = new ArrayBuffer[Double](sizeHint)
      val tmpPeakelIds = new ArrayBuffer[Long](sizeHint)
      val oldRawMapIdByNewRawMapId = new LongMap[Long]()
      val peakelsCountByRawMapId = new LongMap[Int]()
      
      var peakelsCount = 0
      
      try {
        forEachPeakel { peakel =>
          peakelsCount += 1
          
          val oldRawMapId = peakel.rawMapId
          
          // Update peakel raw map id
          if (rawMapId.isDefined) {
            peakel.rawMapId = rawMapId.get
          }
          
          val newRawMapId = peakel.rawMapId
          require( newRawMapId > 0, "peakel.rawMapId must be greater than zero")
          oldRawMapIdByNewRawMapId.put(oldRawMapId, newRawMapId)
          
          if (peakelsCountByRawMapId.contains(newRawMapId) == false) {
            peakelsCountByRawMapId.put(newRawMapId, 0)
          }
          peakelsCountByRawMapId(newRawMapId) += 1
          
          peakelMozList += peakel.moz
          tmpPeakelIds += peakel.id
          
          this.insertPeakelUsingCopyManager(peakel, pgBulkLoader)
        }
      } catch {
        case t: Throwable => {
          pgBulkLoader.cancelCopy()
          logger.error("Error during peakel iteration, Postgres BULK INSERT has been cancelled!")
          throw t
        }
      }
      
      // End of BULK copy
      val nbInsertedPeakels = pgBulkLoader.endCopy()
      
      logger.info(s"BULK insert of $nbInsertedPeakels peakels completed !")

      // Move TMP table content to MAIN table
      /*logger.info(s"move TMP table $tmpPeakelTableName into MAIN ${LcmsDbPeakelTable.name} table")
      
      lcmsEzDBC.execute(
        s"INSERT INTO ${LcmsDbPeakelTable.name} ($peakelTableColsWithoutPK) " +
        s"SELECT $peakelTableColsWithoutPK FROM $tmpPeakelTableName"
      )*/

      // Retrieve generated peakel ids
      logger.info(s"Retrieving generated peakel ids...")
      
      val peakelTuples = new Array[(Long,Double, Long)](peakelsCount)
      val rawMapIds = oldRawMapIdByNewRawMapId.keys
      val whereClause = rawMapIds.map(id => s"map_id=$id").mkString(" OR ")
      
      var idMzPairIdx = 0
      lcmsEzDBC.selectAndProcess(
        s"SELECT id, moz, map_id FROM ${LcmsDbPeakelTable.name} WHERE $whereClause"
      ) { r =>
        peakelTuples(idMzPairIdx) = (r.nextLong, r.nextDouble, r.nextLong)
        idMzPairIdx += 1
      }
      
      assert(
        idMzPairIdx == peakelsCount,
        s"invalid number of retrieved peakel ids: got $idMzPairIdx but expected $peakelsCount"
      )
      
      // Sort peakelTuples to be sure to iterate peakels in insertion order
      val sortedPeakelTuples = peakelTuples.sortBy(_._1)
      
      // Map new peakel ids by old one
      val peakelIdByTmpIdByRawMapId = for ((rawMapId,peakelsCount) <- peakelsCountByRawMapId) yield {
        (rawMapId, new LongMap[Long](peakelsCount) )
      }
      
      var peakelIdx = 0
      while( peakelIdx < peakelsCount ) {
        val peakelMoz = peakelMozList(peakelIdx)
        val (newPeakelId,loadedMoz,rawMapId) = sortedPeakelTuples(peakelIdx)
        
        // Check we retrieved records in the same order
        assert(
          MathUtils.nearlyEquals(peakelMoz, loadedMoz, PgConstants.DOUBLE_PRECISION),
          s"error while trying to update peakel id, m/z values are different: was $peakelMoz and is now $loadedMoz"
        )
        
        val tmpPeakelId = tmpPeakelIds(peakelIdx)
        peakelIdByTmpIdByRawMapId(rawMapId).put(tmpPeakelId,newPeakelId)
        
        peakelIdx += 1
      }
    
      peakelIdByTmpIdByRawMapId
    }
  }
  
  @inline
  protected def insertPeakelUsingCopyManager(peakel: Peakel, pgBulkLoader: CopyIn): Unit = {
    
    // Serialize the peakel data matrix
    val peakelAsBytes = PeakelDataMatrix.pack(peakel.dataMatrix)
    
    val peakelValues = List(
      //peakel.id,
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
