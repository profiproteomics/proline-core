package fr.proline.core.om.storer.msi

import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.storer.msi.impl.GenericRsStorer
import fr.proline.core.om.storer.msi.impl.PgRsStorer
import fr.proline.core.om.storer.msi.impl.SQLiteRsStorer

trait IRsStorer {
  
  /**
   * Store specified new ResultSet and all associated data into dbs. 
   * Protein and peptides referenced by the resultset will be created as well
   * if necessary. 
   */
  def storeResultSet(rs: ResultSet ): Int

  def fetchExistingPeptides( peptideIds: Seq[Int] ): Array[Any]
  def storeNewPeptides( peptides: Seq[Any] ): Unit
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any]
  
  def fetchExistingProteins( proteinCRCs: Seq[String] ): Array[Any]
  def storeNewProteins( proteins: Seq[Any] ): Unit
  
  def storeRsPeptideMatches( rs: ResultSet ): Int
  def storeRsProteinMatches( rs: ResultSet ): Int
  def storeRsSequenceMatches( rs: ResultSet ): Int
  
}

/** A factory object for implementations of the IRsStorer trait */
object RsStorer {
  def apply(driver: String ): IRsStorer = { driver match {
    case "org.postgresql.JDBC" => new PgRsStorer()
    case "org.sqlite.JDBC" => new SQLiteRsStorer()
    case _ => new GenericRsStorer()
    }
  }
}