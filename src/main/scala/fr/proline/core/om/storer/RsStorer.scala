package fr.proline.core.om.storer

import fr.proline.core.om.storer.rs._

/** A factory object for implementations of the IRsStorer trait */
object RsStorer {
  def apply(driver: String ): IRsStorer = { driver match {
    case "pg" => new PgRsStorer()
    case "sqlite" => new SqliteRsStorer()
    case _ => new GenericRsStorer()
    }
  }
}