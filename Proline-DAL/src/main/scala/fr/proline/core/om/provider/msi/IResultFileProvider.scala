package fr.proline.core.om.provider.msi

import java.io.File
import fr.proline.core.om.model.msi.IResultFile
//import fr.proline.core.om.provider.msi.impl.ResultFileProviderContext

trait IResultFileProvider {
  
  val fileType: String
  def getResultFile( fileLocation: File, importProperties: Map[String, Any], providerKey: String ): IResultFile //providerCtx: ResultFileProviderContext
  val resultFileProperties : Map[String, Class[_]]

}

object ResultFileProviderRegistry {
  
  import scala.collection.mutable.HashMap
  import java.util.ServiceLoader
  
  // Initialize ResultFileProviderMap by getting SPI 
  private val resultFileProviderByFormat: HashMap[String, IResultFileProvider]= {
     val resFileServiceLoader  : ServiceLoader[IResultFileProvider] = ServiceLoader.load(classOf[IResultFileProvider])    
     var loadedRFProviders = new HashMap[String, IResultFileProvider]()
      // Discover and register the available commands
     resFileServiceLoader.reload();
     val resultFileIterator: java.util.Iterator[IResultFileProvider] = resFileServiceLoader.iterator()
     while (resultFileIterator.hasNext())   {
        val nextResFile : IResultFileProvider = resultFileIterator.next()
        loadedRFProviders.put(nextResFile.fileType, nextResFile)
    }
     loadedRFProviders
  }
  
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
  
  /**
   * Return an iterator over all available IResultFileProvider
   */
  def getAllResultFileProviders() : Iterator[IResultFileProvider] = {
    resultFileProviderByFormat.valuesIterator
  }
   
}
 