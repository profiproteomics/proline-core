package fr.proline.core.om.storer.ps

import fr.proline.context.IExecutionContext
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.storer.ps.impl.JPAPtmDefinitionStorer
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
  
  def apply( psDbContext: DatabaseConnectionContext ): IPtmDefinitionStorer = {
    require(
      psDbContext.getProlineDatabaseType == ProlineDatabaseType.PS,
      "Invalid DatabaseConnectionContext: a PSDb one must be provided"
    )
    
    JPAPtmDefinitionStorer
  }

}