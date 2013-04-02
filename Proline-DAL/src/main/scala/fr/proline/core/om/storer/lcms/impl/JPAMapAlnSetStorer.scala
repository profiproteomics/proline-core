package fr.proline.core.om.storer.lcms.impl

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms.MapAlignmentSet
import fr.proline.core.om.storer.lcms.IMapAlnSetStorer

class JPAMapAlnSetStorer( lcmsDbCtx: DatabaseConnectionContext ) extends IMapAlnSetStorer {
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit = {
    throw new Exception("not yet implemented")
    

  }

  
}