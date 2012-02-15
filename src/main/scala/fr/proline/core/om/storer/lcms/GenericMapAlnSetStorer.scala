package fr.proline.core.om.storer.lcms

import fr.proline.core.LcmsDb

class GenericMapAlnSetStorer( lcmsDb: LcmsDb ) extends IMapAlnSetStorer {
  
  import fr.proline.core.om.lcms.MapAlignmentSet
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit = {
    throw new Exception("not yet implemented")
    

  }

  
}