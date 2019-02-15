package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.StrictLogging

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IPeakelWriter

class SQLPeakelWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IPeakelWriter with StrictLogging {

  // TODO: copy/pasted from PgPeakelWriter => put in abstract class
  def insertPeakels(peakels: Seq[Peakel], rawMapId: Long) {
    val newIdByTmpId = this.insertPeakelStream( peakels.foreach _, Some(rawMapId), peakels.length).head._2
    
    // Update peakel ids
    for (peakel <- peakels) {
      peakel.id = newIdByTmpId(peakel.id)
    }
  }
  
  // This method is able to stream the insertion of peakels coming from different raw maps
  def insertPeakelStream(forEachPeakel: (Peakel => Unit) => Unit, rawMapId: Option[Long], sizeHint: Int = 10): LongMap[LongMap[Long]] = {
    
    val oldAndNewIds = new ArrayBuffer[Array[Long]](sizeHint)
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      // Insert peakels
      ezDBC.executePrepared(LcmsDbPeakelTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { peakelInsertStmt =>
        
        forEachPeakel { peakel =>
          
          val oldPeakelId = peakel.id
          val oldRawMapId = peakel.rawMapId
          
          // Update peakel raw map id
          if (rawMapId.isDefined) {
            peakel.rawMapId = rawMapId.get
          }
          
          // Serialize the peakel data matrix
          val peakelAsBytes = PeakelDataMatrix.pack(peakel.dataMatrix)
          
          peakelInsertStmt.executeWith(
            peakel.moz,
            peakel.elutionTime,
            peakel.apexIntensity,
            peakel.area,
            peakel.duration,
            0f, // peakel.fwhm,
            peakel.isOverlapping,
            peakel.featuresCount,
            peakel.dataMatrix.peaksCount,
            peakelAsBytes,
            peakel.properties.map( ProfiJson.serialize(_) ),
            peakel.firstScanId,
            peakel.lastScanId,
            peakel.apexScanId,
            peakel.rawMapId
          )
          
          // Update peakel id
          val newPeakelId = peakelInsertStmt.generatedLong
          peakel.id = newPeakelId
          
          oldAndNewIds += Array(peakel.rawMapId,oldPeakelId,newPeakelId)
        }
        
      }
      
    } // ends DoJDBCWork
    
    
    val peakelIdByTmpIdByRawMapId = new LongMap[LongMap[Long]]()
    
    for (peakelOldAndNewIds <- oldAndNewIds) {
      val oldRawMapId = peakelOldAndNewIds(0)
      val oldPeakelId = peakelOldAndNewIds(1)
      val newPeakelId = peakelOldAndNewIds(2)
      
      // TODO: optimize the sizeHint by computing it for each different raw map, otherwise it could be over estimated
      val peakelIdByTmpId = peakelIdByTmpIdByRawMapId.getOrElseUpdate(oldRawMapId, new LongMap[Long](sizeHint))
      peakelIdByTmpId.put(oldPeakelId, newPeakelId)
    }
    
    peakelIdByTmpIdByRawMapId
  }

}