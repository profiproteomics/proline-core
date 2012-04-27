package fr.proline.core.om.storer.lcms

import fr.proline.core.dal.LcmsDb
import fr.proline.core.om.storer.lcms.impl._

trait IMapAlnSetStorer {
  
  import fr.proline.core.om.model.lcms.MapAlignmentSet
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit
  
}

/** A factory object for implementations of the IMapAlnSetStorer trait */
object MapAlnSetStorer {
  def apply( lcmsDb: LcmsDb ): IMapAlnSetStorer = { lcmsDb.config.driver match {
    case "org.postgresql.Driver" => new GenericMapAlnSetStorer(lcmsDb)
    case "org.sqlite.JDBC" => new SQLiteMapAlnSetStorer(lcmsDb)
    case _ => new GenericMapAlnSetStorer(lcmsDb)
    }
  }
}