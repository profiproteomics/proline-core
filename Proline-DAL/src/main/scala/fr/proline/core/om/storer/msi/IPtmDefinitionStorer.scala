package fr.proline.core.om.storer.msi

import fr.proline.context.IExecutionContext
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.storer.msi.impl.JPAPtmDefinitionStorer
import fr.proline.repository.ProlineDatabaseType

/**
 * @author David Bouyssie
 *
 */
trait IPtmDefinitionStorer {
  def storePtmDefinitions( ptmDefs: Seq[PtmDefinition], execCtx: IExecutionContext ): Int
}

/**
 * @author David Bouyssie
 *
 */
object BuildPtmDefinitionStorer {
  
  def apply( msiDbContext: MsiDbConnectionContext ): IPtmDefinitionStorer = {
    
    JPAPtmDefinitionStorer
  }

}