package fr.proline.core.om.storer.lcms

trait IMapAlnSetStorer {
  
  import fr.proline.core.om.lcms.MapAlignmentSet
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit
  
}