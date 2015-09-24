package fr.proline.core.om.storer.msi

import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi.{ IResultFile, IPeaklistContainer, ResultSet, Protein, Peptide }
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.IRsContainer

trait IRsWriter extends LazyLogging {

  /**
   * Store specified new ResultSet and all associated data into dbs.
   * Protein and peptides referenced by the result set will be created as well
   * if necessary.
   */
  def fetchExistingPeptidesIdByUniqueKey(pepSequences: Seq[String], msiDbCtx: DatabaseConnectionContext): Map[String, Long]
  def insertNewPeptides(peptides: Seq[Peptide], peptideByUniqueKey: HashMap[String,Peptide], msiDbCtx: DatabaseConnectionContext): Unit
  
  def fetchProteinIdentifiers(accessions: Seq[String]): Array[Any] // TODO: use JPA

  def fetchExistingProteins(protCRCs: Seq[String]): Array[Protein]
  def insertNewProteins(proteins: Seq[Protein], proteinBySequence: HashMap[String,Protein], msiDbCtx: DatabaseConnectionContext): Array[Protein]

  def insertRsReadablePtmStrings(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int
  def insertRsPeptideMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int  
  def insertRsSpectrumMatches(rs: ResultSet, rf: IRsContainer, msiDbCtx: DatabaseConnectionContext): Int
  def insertRsProteinMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int
  def insertRsSequenceMatches(rs: ResultSet, msiDbCtx: DatabaseConnectionContext): Int

}


