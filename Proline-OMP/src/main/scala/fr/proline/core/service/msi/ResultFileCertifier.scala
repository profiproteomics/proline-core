package fr.proline.core.service.msi

import java.io.File
import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.context._
import fr.proline.core.om.provider.msi.IResultFileProvider
import fr.proline.core.om.provider.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.ps.BuildPtmDefinitionStorer
import fr.proline.core.om.model.msi.PtmDefinition

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
    val psDbCtx = executionContext.getPSDbConnectionContext()
    
    for ((fileType, files) <- resultIdentFilesByFormat) {
      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider.isDefined, "No ResultFileProvider for specified identification file format "+fileType)

      val storer = BuildPtmDefinitionStorer(executionContext.getPSDbConnectionContext)

      val rfVerifier = rfProvider.get.getResultFileVerifier
      val ptms = new ArrayBuffer[PtmDefinition]
      
      for (file <- files) {
        
        // Check if result file is valid
        // TODO: return something else than a Boolean (this is not very informative...)
        if( rfVerifier.isValid(file, importProperties) == false ) {
          throw new Exception("result file ("+file+") is invalid")
        }
        
        // Retrieve PTM definitions from the result file
        val ptmDefs = rfVerifier.getPtmDefinitions(file, importProperties)
        for (p <- ptmDefs) {
          if (!ptms.exists(_.sameAs(p))) ptms += p
        }
      }
      
      // Store PTMs if some were found
      if( ptms.length > 0 ) {
        
        val isTxOk = psDbCtx.tryInTransaction {
          this.logger.info("%d PTM(s) found in the result file, they are going to be stored".format(ptms.length) )
          storer.storePtmDefinitions(ptms, executionContext)
        }
        if( isTxOk == false ) result = false
      }
      
    }

    result
  }

}