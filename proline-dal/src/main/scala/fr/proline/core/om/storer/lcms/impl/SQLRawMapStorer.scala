package fr.proline.core.om.storer.lcms.impl

import scala.collection.mutable.ArrayBuffer

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.jdbc.easy._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.storer.lcms._

class SQLRawMapStorer(
  val lcmsDbCtx: LcMsDbConnectionContext,
  val featureWriter: IFeatureWriter,
  val peakelWriter: Option[IPeakelWriter] = None
) extends IRawMapStorer with LazyLogging {

  def storeRawMap(rawMap: RawMap, storeFeatures: Boolean = true, storePeakels: Boolean = false): Unit = {

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>

      // Create new map
      val newRawMapId = this.insertMap(ezDBC, rawMap, null)

      // Update run map id
      rawMap.id = newRawMapId

      // Store the related run map
      val peakPickingSoftwareId = rawMap.peakPickingSoftware.id
      val peakelFittingModelId = rawMap.peakelFittingModel.map(_.id)
      
      if (peakPickingSoftwareId < 1) {
        val newPpsId = this.getOrInsertPeakPickingSoftware(ezDBC, rawMap.peakPickingSoftware)
        rawMap.peakPickingSoftware.id = newPpsId
      }

      ezDBC.execute(
        LcmsDbRawMapTable.mkInsertQuery,
        newRawMapId,
        rawMap.runId,
        rawMap.peakPickingSoftware.id,
        peakelFittingModelId
      )

      // Insert peakels if requested
      if (storePeakels) {
        require( rawMap.peakels.isDefined, "the raw map must contain peakels" )
        peakelWriter.get.insertPeakels(rawMap.peakels.get, rawMap.id)
      }
      
      // Insert features if requested
      if (storeFeatures) {
        featureWriter.insertFeatures(rawMap.features, rawMap.id, linkToPeakels = storePeakels)
      }
    }

    ()
  }
  
  protected def insertMap(ezDBC: EasyDBC, lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date): Long = {

    // FIXME: scoring should not be null
    val ftScoringId = lcmsMap.featureScoring.map(_.id).getOrElse(1L)
    val lcmsMapType = if (lcmsMap.isProcessed) 1 else 0
    
    ezDBC.executePrepared(LcmsDbMapTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { statement =>
      val mapDesc = if (lcmsMap.description == null) None else Some(lcmsMap.description)

      statement.executeWith(
        lcmsMap.name,
        mapDesc,
        lcmsMapType,
        lcmsMap.creationTimestamp,
        modificationTimestamp,
        lcmsMap.properties.map( ProfiJson.serialize(_) ),
        ftScoringId
      )

      statement.generatedLong
    }

  }
  
  protected def getOrInsertPeakPickingSoftware(ezDBC: EasyDBC, pps: PeakPickingSoftware): Long = {
    
    val ppsIdQuery = new SelectQueryBuilder1(LcmsDbPeakPickingSoftwareTable).mkSelectQuery( (t,c) =>
      List(t.ID) -> "WHERE "~ t.NAME ~" = '"~ pps.name ~"' AND "~ t.VERSION ~" = '"~ pps.version ~"'"
    )
    
    val idOpt = ezDBC.selectHeadOption(ppsIdQuery) { _.nextLong }
    
    if (idOpt.isDefined) idOpt.get
    else {
      
      // *** BEGIN OF WORKAROUND FOR CORRUPTED PG SEQUENCE *** //
      // TODO: remove me when issues #16445 is fixed
      if (lcmsDbCtx.getDriverType == fr.proline.repository.DriverType.POSTGRESQL) {
        try {
          ezDBC.selectAndProcess("SELECT setval('peak_picking_software_id_seq', (SELECT MAX(id) FROM peak_picking_software))") { r =>
           logger.debug("New 'peak_picking_software_id_seq' value is: " + r.nextLong)
          }
        } catch {
          case t: Throwable => logger.error("Can't fix the sequence 'peak_picking_software_id_seq'", t)
        }
      }
      // *** END OF WORKAROUND FOR CORRUPTED PG SEQUENCE *** //
      
      val sqlQuery = LcmsDbPeakPickingSoftwareTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID))
      
      ezDBC.executePrepared(sqlQuery, true) { statement =>
  
        statement.executeWith(
          pps.name,
          pps.version,
          pps.algorithm,
          pps.properties.map( ProfiJson.serialize(_) )
        )
        
        statement.generatedLong
      }
    }
    
  }

}