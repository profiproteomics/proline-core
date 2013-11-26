package fr.proline.core.service.msi
import java.io.File
import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msi.IResultFileProvider
import fr.proline.core.om.provider.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.ps.BuildPtmDefinitionStorer
import fr.proline.core.om.model.msi.PtmDefinition
import scala.collection.mutable.ArrayBuffer

class ResultFileCertifier(
  executionContext: IExecutionContext,
  resultIdentFilesByFormat: Map[String, Array[File]],
  importProperties: Map[String, Any]
  ) extends IService with Logging {

  override protected def beforeInterruption = {
    // Release database connections
    //this.logger.info("releasing database connections before service interruption...")
  }

  def runService(): Boolean = {

    var result = true
    for ((fileType, files) <- resultIdentFilesByFormat) {
      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider != None, "No ResultFileProvider for specified identification file format "+fileType)

      val storer = BuildPtmDefinitionStorer(executionContext.getPSDbConnectionContext)

      val verifier = rfProvider.get.getResultFileVerifier
      val ptms = new ArrayBuffer[PtmDefinition]

      for (file <- files) {
        verifier.isValid(file, importProperties)  // TODO : Use returned value !
        val ptmDefs = verifier.getPtmDefinitions(file, importProperties)
        for (p <- ptmDefs) {
          if (!ptms.exists(_.sameAs(p))) ptms += p
        }
      }
      
      val psCtxt = executionContext.getPSDbConnectionContext()      
      var transactionOK = false
      
      try {
    	  psCtxt.beginTransaction()
    	  storer.storePtmDefinitions(ptms, executionContext)
    	  psCtxt.commitTransaction()
    	  transactionOK = true
      } finally {
    	  if (!transactionOK) {
    	      result = false
    		  logger.info("Rollbacking PS Db Transaction")
    		  psCtxt.rollbackTransaction()
    	  }
      }
    }

    result
  }

}