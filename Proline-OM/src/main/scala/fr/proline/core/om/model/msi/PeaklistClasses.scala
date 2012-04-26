package fr.proline.core.om.model.msi

import fr.proline.core.utils.misc.InMemoryIdGen

object Spectrum extends InMemoryIdGen
case class Spectrum( id: Int )

object PeaklistSoftware extends InMemoryIdGen
case class PeaklistSoftware( var id: Int,
                             val name: String,
                             val version: String
                           )

object Peaklist extends InMemoryIdGen
case class Peaklist( var id: Int,
                     val fileType: String,
                     val path: String,
                     val rawFileName: String,
                     val msLevel: Int,
                     var peaklistSoftware: PeaklistSoftware = null
                     )