package fr.proline.core.om.storer.ps

import fr.proline.context.IExecutionContext
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.storer.ps.impl.JPAPtmDefinitionStorer

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
    
    JPAPtmDefinitionStorer
    /*psDbContext.isJPA match {
      case true => new RsmStorer( new SQLRsmStorer() )
      case false => new RsmStorer( new SQLRsmStorer() )
    }*/
    
  }

}