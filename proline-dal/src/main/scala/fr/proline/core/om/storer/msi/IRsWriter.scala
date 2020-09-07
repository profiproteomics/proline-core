package fr.proline.core.om.storer.msi

import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi.{IPeaklistContainer, IResultFile, Peptide, Protein, ResultSet}
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.IRsContainer
import fr.proline.core.orm.msi.PeptideReadablePtmString

trait IRsWriter extends LazyLogging {

  /**
   * Store specified new ResultSet and all associated data into dbs.
   * Protein and peptides referenced by the result set will be created as well
   * if necessary.
   */
  def fetchExistingPeptidesIdByUniqueKey(pepSequences: Seq[String], msiDbCtx: MsiDbConnectionContext): Map[String, Long]
  //def insertNewPeptides(peptides: Seq[Peptide], peptideByUniqueKey: HashMap[String,Peptide], msiDbCtx: DatabaseConnectionContext): Unit
  
  def fetchProteinIdentifiers(accessions: Seq[String]): Array[Any] // TODO: use JPA

  def fetchExistingProteins(protCRCs: Seq[String]): Array[Protein]
  def insertNewProteins(proteins: Seq[Protein], proteinBySequence: HashMap[String,Protein], msiDbCtx: MsiDbConnectionContext): Array[Protein]

  def insertRsReadablePtmStrings(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int
  //Use PtmString registered in PeptideReadablePtmString 
  def insertSpecifiedRsReadablePtmStrings(rs: ResultSet, readablePtmStringByPepId: Map[Long, PeptideReadablePtmString], msiDbCtx: MsiDbConnectionContext): Int
  def insertRsPeptideMatches(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int  
  def insertRsSpectrumMatches(rs: ResultSet, rf: IRsContainer, msiDbCtx: MsiDbConnectionContext): Int
  def insertRsProteinMatches(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int
  def insertRsSequenceMatches(rs: ResultSet, msiDbCtx: MsiDbConnectionContext): Int

}


