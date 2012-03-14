package fr.proline.core.om.model.msi

import fr.proline.core.utils.misc.InMemoryIdGen

object Spectrum extends InMemoryIdGen
case class Spectrum( id: Int )

object Peaklist extends InMemoryIdGen
case class Peaklist( val id: Int,
                     val fileType: String,
                     val path: String,
                     val msLevel: Int )