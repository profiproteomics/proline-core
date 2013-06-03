package fr.proline.core.om.model.msi

import java.io.File

trait IPeaklistContainer {  
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ): Unit
}

trait IResultFile extends IPeaklistContainer {
  
  val fileLocation: File  
  val importProperties: Map[String, Any]  
  
  val msLevel: Int
  val msiSearch: MSISearch
  val msQueryByInitialId: Map[Int,MsQuery]
  val hasDecoyResultSet: Boolean
  val hasMs2Peaklist: Boolean
  
  var instrumentConfig: Option[InstrumentConfig] = None
  var peaklistSoftware: Option[PeaklistSoftware] = None
  
  def getResultSet( wantDecoy: Boolean ): ResultSet
  
  def eachSpectrumMatch( wantDecoy: Boolean, onEachSpectrumMatch: SpectrumMatch => Unit ): Unit
  
  def close(): Unit // release resources
  
}
