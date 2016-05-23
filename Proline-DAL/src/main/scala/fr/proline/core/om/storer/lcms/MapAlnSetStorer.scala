package fr.proline.core.om.storer.lcms

import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.om.storer.lcms.impl._
import fr.proline.repository.DriverType

trait IMapAlnSetStorer {
  
  import fr.proline.core.om.model.lcms.MapAlignmentSet
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Long, alnRefMapId: Long): Unit
  
}

/** A factory object for implementations of the IMapAlnSetStorer trait */
object MapAlnSetStorer {
  def apply( lcmsDbCtx: LcMsDbConnectionContext ): IMapAlnSetStorer = {
    lcmsDbCtx.getDriverType match {
      //case DriverType.POSTGRESQL => new GenericMapAlnSetStorer(lcmsDb)
      //case DriverType.SQLITE => new SQLiteMapAlnSetStorer(lcmsDb)
      case _ => new SQLMapAlnSetStorer(lcmsDbCtx)
    }
  }
}