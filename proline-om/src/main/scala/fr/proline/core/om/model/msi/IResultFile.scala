package fr.proline.core.om.model.msi

import java.io.File

trait IPeaklistContainer {
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ): Unit
}

trait IRsContainer extends IPeaklistContainer {

  def getResultSet( wantDecoy: Boolean ): ResultSet
  
  def eachSpectrumMatch( wantDecoy: Boolean, onEachSpectrumMatch: SpectrumMatch => Unit ): Unit

}

trait IResultFile extends IRsContainer {
  
  def fileLocation: File
  def importProperties: Map[String, Any]
  
  def msLevel: Int
  def msiSearch: MSISearch
  def msQueries: Array[MsQuery]
  def hasDecoyResultSet: Boolean
  def hasMs2Peaklist: Boolean
  
  var instrumentConfig: Option[InstrumentConfig] = None
  var fragmentationRuleSet: Option[FragmentationRuleSet] = None
  var peaklistSoftware: Option[PeaklistSoftware] = None
    
  def close(): Unit // release resources
  
  /**
   * Load ResultSet from associated file. 
   * This method could be called before the getResultSet() but it's not mandatory...
   */  
  def parseResultSet(wantDecoy: Boolean )
  
  // Requirements
  // TODO: make this requirement works ???
  //require(fileLocation != null, "fileLocation is null")
  
}
