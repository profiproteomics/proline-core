package fr.proline.core.service.msi
import java.io.File
import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
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

    for ((fileType, files) <- resultIdentFilesByFormat) {
      // Get Right ResultFile provider
      val rfProvider: Option[IResultFileProvider] = ResultFileProviderRegistry.get(fileType)
      require(rfProvider != None, "No ResultFileProvider for specified identification file format")

      val storer = BuildPtmDefinitionStorer(executionContext.getPSDbConnectionContext)

      val verifier = rfProvider.get.getResultFileVerifier
      var ptms = Set.empty[PtmDefinition]

      for (file <- files) {
        verifier.isValid(file, importProperties)
        val ptmDefs = verifier.getPtmDefinitions(file, importProperties)
        for (p <- ptmDefs) {
          if (!ptms.exists(_.sameAs(p))) ptms += p
        }
      }
      storer.storePtmDefinitions(ptms.toSeq, executionContext)
    }

    true
  }

}