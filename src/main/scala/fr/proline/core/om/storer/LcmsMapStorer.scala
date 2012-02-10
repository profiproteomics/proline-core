package fr.proline.core.om.storer

import fr.proline.core.LcmsDb
import fr.proline.core.om.storer.lcms._

/*
/** A factory object for implementations of the IRunMapStorer trait */
object RunMapStorer {
  def apply(driver: String ): IRunMapStorer = { driver match {
    case "pg" => new GenericRunMapStorer()
    case "sqlite" => new GenericRunMapStorer()
    case _ => new GenericRunMapStorer()
    }
  }
}
*/

/** A factory object for implementations of the IProcessedMapStorer trait */
object ProcessedMapStorer {
  def apply( lcmsDb: LcmsDb ): IProcessedMapStorer = { lcmsDb.config.driver match {
    case "org.postgresql.JDBC" => new GenericProcessedMapStorer(lcmsDb)
    case "org.sqlite.JDBC" => new SQLiteProcessedMapStorer(lcmsDb)
    case _ => new GenericProcessedMapStorer(lcmsDb)
    }
  }
}