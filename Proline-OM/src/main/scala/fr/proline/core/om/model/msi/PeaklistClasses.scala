package fr.proline.core.om.model.msi

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.core.utils.misc.InMemoryIdGen

object Spectrum extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Spectrum ( var id: Int,
                      val title: String,
                      val precursorMoz: Double,
                      val precursorIntensity: Float = Float.NaN,
                      val precursorCharge: Int,
                      val isSummed: Boolean = false,
                      val firstCycle: Int = 0,
                      val lastCycle: Int = 0,
                      val firstScan: Int = 0,
                      val lastScan: Int = 0,
                      val firstTime: Float = 0,
                      val lastTime: Float = 0,
                      var mozList: Option[Array[Double]],
                      var intensityList: Option[Array[Float]],
                      val peaksCount: Int,
                      val instrumentConfigId: Int,
                      val peaklistId: Int
                      )

object PeaklistSoftware extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeaklistSoftware( var id: Int,
                             val name: String,
                             val version: String
                           )

object Peaklist extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Peaklist( var id: Int,
                     val fileType: String,
                     val path: String,
                     val rawFileName: String,
                     val msLevel: Int,
                     var peaklistSoftware: PeaklistSoftware = null
                     )
