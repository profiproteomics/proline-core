package fr.proline.core.service.msi
import java.io.File
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.model.msi.ResultFileFactory
import fr.proline.core.service.IService
import com.weiglewilczek.slf4s.Logger

class ResultFileImporter(projectId: Int,
                         identificationFile: File,
                         fileType: String,
                         providersKey: String,
                         storeResultSet: Boolean) extends IService {

  private val logger: Logger = Logger("fr.proline.core.service.msi.ResultFileImporter")
  private var readResultSetId: Int = 0 
  
  def resultSetId = readResultSetId	 
  
  def runService(): Boolean = {
    
    if (identificationFile == null)
      throw new IllegalArgumentException("ResultFileImporter service: No file specified.")

    logger.info(" Run service " + fileType + " ResultFileImporter on " + identificationFile.getAbsoluteFile())
    
    //Get Right ResultFile
    val rf: Option[IResultFile] = ResultFileFactory.getResultFile(fileType)
    if (rf == None)
      throw new IllegalArgumentException("No ResultFile for specified identification file format")

    val resultFile = rf.get

    //Specify Provider key for ProvidersFactory
    resultFile.providerKey = providersKey
    val result = resultFile.parseFile(identificationFile)

    if (!result) {
      logger.info("Parsing file " + identificationFile.getAbsoluteFile() + " using " + resultFile.getClass().getName() + " failed !")
      return result
    }

    val decoyRs = resultFile.getResultSet(true)
    val fowardRs = resultFile.getResultSet(false)
    val rsStorer = RsStorer.apply("")
    
    if(decoyRs == null)
    	fowardRs.decoyResultSet = Option.empty
	else 
		fowardRs.decoyResultSet = Some(decoyRs)
		
    readResultSetId = rsStorer.storeResultSet(fowardRs)    

    return true
  }
   
}