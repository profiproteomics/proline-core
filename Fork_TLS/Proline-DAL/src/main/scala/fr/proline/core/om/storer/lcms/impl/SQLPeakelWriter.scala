package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms.IPeakelWriter

class SQLPeakelWriter(lcmsDbCtx: LcMsDbConnectionContext) extends IPeakelWriter {

  def insertPeakels(peakels: Seq[Peakel], rawMapId: Long): Unit = {
    
    val newRawMapId = rawMapId
    
    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>
    
      // Insert peakels
      ezDBC.executePrepared(LcmsDbPeakelTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { peakelInsertStmt =>
        
        for( peakel <- peakels ) {
          
          // Update peakel raw map id
          peakel.rawMapId = newRawMapId
          
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
          peakel.id = peakelInsertStmt.generatedLong
        }
        
      }
      
    } // ends DoJDBCWork
    
    ()
  }

}