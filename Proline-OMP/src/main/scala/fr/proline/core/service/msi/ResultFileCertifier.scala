package fr.proline.core.service.msi

import java.io.File

import scala.collection.mutable.ArrayBuffer

import com.typesafe.scalalogging.LazyLogging

import fr.profi.chemistry.model.Enzyme
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.context.dbCtxToTxDbCtx
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultFileProvider
import fr.proline.core.om.provider.msi.ResultFileProviderRegistry
import fr.proline.core.om.storer.msi.BuildPtmDefinitionStorer
import fr.proline.core.om.storer.uds.BuildEnzymeStorer


class ResultFileCertifier(
  executionContext: IExecutionContext,
  resultIdentFilesByFormat: Map[String, Array[File]],
  importProperties: Map[String, Any]
) extends IService with LazyLogging {
  
  override protected def beforeInterruption = {
    // Release database connections
    //this.logger.info("releasing database connections before service interruption...")
  }

  def runService(): Boolean = {
    
    var result = true
    val msiDbCtx = executionContext.getMSIDbConnectionContext()
    val udsDbCtx = executionContext.getUDSDbConnectionContext()
    
    for ((fileType, files) <- resultIdentFilesByFormat) {
      	executeOnProgress() //execute registered action during progress
      	

      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider.isDefined, "No ResultFileProvider for specified identification file format "+fileType)

      rfProvider.get.setParserContext(ProviderDecoratedExecutionContext(executionContext) )

      val ptmDefStorer = BuildPtmDefinitionStorer(executionContext.getMSIDbConnectionContext)
      val enzymeStorer = BuildEnzymeStorer(executionContext.getUDSDbConnectionContext)

      val rfVerifier = rfProvider.get.getResultFileVerifier
      val ptms = new ArrayBuffer[PtmDefinition]
      val enzymes = new ArrayBuffer[Enzyme]
      
      for (file <- files) {
        
        // Check if result file is valid
        // TODO: return something else than a Boolean (this is not very informative...)
        if( rfVerifier.isValid(file, importProperties) == false ) {
          throw new Exception(s"Invalid result file: '$file'")
        }
        
        // Retrieve PTM definitions from the result file
        val ptmDefs = rfVerifier.getPtmDefinitions(file, importProperties)
        for (p <- ptmDefs) {
          if (!ptms.exists(_.sameAs(p))) ptms += p
        }
        
        // Retrieve enzyme from the result file
        val enzymeDefs = rfVerifier.getEnzyme(file, importProperties)
        for (e <- enzymeDefs) {
          if (!enzymes.exists(_.eq(e))) enzymes += e
        }
      }
      
    	executeOnProgress() //execute registered action during progress
      // Store PTMs if some were found
      if (ptms.nonEmpty) {
        
        val isTxOk = msiDbCtx.tryInTransaction {
          this.logger.info(s"${ptms.length} PTM(s) found in the result file, they are going to be stored" )
          ptmDefStorer.storePtmDefinitions(ptms, executionContext)
        }
        if( !isTxOk ) result = false
      }

    	executeOnProgress() //execute registered action during progress
      // Store enzyme if some were found
      if (enzymes.nonEmpty) {
        
        val isTxOk = udsDbCtx.tryInTransaction {
          this.logger.info(s"${enzymes.length} enzyme(s) found in the result file, they are going to be stored" )
          enzymeStorer.storeEnzymes(enzymes, executionContext)
        }
      }
      
    }

    result
  }

}