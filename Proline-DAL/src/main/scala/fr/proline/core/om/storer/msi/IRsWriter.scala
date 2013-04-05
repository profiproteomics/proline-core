package fr.proline.core.om.storer.msi

import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.{ IResultFile, IPeaklistContainer, ResultSet, Protein, Peptide }
import fr.proline.core.om.storer.msi.impl.SQLRsStorer
import fr.proline.context.DatabaseConnectionContext

trait IRsWriter extends Logging {

  // TODO: implement as InMemoryProvider
  val peptideByUniqueKey = new HashMap[String, Peptide]()
  val proteinBySequence = new HashMap[String, Protein]()

  /**
   * Store specified new ResultSet and all associated data into dbs.
   * Protein and peptides referenced by the result set will be created as well
   * if necessary.
   */
  def fetchExistingPeptidesIdByUniqueKey(pepSequences: Seq[String], msiDbCtx: DatabaseConnectionContext): Map[String, Int]
  def storeNewPeptides(peptides: Seq[Peptide], msiDbCtx: DatabaseConnectionContext): Unit

  def fetchProteinIdentifiers(accessions: Seq[String]): Array[Any] // TODO: use JPA

  def fetchExistingProteins(protCRCs: Seq[String]): Array[Protein]
  def storeNewProteins(proteins: Seq[Protein], msiDbCtx: DatabaseConnectionContext): Array[Protein]

  def storeRsPeptideMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int
  def storeRsSpectrumMatches(rs: ResultSet, rf: IResultFile, msiDbCtx: DatabaseConnectionContext): Int
  def storeRsProteinMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int
  def storeRsSequenceMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int

}


