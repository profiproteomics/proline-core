package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms._
import fr.proline.core.om.model.lcms._
import fr.profi.util.sql._
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson

class SQLPeakelProvider(val lcmsDbCtx: DatabaseConnectionContext) {
  
  val PeakelCols = LcmsDbPeakelColumns
  val PeakelItemCols = LcmsDbFeaturePeakelItemColumns
  
  def getPeakels( peakelIds: Seq[Long] ): Array[Peakel] = {
    if( peakelIds.isEmpty ) return Array()

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      // Build peakels SQL query
      val peakelQuery = new SelectQueryBuilder1(LcmsDbPeakelTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ peakelIds.mkString(",") ~") "
      )
      
      // Iterate over peakels
      ezDBC.select( peakelQuery ) { r =>
        
        this.buildPeakel(r)
 

      } toArray

    })

  }
  
  def buildPeakel( peakelRecord: ResultSetRow ): Peakel = {
    
    val r = peakelRecord

   // Read and deserialize peaks
    val peaksAsBytes = r.getBytes(PeakelCols.PEAKS)
    val lcMsPeaks = org.msgpack.ScalaMessagePack.read[Array[LcMsPeak]](peaksAsBytes)
    
    // Read and deserialize properties
    val propsAsJSON = r.getStringOption(PeakelCols.SERIALIZED_PROPERTIES)
    val propsOpt = propsAsJSON.map( ProfiJson.deserialize[PeakelProperties](_) )
    
    Peakel(
      id = r.getLong(PeakelCols.ID),
      moz = r.getDouble(PeakelCols.MOZ),
      elutionTime = toFloat(r.getAny(PeakelCols.ELUTION_TIME)),
      apexIntensity = toFloat(r.getAny(PeakelCols.APEX_INTENSITY)),
      area = toFloat(r.getAny(PeakelCols.APEX_INTENSITY)),
      duration = toFloat(r.getAny(PeakelCols.DURATION)),
      fwhm = r.getAnyOption(PeakelCols.FWHM).map(toFloat(_)),
      isOverlapping = toBoolean(r.getAny(PeakelCols.IS_OVERLAPPING)),
      featuresCount = r.getInt(PeakelCols.FEATURE_COUNT),
      peaks = lcMsPeaks,
      firstScanId = r.getLong(PeakelCols.FIRST_SCAN_ID),
      lastScanId = r.getLong(PeakelCols.LAST_SCAN_ID),
      apexScanId = r.getLong(PeakelCols.APEX_SCAN_ID),
      rawMapId = r.getLong(PeakelCols.MAP_ID),
      properties = propsOpt
    )
    
  }
  
  def getFeaturePeakelItems( featureIds: Seq[Long] ): Array[FeaturePeakelItem] = {
    
    println("checking")

    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      // Build peakels SQL query
      val peakelItemQuery = new SelectQueryBuilder1(LcmsDbFeaturePeakelItemTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.FEATURE_ID ~" IN("~ featureIds.mkString(",") ~") "
      )
      
      val peakelIds = new ArrayBuffer[Long](featureIds.length)
      
      // Iterate over peakels
      val peakelItems = ezDBC.select( peakelItemQuery ) { r =>
        println("ok")
        
        val peakelItem = this.buildFeaturePeakelItem(r)
        peakelIds += peakelItem.peakelReference.id

        peakelItem
        
      } toArray
      
      // Load peakels and map them bey their id
      val peakelById = this.getPeakels(peakelIds).map( p => p.id -> p ).toMap
      
      // Attach peakels to peakel items
      for( peakelItem <- peakelItems ) {
        peakelItem.peakelReference = peakelById(peakelItem.peakelReference.id)
      }
      
      peakelItems
    })

  }
  
  def buildFeaturePeakelItem( itemRecord: ResultSetRow ): FeaturePeakelItem = {
    
    val r = itemRecord
    
    // Read and deserialize properties
    val propsAsJSON = r.getStringOption(PeakelItemCols.SERIALIZED_PROPERTIES)
    val propsOpt = propsAsJSON.map( ProfiJson.deserialize[FeaturePeakelItemProperties](_) )
    
    FeaturePeakelItem(
      peakelReference = PeakelIdentifier( r.getLong(PeakelItemCols.PEAKEL_ID) ),
      isotopeIndex = r.getInt(PeakelItemCols.ISOTOPE_INDEX),
      properties = propsOpt
    )    
  }

}
