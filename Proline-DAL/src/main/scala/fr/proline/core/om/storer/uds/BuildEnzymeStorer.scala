package fr.proline.core.om.storer.uds

import fr.proline.context.IExecutionContext
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.Enzyme
import fr.proline.core.om.storer.uds.impl.JPAEnzymeStorer
import fr.proline.repository.ProlineDatabaseType

trait IEnzymeStorer {
  def storeEnzymes( enzymes: Seq[Enzyme], execCtx: IExecutionContext ): Int
}

object BuildEnzymeStorer {
  
  def apply( udsDbContext: DatabaseConnectionContext ): IEnzymeStorer = {
    require(
      udsDbContext.getProlineDatabaseType == ProlineDatabaseType.UDS,
      "Invalid DatabaseConnectionContext: a UDSDb one must be provided"
    )
    
    JPAEnzymeStorer
  }

}