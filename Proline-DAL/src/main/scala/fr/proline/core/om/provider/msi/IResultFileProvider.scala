package fr.proline.core.om.provider.msi

import java.io.File
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.model.msi.PtmDefinition
//import fr.proline.core.om.provider.msi.impl.ResultFileProviderContext
import fr.proline.core.om.model.msi.Enzyme

trait IResultFileVerifier {
   // returns PtmDefinitions referenced by the specified file
   def getPtmDefinitions(fileLocation: File, importProperties: Map[String, Any]): Seq[PtmDefinition]
   // can be used to verify that the provider handle this kind of file (ex: MSMS search, error tolerant search, n15 search, PMF, ...)  
   def isValid(fileLocation: File, importProperties: Map[String, Any]) : Boolean
   // returns Enzyme referenced by the specified file
   def getEnzyme(fileLocation: File, importProperties: Map[String, Any]): Array[Enzyme]
}

trait IResultFileProvider {
  
  val fileType: String
  def getResultFile( fileLocation: File, importProperties: Map[String, Any],
      parserContext: ProviderDecoratedExecutionContext): IResultFile
  def getResultFileVerifier : IResultFileVerifier
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
 