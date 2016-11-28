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
import fr.proline.core.om.storer.lcms._

class SQLRawMapStorer(
  val lcmsDbCtx: LcMsDbConnectionContext,
  val featureWriter: IFeatureWriter,
  val peakelWriter: Option[IPeakelWriter] = None
) extends IRawMapStorer {

  def storeRawMap(rawMap: RawMap, storePeakels: Boolean = false): Unit = {

    DoJDBCWork.withEzDBC(lcmsDbCtx) { ezDBC =>

      // Create new map
      val newRawMapId = this.insertMap(ezDBC, rawMap, null)

      // Update run map id
      rawMap.id = newRawMapId

      // Store the related run map
      val peakPickingSoftwareId = rawMap.peakPickingSoftware.id
      val peakelFittingModelId = rawMap.peakelFittingModel.map(_.id)

      ezDBC.execute(
        LcmsDbRawMapTable.mkInsertQuery,
        newRawMapId,
        rawMap.runId,
        peakPickingSoftwareId,
        peakelFittingModelId
      )

      // Insert features
      val flattenedFeatures = featureWriter.insertFeatures(rawMap.features, rawMap.id)

      // Store peakels if requested
      if (storePeakels) {
        require( rawMap.peakels.isDefined, "the raw map must contain peakels" )
        peakelWriter.get.insertPeakels(rawMap.peakels.get, rawMap.id)
        featureWriter.linkFeaturesToPeakels(flattenedFeatures, rawMap.id)
      }

    }

    ()
  }

  protected def insertMap(ezDBC: EasyDBC, lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date): Long = {

    // FIXME: scoring should not be null
    val ftScoringId = lcmsMap.featureScoring.map(_.id).getOrElse(1L)
    val lcmsMapType = if (lcmsMap.isProcessed) 1 else 0
    
    var newMapId = 0L
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

      newMapId = statement.generatedLong
    }

    newMapId

  }

}