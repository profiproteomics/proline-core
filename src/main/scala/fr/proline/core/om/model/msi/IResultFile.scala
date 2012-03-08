package fr.proline.core.om.model.msi

  
class Spectrum() // TO define somewhere else

trait IResultFile {
  
  var msLevel: Int
  var msQueryById: Map[Int,MsQuery]
  var hasDecoyResultSet: Boolean
  var hasMs2Peaklist: Boolean
  
  def getResultSet( wantDecoy: Boolean ): Object
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ): Unit

}

class ResultFileImplFake extends IResultFile {
  
  var msLevel: Int = 2
  var msQueryById: Map[Int,MsQuery] = null
  var hasDecoyResultSet: Boolean = true
  var hasMs2Peaklist: Boolean = true
  
  def getResultSet( wantDecoy: Boolean ): Object = { new Object }
  
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ): Unit = {
    val spec = new Spectrum
    onEachSpectrum(spec)
  }
}