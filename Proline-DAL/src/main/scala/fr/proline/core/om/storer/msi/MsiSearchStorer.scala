package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.MSISearch
import fr.proline.core.om.model.msi.MsQuery

trait IMsiSearchStorer {
  
  def storeMsiSearch( msiSearch: MSISearch,
                      msQueries: Seq[MsQuery],
                      spectrumIdByTitle: Map[String,Int] ): Map[Int,Int]

  def insertInstrumentConfig( instrumentConfig: InstrumentConfig ): Unit
  
  def storeMsQueries( msiSearch: MSISearch,
                      msQueries: Seq[MsQuery],
                      spectrumIdByTitle: Map[String,Int] ): Unit
}

/** A factory object for implementations of the IMsiSearchStorer trait */
object MsiSearchStorer {
  
  import fr.proline.core.om.storer.msi.impl.PgMsiSearchStorer
  import fr.proline.core.om.storer.msi.impl.SQLiteMsiSearchStorer

  def apply( msiDb: MsiDb ): IMsiSearchStorer = { msiDb.config.driver match {
    case "org.postgresql.Driver" => new PgMsiSearchStorer( msiDb )
    case "org.sqlite.JDBC" => new SQLiteMsiSearchStorer( msiDb )
    //case _ => 
    }
  }
  
}


