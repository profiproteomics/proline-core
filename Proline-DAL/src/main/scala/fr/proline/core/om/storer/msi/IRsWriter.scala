package fr.proline.core.om.storer.msi

import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.{ ResultSet, Protein, Peptide, IPeaklistContainer }
import fr.proline.core.om.storer.msi.impl.SQLRsStorer
import fr.proline.repository.DatabaseContext

trait IRsWriter extends Logging {

  // TODO: implement as InMemoryProvider
  val peptideByUniqueKey = new HashMap[String, Peptide]()
  val proteinBySequence = new HashMap[String, Protein]()

  /**
   * Store specified new ResultSet and all associated data into dbs.
   * Protein and peptides referenced by the result set will be created as well
   * if necessary.
   */
  def fetchExistingPeptidesIdByUniqueKey(pepSequences: Seq[String], msiDb: DatabaseContext): Map[String, Int]
  def storeNewPeptides(peptides: Seq[Peptide], msiDb: DatabaseContext): Array[Peptide]

  def fetchProteinIdentifiers(accessions: Seq[String]): Array[Any] // TODO: use JPA

  def fetchExistingProteins(protCRCs: Seq[String]): Array[Protein]
  def storeNewProteins(proteins: Seq[Protein], msiDb: DatabaseContext): Array[Protein]

  def storeRsPeptideMatches(rs: ResultSet, msiDb: DatabaseContext): Int
  def storeRsProteinMatches(rs: ResultSet, msiDb: DatabaseContext): Int
  def storeRsSequenceMatches(rs: ResultSet, msiDb: DatabaseContext): Int

}


