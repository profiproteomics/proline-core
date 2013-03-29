package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.storer.lcms.IMapAlnSetStorer

class GenericMapAlnSetStorer( lcmsDb: SQLQueryHelper ) extends IMapAlnSetStorer {
  
  import fr.proline.core.om.model.lcms.MapAlignmentSet
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit = {
    throw new Exception("not yet implemented")
    

  }

  
}