package fr.proline.core.om.model.msi

import java.io.File

trait IPeaklistContainer {  
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ): Unit
}

trait IResultFile extends IPeaklistContainer {
  
  val fileLocation: File
  val providerKey : String
  
  val msLevel: Int
  val msiSearch: MSISearch
  val msQueryByInitialId: Map[Int,MsQuery]
  val hasDecoyResultSet: Boolean
  val hasMs2Peaklist: Boolean
  
  var instrumentConfig: InstrumentConfig = null
  
  def getResultSet( wantDecoy: Boolean ): ResultSet
  
}

trait IResultFileProvider {
  
  val fileType: String
  def getResultFile( fileLocation: File, providerKey: String ): IResultFile

}

object ResultFileProviderRegistry {

  import scala.collection.mutable.HashMap
  
  private val resultFileProviderByFormat: HashMap[String, IResultFileProvider]= new HashMap[String , IResultFileProvider]()
  
  /**
  * Register specified ResultFile for specified format. 
  * return an Option containing previous ResultFile associated to format if exist None otherwise
  * 
  */
  def register( resultFileProvider:IResultFileProvider ): Option[IResultFileProvider] = {     
    resultFileProviderByFormat.put( resultFileProvider.fileType, resultFileProvider )
  }
  
   /**
  * Return an Option containing the ResultFile for specified format if exist, None otherwise
  */
  def get( fileType: String ): Option[IResultFileProvider]= {    
    resultFileProviderByFormat.get( fileType )
  }
   
}
 