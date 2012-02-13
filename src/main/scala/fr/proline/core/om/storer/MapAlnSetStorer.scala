package fr.proline.core.om.storer

import fr.proline.core.LcmsDb
import fr.proline.core.om.storer.lcms._

/** A factory object for implementations of the IMapAlnSetStorer trait */
object MapAlnSetStorer {
  def apply( lcmsDb: LcmsDb ): IMapAlnSetStorer = { lcmsDb.config.driver match {
    case "org.postgresql.JDBC" => new GenericMapAlnSetStorer(lcmsDb)
    case "org.sqlite.JDBC" => new SQLiteMapAlnSetStorer(lcmsDb)
    case _ => new GenericMapAlnSetStorer(lcmsDb)
    }
  }
}