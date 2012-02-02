package fr.proline.core.om.storer

import fr.proline.core.om.storer.rs._

/** A factory object for implementations of the IRsStorer trait */
object RsStorer {
  def apply(driver: String ): IRsStorer = { driver match {
    case "org.postgresql.JDBC" => new PgRsStorer()
    case "org.sqlite.JDBC" => new SQLiteRsStorer()
    case _ => new GenericRsStorer()
    }
  }
}