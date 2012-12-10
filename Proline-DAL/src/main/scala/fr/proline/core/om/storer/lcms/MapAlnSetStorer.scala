package fr.proline.core.om.storer.lcms

import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.storer.lcms.impl._
import fr.proline.repository.DriverType

trait IMapAlnSetStorer {
  
  import fr.proline.core.om.model.lcms.MapAlignmentSet
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit
  
}

/** A factory object for implementations of the IMapAlnSetStorer trait */
object MapAlnSetStorer {
  def apply( lcmsDb: SQLQueryHelper ): IMapAlnSetStorer = { lcmsDb.driverType match {
    //case DriverType.POSTGRESQL => new GenericMapAlnSetStorer(lcmsDb)
    //case DriverType.SQLITE => new SQLiteMapAlnSetStorer(lcmsDb)
    case _ => new SQLiteMapAlnSetStorer(lcmsDb.ezDBC)
    }
  }
}