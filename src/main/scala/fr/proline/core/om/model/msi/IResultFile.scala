package fr.proline.core.om.model.msi
import java.io.File
  
class Spectrum() // TO define somewhere else

trait IResultFile {
  
  var msLevel: Int
  var msQueryById: Map[Int,MsQuery]
  var hasDecoyResultSet: Boolean
  var hasMs2Peaklist: Boolean
  var providerKey : String
  
  def getResultSet( wantDecoy: Boolean ): ResultSet
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ): Unit
  def parseFile(identFile : File) : Boolean

}
 
 object ResultFileFactory {   

   import scala.collection.mutable.HashMap
 
   private val resultFilePerFormat:HashMap[String, IResultFile]= new HashMap[String , IResultFile]()
   
   /**
    * Register specified ResultFile for specified format. 
    * return an Option containing previous ResultFile associated to format if exist None otherwise
    * 
    */
   def registerResultFile(format: String, resultFile:IResultFile) : Option[IResultFile] = {     
		  resultFilePerFormat.put(format, resultFile)
   }
   
   /**
    * Return an Option containing the ResultFile for specified format if exist, None otherwise
    */
   def getResultFile(identificationFormat: String) :Option[IResultFile]= {    
       resultFilePerFormat.get(identificationFormat)
   }
}
 